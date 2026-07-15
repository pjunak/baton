package eu.junak.baton.core.sync

import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.PlayerState
import eu.junak.baton.core.model.ServerMessage
import eu.junak.baton.core.network.NetworkMonitor
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
 * Reconnects with exponential backoff, and immediately when [NetworkMonitor]
 * reports connectivity is back — no attempt is made while the device has no
 * validated network (it could only fail; at app open that failure used to
 * surface as a spurious "Unable to resolve host" toast). Transport failures
 * are represented by [status] (the Console banner) plus [lastFailure], never
 * by [errors] — that flow carries only server-reported protocol errors. The
 * shared OkHttp client carries the session cookie, so the upgrade is
 * authenticated automatically.
 */
@Singleton
class SyncClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val serverConfig: ServerConfig,
    private val networkStore: NetworkStore,
    private val networkMonitor: NetworkMonitor,
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

    /** Server-reported error frames (e.g. a rejected action), for toasts.
     *  Transport failures do NOT pass through here — see [lastFailure]. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _lastFailure = MutableStateFlow<String?>(null)

    /** Why the last connection attempt failed (null once connected or after a
     *  fresh connect/disconnect). Persistent-failure diagnostic for the
     *  Console's disconnected banner — a wrong URL or a down server stays
     *  explained without toasting every self-healing reconnect attempt. */
    val lastFailure: StateFlow<String?> = _lastFailure.asStateFlow()

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when the server tells us the session is gone: a coded
     *  `session_expired`/`session_revoked` frame (session died mid-connection),
     *  or the guest-rejection an action gets after reconnecting with a dead
     *  cookie (the WS upgrade *succeeds* as guest, so there's no 401 to catch).
     *  The app routes this to the re-login flow instead of letting the user
     *  strand on a connected-but-powerless Console. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    @Volatile private var webSocket: WebSocket? = null

    /** True between [connect] and [disconnect]: the app wants a live socket,
     *  so reconnect machinery (backoff, network-regain) may act. */
    @Volatile private var wantConnected = false

    @Volatile private var deviceName: String = "Baton"

    @Volatile private var reconnectAttempts = 0

    private var reconnectJob: Job? = null

    init {
        // The moment a validated network (re)appears, connect NOW instead of
        // sleeping out whatever backoff accumulated while offline. This is
        // what makes app-open and Wi-Fi->cell handovers feel instant.
        scope.launch {
            networkMonitor.online.collect { online ->
                if (online && wantConnected && _status.value != ConnectionStatus.CONNECTED) {
                    reconnectNow()
                }
            }
        }
    }

    /** Open the socket and keep it alive (reconnecting) until [disconnect]. */
    @Synchronized
    fun connect(deviceName: String) {
        this.deviceName = deviceName
        wantConnected = true
        _lastFailure.value = null
        reconnectAttempts = 0
        reconnectJob?.cancel()
        openSocket()
    }

    @Synchronized
    fun disconnect() {
        wantConnected = false
        _lastFailure.value = null
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
        // Supersede any previous socket (two ViewModels observing DISCONNECTED
        // can both call connect()) — without this, the orphan keeps feeding
        // stale state into _state and its eventual close schedules a rogue
        // reconnect. Its late callbacks are ignored via the identity guard.
        webSocket?.cancel()
        webSocket = null
        _status.value = ConnectionStatus.CONNECTING
        if (!networkMonitor.online.value) {
            // No validated network: an attempt now can only fail (the spurious
            // "Unable to resolve host" at app open). Stay in CONNECTING; the
            // network-regain collector fires the real attempt the moment the
            // network is up, with the backoff loop kept as a safety net.
            scheduleReconnect()
            return
        }
        val request = Request.Builder().url(ServerUrl.wsUrl(base)).build()
        webSocket = okHttpClient.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        if (!wantConnected) return
        reconnectJob?.cancel()
        val backoff = (BASE_BACKOFF_MS * 2.0.pow(reconnectAttempts)).toLong()
        reconnectAttempts++
        reconnectJob = scope.launch {
            delay(min(MAX_BACKOFF_MS, backoff))
            if (wantConnected) openSocket()
        }
    }

    /** Skip any pending backoff and try again right away (network just came back). */
    @Synchronized
    private fun reconnectNow() {
        if (!wantConnected) return
        reconnectAttempts = 0
        reconnectJob?.cancel()
        openSocket()
    }

    /** True iff [socket] is still the one this client owns — a superseded
     *  socket's late callbacks must not touch shared state (stale snapshots
     *  overwriting fresh ones, rogue reconnect scheduling). */
    private fun isCurrent(socket: WebSocket): Boolean = socket === webSocket

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent(webSocket)) {
                webSocket.cancel()
                return
            }
            reconnectAttempts = 0
            _lastFailure.value = null
            // Identify ourselves so the operator can designate this device an
            // output. Sent BEFORE the status flips so anything reacting to
            // CONNECTED (e.g. re-asserting the output designation) enqueues
            // after register on this ordered socket.
            webSocket.send(
                json.encodeToString(
                    Action.serializer(),
                    Action.Register(name = deviceName, clientId = networkStore.clientId),
                ),
            )
            _status.value = ConnectionStatus.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket)) return
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
                is ServerMessage.Error -> {
                    if (message.code == "session_expired" ||
                        message.code == "session_revoked" ||
                        // Dead cookie at (re)connect: the upgrade succeeds as a
                        // guest, so session loss surfaces as this rejection on
                        // the first action instead of a coded frame.
                        message.detail.contains("guest sessions cannot mutate")
                    ) {
                        _sessionExpired.tryEmit(Unit)
                    }
                    _errors.tryEmit(message.detail)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(NORMAL_CLOSURE, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket)) return
            _status.value = ConnectionStatus.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(webSocket)) return
            _status.value = ConnectionStatus.DISCONNECTED
            // Transport failures are NOT toasted (reconnect is automatic, and at
            // app open a DNS race would spam "Unable to resolve host" for a
            // connection that heals itself a second later). The banner shows the
            // status; lastFailure gives it the why if the outage persists.
            _lastFailure.value = t.message ?: "Connection lost"
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
