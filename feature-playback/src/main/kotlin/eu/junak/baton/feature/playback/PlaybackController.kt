package eu.junak.baton.feature.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.PlayerState
import eu.junak.baton.core.network.data.NetworkStore
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Projects canonical output membership into the phone's foreground service. */
@Singleton
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncClient: SyncClient,
    private val networkStore: NetworkStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _enabled = MutableStateFlow(false)

    /**
     * Whether the speaker role is on. This is LOCAL role state, not live
     * membership: it survives transport loss (see [SpeakerRoleReducer]), which
     * keeps the foreground service and the reconnect machinery alive through a
     * dropped socket. The service holds paused while [SyncClient.liveState] has
     * no snapshot confirming membership, so audio still flows only from live
     * canonical state.
     */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** This device's protocol id (`device_id == client_id`). */
    val deviceId: String get() = networkStore.clientId

    private val role = SpeakerRoleReducer()

    init {
        scope.launch {
            combine(syncClient.liveState, syncClient.connectionEpoch, ::Pair).collect { (state, epoch) ->
                val decision = if (state == null) {
                    role.onDisconnected(_enabled.value)
                } else {
                    role.onState(state, deviceId, _enabled.value, SystemClock.elapsedRealtime(), epoch)
                }
                _enabled.value = decision.enabled
                if (decision.rejoin && state != null) {
                    syncClient.send(Action.SetActiveOutputs((state.activeOutputDeviceIds + deviceId).distinct()))
                }
                if (decision.startService) {
                    // Normally reached with the app visible (the role is granted in
                    // response to an in-app toggle). A Console-driven grant can land
                    // while backgrounded, where Android may refuse the foreground
                    // start — release the role instead of claiming a mute speaker.
                    runCatching {
                        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
                    }.onFailure {
                        Log.e(TAG, "Could not start the speaker service", it)
                        setEnabled(false)
                    }
                }
            }
        }
    }

    /**
     * Request output membership. Audio starts only after server confirmation.
     * [outputDeviceIds] replaces the output set for an explicit operator choice.
     * Reconnect never replays this mutation against an old snapshot.
     */
    fun setEnabled(on: Boolean, outputDeviceIds: List<String>? = null) {
        if (on) {
            role.onJoinRequested()
        } else {
            _enabled.value = false
            role.onLeaveRequested(SystemClock.elapsedRealtime())
        }
        val current = syncClient.liveState.value?.activeOutputDeviceIds ?: return
        val next = outputDeviceIds ?: if (on) (current + deviceId).distinct() else current - deviceId
        if (next != current) syncClient.send(Action.SetActiveOutputs(next))
    }

    private companion object {
        const val TAG = "PlaybackController"
    }
}

internal fun isCanonicalOutput(state: PlayerState?, deviceId: String): Boolean =
    state?.activeOutputDeviceIds?.contains(deviceId) == true

/** What one [SyncClient.liveState] emission does to the speaker role. */
internal data class RoleDecision(
    val enabled: Boolean,
    /** Start the foreground service (the role just turned on). */
    val startService: Boolean = false,
    /** Re-request output membership that a disconnect pruned server-side. */
    val rejoin: Boolean = false,
)

/**
 * Pure decision core for the phone-speaker role. The server contract
 * (`music/clients/README.md`) prunes a disconnected socket from
 * `active_output_device_ids`, so absence from the FIRST snapshot of a new
 * connection — detected by the connection epoch, which survives conflation of
 * the disconnected null — is transport fallout, not an operator choice: the
 * role rejoins once and stays on while the confirmation is in flight. Absence
 * observed mid-connection (outside that confirmation window) IS a deliberate
 * removal and relinquishes the role, as does a first snapshot in which a
 * one-output mode already plays through another speaker. Symmetrically, a local stop opens a leave
 * window so a racing broadcast that still lists this device cannot undo it.
 * Transport loss itself never changes the role; that is what keeps the
 * Android 17 while-in-use foreground service alive across a screen-off Wi-Fi
 * blip instead of restarting it muted from the background.
 */
internal class SpeakerRoleReducer {
    private var handledEpoch = Int.MIN_VALUE

    /** Sentinels sit outside their windows for every non-negative clock,
     *  without `now - sentAt` overflowing. */
    private var rejoinSentAt = -REJOIN_CONFIRM_MS - 1
    private var leaveSentAt = -LEAVE_CONFIRM_MS - 1

    /** A local re-enable cancels any pending leave window. */
    fun onJoinRequested() {
        leaveSentAt = -LEAVE_CONFIRM_MS - 1
    }

    /** A local stop: ignore membership from broadcasts that predate it. */
    fun onLeaveRequested(now: Long) {
        leaveSentAt = now
    }

    fun onDisconnected(enabled: Boolean): RoleDecision = RoleDecision(enabled)

    fun onState(
        state: PlayerState,
        deviceId: String,
        enabled: Boolean,
        now: Long,
        connectionEpoch: Int,
    ): RoleDecision {
        val firstSnapshot = connectionEpoch != handledEpoch
        handledEpoch = connectionEpoch
        return when {
            isCanonicalOutput(state, deviceId) -> {
                if (now - leaveSentAt <= LEAVE_CONFIRM_MS) {
                    // A broadcast that predates our stop can still list us;
                    // honor the local stop while its removal is in flight.
                    RoleDecision(enabled = false)
                } else {
                    // Confirmation closes the rejoin window, so a LATER
                    // mid-connection removal relinquishes immediately.
                    rejoinSentAt = -REJOIN_CONFIRM_MS - 1
                    RoleDecision(enabled = true, startService = !enabled)
                }
            }
            !enabled -> RoleDecision(enabled = false)
            // A one-output mode handed the room to another speaker while we
            // were away; rejoining would take it back, so yield instead.
            firstSnapshot && !state.multipleOutputs && state.activeOutputDeviceIds.isNotEmpty() ->
                RoleDecision(enabled = false)
            firstSnapshot -> {
                rejoinSentAt = now
                RoleDecision(enabled = true, rejoin = true)
            }
            // An unrelated broadcast can race our rejoin confirmation; don't
            // give the role up while ours may still be in flight.
            now - rejoinSentAt <= REJOIN_CONFIRM_MS -> RoleDecision(enabled = true)
            else -> RoleDecision(enabled = false)
        }
    }

    private companion object {
        const val REJOIN_CONFIRM_MS = 10_000L
        const val LEAVE_CONFIRM_MS = 10_000L
    }
}
