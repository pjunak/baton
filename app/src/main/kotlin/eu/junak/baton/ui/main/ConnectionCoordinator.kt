package eu.junak.baton.ui.main

import android.os.Build
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import eu.junak.baton.feature.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

internal fun shouldKeepSyncConnected(uiStarted: Boolean, speakerEnabled: Boolean): Boolean =
    uiStarted || speakerEnabled

/**
 * Keeps realtime sync alive only while somebody needs it: the visible controller UI, or the
 * foreground speaker service. The latter deliberately includes every Android audio route — phone
 * speaker, wired output, Bluetooth headphones, and Bluetooth speakers all use the same Media3
 * player and therefore the same [PlaybackController.enabled] gate.
 */
@Singleton
class ConnectionCoordinator @Inject constructor(
    private val syncClient: SyncClient,
    playbackController: PlaybackController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uiStarted = MutableStateFlow(false)

    init {
        scope.launch {
            combine(uiStarted, playbackController.enabled, ::shouldKeepSyncConnected)
                .distinctUntilChanged()
                .collectLatest { needed ->
                    if (needed) {
                        if (syncClient.status.value == ConnectionStatus.DISCONNECTED) {
                            syncClient.connect(deviceName())
                        }
                    } else {
                        // Avoid reconnect churn across a rotation or a momentary system overlay.
                        delay(BACKGROUND_GRACE_MS)
                        syncClient.disconnect()
                    }
                }
        }
    }

    fun setUiStarted(started: Boolean) {
        uiStarted.value = started
    }

    private fun deviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Baton"

    private companion object {
        const val BACKGROUND_GRACE_MS = 5_000L
    }
}
