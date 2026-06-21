package eu.junak.baton.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A server → client message over the WebSocket, mirrored from the backend's
 * outbound schemas (`protocol.py`). Decode with the [ServerMessage] base type so
 * the `type` discriminator selects the concrete class.
 *
 * `SfxFired` is transient (not part of [PlayerState]) — broadcast to every
 * socket; the speaker role plays it only when this device is an active output.
 */
@Serializable
sealed interface ServerMessage {

    /** Sent immediately on connect, before the client's `register`, so
     *  `yourDeviceId` is empty by design — the client owns its stable id. */
    @Serializable
    @SerialName("state_snapshot")
    data class StateSnapshot(
        val yourDeviceId: String = "",
        val state: PlayerState,
    ) : ServerMessage

    @Serializable
    @SerialName("state_changed")
    data class StateChanged(val state: PlayerState) : ServerMessage

    @Serializable
    @SerialName("sfx_fired")
    data class SfxFired(
        val soundboardId: String,
        val itemPath: String,
        val volume: Double = 1.0,
    ) : ServerMessage

    @Serializable
    @SerialName("error")
    data class Error(val detail: String) : ServerMessage
}
