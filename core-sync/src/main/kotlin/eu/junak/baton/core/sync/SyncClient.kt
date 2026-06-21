package eu.junak.baton.core.sync

import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.PlayerState
import eu.junak.baton.core.model.ServerMessage
import eu.junak.baton.core.network.ServerConfig
import eu.junak.baton.core.network.data.NetworkStore
import eu.junak.baton.core.network.url.ServerUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Owns the WebSocket to the server and exposes the canonical [PlayerState] as a
 * [StateFlow] the rest of the app reconciles to. Mirrors the protocol in the
 * music backend's `clients/README.md`:
 *
 * 1. connect → server pushes a `state_snapshot`
 * 2. we `register` with our stable client_id (so the operator can designate this
 *    device as an output)
 * 3. every `state_changed` replaces local state; `sfx_fired` / `error` are
 *    surfaced as one-shot events
 *
 * Reconnects with exponential backoff. The shared OkHttp client carries the
 * session cookie, so the upgrade is authenticated automatically.
 */
@Singleton
class SyncClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val serverConfig: ServerConfig,
    private val networkStore: NetworkStore,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<PlayerState?>(null)

    /** Latest canonical state, or null before the first snapshot. */
    val state: StateFlow<PlayerState?> = _state.asStateFlow()

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _sfxEvents = MutableSharedFlow<ServerMessage.SfxFired>(extraBufferCapacity = EVENT_BUFFER)

    /** Transient fire-and-forget SFX events (the speaker role plays them). */
    val sfxEvents: SharedFlow<ServerMessage.SfxFired> = _sfxEvents.asSharedFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)

    /** Server-reported error frames (e.g. a rejected action), for toasts. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    @Volatile private var webSocket: WebSocket? = null

    @Volatile private var manuallyClosed = false

    @Volatile private var deviceName: String = "Baton"

    @Volatile private var reconnectAttempts = 0

    private var reconnectJob: Job? = null

    /** Open the socket and keep it alive (reconnecting) until [disconnect]. */
    @Synchronized
    fun connect(deviceName: String) {
        this.deviceName = deviceName
        manuallyClosed = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        openSocket()
    }

    @Synchronized
    fun disconnect() {
        manuallyClosed = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(NORMAL_CLOSURE, null)
        webSocket = null
        _status.value = ConnectionStatus.DISCONNECTED
    }

    /** Send a typed action over the socket. Returns false if not connected. */
    fun send(action: Action): Boolean {
        val socket = webSocket ?: return false
        return socket.send(json.encodeToString(Action.serializer(), action))
    }

    @Synchronized
    private fun openSocket() {
        val base = serverConfig.baseUrlOrNull()
        if (base == null) {
            _status.value = ConnectionStatus.DISCONNECTED
            return
        }
        _status.value = ConnectionStatus.CONNECTING
        val request = Request.Builder().url(ServerUrl.wsUrl(base)).build()
        webSocket = okHttpClient.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        if (manuallyClosed) return
        reconnectJob?.cancel()
        val backoff = (BASE_BACKOFF_MS * 2.0.pow(reconnectAttempts)).toLong()
        reconnectAttempts++
        reconnectJob = scope.launch {
            delay(min(MAX_BACKOFF_MS, backoff))
            if (!manuallyClosed) openSocket()
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempts = 0
            _status.value = ConnectionStatus.CONNECTED
            // Identify ourselves so the operator can designate this device an output.
            webSocket.send(
                json.encodeToString(
                    Action.serializer(),
                    Action.Register(name = deviceName, clientId = networkStore.clientId),
                ),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = try {
                json.decodeFromString(ServerMessage.serializer(), text)
            } catch (e: IllegalArgumentException) {
                _errors.tryEmit("Unparseable message from server: ${e.message}")
                return
            }
            when (message) {
                is ServerMessage.StateSnapshot -> _state.value = message.state
                is ServerMessage.StateChanged -> _state.value = message.state
                is ServerMessage.SfxFired -> _sfxEvents.tryEmit(message)
                is ServerMessage.Error -> _errors.tryEmit(message.detail)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _status.value = ConnectionStatus.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _status.value = ConnectionStatus.DISCONNECTED
            _errors.tryEmit(t.message ?: "Connection lost")
            scheduleReconnect()
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val EVENT_BUFFER = 16
    }
}
