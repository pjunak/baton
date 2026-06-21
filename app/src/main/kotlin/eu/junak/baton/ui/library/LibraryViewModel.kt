package eu.junak.baton.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.api.FolderOut
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read-only library browser. Walks the folder tree (LibraryApi.tree), searches
 * (LibraryApi.search), and starts playback by sending ambient actions through
 * [SyncClient] — the server is the one that actually plays.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryApi: LibraryApi,
    private val syncClient: SyncClient,
) : ViewModel() {

    data class UiState(
        val path: String = "",
        val folders: List<FolderOut> = emptyList(),
        val tracks: List<Track> = emptyList(),
        val query: String = "",
        val searchResults: List<Track>? = null,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        loadFolder("")
    }

    fun loadFolder(path: String) {
        _ui.update { it.copy(loading = true, error = null, query = "", searchResults = null) }
        viewModelScope.launch {
            runCatching { libraryApi.tree(path) }
                .onSuccess { response ->
                    _ui.update {
                        it.copy(
                            path = response.path,
                            folders = response.folders,
                            tracks = response.tracks,
                            loading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loading = false, error = e.message ?: "Couldn't load this folder.") }
                }
        }
    }

    fun goUp() {
        val current = _ui.value.path
        if (current.isNotEmpty()) loadFolder(current.substringBeforeLast('/', ""))
    }

    fun openFolder(folder: FolderOut) = loadFolder(folder.path)

    fun onQueryChange(query: String) {
        _ui.update { it.copy(query = query) }
        if (query.isBlank()) {
            _ui.update { it.copy(searchResults = null) }
            return
        }
        viewModelScope.launch {
            runCatching { libraryApi.search(query) }
                .onSuccess { response ->
                    // Drop a stale response if the query has since moved on.
                    if (_ui.value.query == query) {
                        _ui.update { it.copy(searchResults = response.tracks) }
                    }
                }
        }
    }

    fun playTrack(track: Track) {
        syncClient.send(Action.AmbientPlayTrack(track.id))
    }

    fun enqueue(track: Track) {
        syncClient.send(Action.AmbientEnqueue(trackId = track.id))
    }

    fun playCurrentFolder() {
        syncClient.send(Action.AmbientPlayFolder(path = _ui.value.path))
    }
}
