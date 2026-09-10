package eu.junak.baton.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.MediaUrls
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.sync.ConnectionStatus
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class LibraryEvent { SELECT_OUTPUT, SEND_FAILED, QUEUE_REQUESTED }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    libraryApi: LibraryApi,
    private val syncClient: SyncClient,
    private val mediaUrls: MediaUrls,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val browser = LibraryBrowser(
        libraryApi,
        viewModelScope,
        initial = savedStateHandle.get<String>(NAVIGATION_KEY)?.let {
            runCatching { Json.decodeFromString<LibraryNavigation>(it) }.getOrNull()
        } ?: LibraryNavigation(),
        saveNavigation = { savedStateHandle[NAVIGATION_KEY] = Json.encodeToString(it) },
    )
    val ui = browser.state
    val connection = syncClient.status
    private val eventChannel = Channel<LibraryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun openFolder(path: String) = browser.openFolder(path)
    fun openAncestor(path: String) = browser.openAncestor(path)
    fun goUp() = browser.goUp()
    fun back() = browser.back()
    fun onQueryChange(query: String) = browser.onQueryChange(query)
    fun refresh() = browser.refresh()
    fun rememberScroll(key: String, index: Int, offset: Int) = browser.rememberScroll(key, index, offset)
    fun coverUrl(trackId: Int): String? = mediaUrls.cover(trackId)
    fun openContainingFolder(track: Track) = openFolder(parentLibraryPath(track.path))

    fun playTrack(track: Track) = send(Action.AmbientPlayTrack(track.id), startsPlayback = true)
    fun playCurrentFolder() = send(Action.AmbientPlayFolder(ui.value.location.path), startsPlayback = true)
    fun playInterrupt(track: Track) = send(
        Action.FireInterruptTrack(trackId = track.id, fadeInMs = 500, fadeOutMs = 500),
        startsPlayback = true,
    )

    fun enqueue(track: Track) {
        if (send(Action.AmbientEnqueue(trackId = track.id))) eventChannel.trySend(LibraryEvent.QUEUE_REQUESTED)
    }

    private fun send(action: Action, startsPlayback: Boolean = false): Boolean {
        val result = sendLibraryAction(
            action,
            startsPlayback,
            connected = syncClient.status.value == ConnectionStatus.CONNECTED,
            hasOutput = syncClient.liveState.value?.activeOutputDeviceIds?.isNotEmpty() == true,
            send = syncClient::send,
        )
        when (result) {
            LibraryActionResult.SELECT_OUTPUT -> eventChannel.trySend(LibraryEvent.SELECT_OUTPUT)
            LibraryActionResult.FAILED -> eventChannel.trySend(LibraryEvent.SEND_FAILED)
            LibraryActionResult.SENT -> Unit
        }
        return result == LibraryActionResult.SENT
    }

    private companion object {
        const val NAVIGATION_KEY = "library_navigation"
    }
}

internal enum class LibraryActionResult { SENT, SELECT_OUTPUT, FAILED }

/** Gate live actions at dispatch time as well as disabling their visible controls. */
internal fun sendLibraryAction(
    action: Action,
    startsPlayback: Boolean,
    connected: Boolean,
    hasOutput: Boolean,
    send: (Action) -> Boolean,
): LibraryActionResult = when {
    !connected -> LibraryActionResult.FAILED
    startsPlayback && !hasOutput -> LibraryActionResult.SELECT_OUTPUT
    send(action) -> LibraryActionResult.SENT
    else -> LibraryActionResult.FAILED
}
