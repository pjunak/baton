package eu.junak.baton.core.sync

/** Live state of the WebSocket connection to the server. */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}
