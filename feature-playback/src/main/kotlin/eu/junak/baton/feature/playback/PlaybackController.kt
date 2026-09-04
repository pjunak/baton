package eu.junak.baton.feature.playback

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /** Whether this phone is currently acting as an audio output. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** This device's protocol id (`device_id == client_id`). */
    val deviceId: String get() = networkStore.clientId

    init {
        scope.launch {
            syncClient.liveState
                .map { isCanonicalOutput(it, deviceId) }
                .distinctUntilChanged()
                .collect { active ->
                    val start = active && !_enabled.value
                    _enabled.value = active
                    if (start) {
                        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
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
        if (!on) _enabled.value = false
        val current = syncClient.liveState.value?.activeOutputDeviceIds ?: return
        val next = outputDeviceIds ?: if (on) (current + deviceId).distinct() else current - deviceId
        if (next != current) syncClient.send(Action.SetActiveOutputs(next))
    }

    fun toggle() = setEnabled(!_enabled.value)
}

internal fun isCanonicalOutput(state: PlayerState?, deviceId: String): Boolean =
    state?.activeOutputDeviceIds?.contains(deviceId) == true
