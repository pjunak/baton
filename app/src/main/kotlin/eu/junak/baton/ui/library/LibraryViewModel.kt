package eu.junak.baton.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.MediaUrls
import eu.junak.baton.core.network.api.FolderOut
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.sync.SyncClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read-only library browser. Per the server contract, the folder hierarchy comes
 * from LibraryApi.folders as one whole-tree response that clients navigate locally;
 * LibraryApi.tree only supplies the tracks directly inside the open folder. Search
 * goes through LibraryApi.search, and playback starts by sending ambient actions
 * through [SyncClient] — the server is the one that actually plays.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryApi: LibraryApi,
    private val syncClient: SyncClient,
    private val mediaUrls: MediaUrls,
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

    /** Drives debounced search; updated on every keystroke, but only the value the user
     *  pauses on actually hits the network. */
    private val queryFlow = MutableStateFlow("")

    /** The whole folder hierarchy (any depth) from LibraryApi.folders. Re-fetched on
     *  every root load — so returning to the top picks up uploads and rescans — and
     *  reused while browsing deeper, which makes folder navigation one request per step. */
    private var allFolders: List<FolderOut>? = null

    init {
        loadFolder("")
        observeSearch()
    }

    fun loadFolder(path: String) {
        _ui.update { it.copy(loading = true, error = null, query = "", searchResults = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val tree = async { libraryApi.tree(path) }
                    val cached = allFolders
                    val folders =
                        if (cached != null && path.isNotEmpty()) cached
                        else libraryApi.folders().folders.also { allFolders = it }
                    folders to tree.await()
                }
            }
                .onSuccess { (folders, tree) ->
                    _ui.update {
                        it.copy(
                            path = tree.path,
                            folders = folders.filter { f -> f.parentPath == tree.path },
                            tracks = tree.tracks,
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
        queryFlow.value = query
    }

    /**
     * Debounced search: [collectLatest] cancels the pending [delay] (and any in-flight request)
     * the instant a newer keystroke arrives, so only the query the user paused on hits the network —
     * no per-keystroke request storm, and a newer result can never be overwritten by an older one.
     */
    private fun observeSearch() {
        viewModelScope.launch {
            queryFlow.collectLatest { query ->
                if (query.isBlank()) {
                    _ui.update { it.copy(searchResults = null) }
                    return@collectLatest
                }
                delay(SEARCH_DEBOUNCE_MS)
                runCatching { libraryApi.search(query) }
                    .onSuccess { response -> _ui.update { it.copy(searchResults = response.tracks) } }
            }
        }
    }

    /** Cover-art URL for a track id, for list thumbnails. */
    fun coverUrl(trackId: Int): String? = mediaUrls.cover(trackId)

    fun playTrack(track: Track) {
        syncClient.send(Action.AmbientPlayTrack(track.id))
    }

    fun enqueue(track: Track) {
        syncClient.send(Action.AmbientEnqueue(trackId = track.id))
    }

    /** Play [track] as an interrupt: it takes over now (ambient pauses) and returns when done. */
    fun playInterrupt(track: Track) {
        syncClient.send(Action.FireInterruptTrack(trackId = track.id, fadeInMs = 500, fadeOutMs = 500))
    }

    fun playCurrentFolder() {
        syncClient.send(Action.AmbientPlayFolder(path = _ui.value.path))
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** Parent folder of a library path: "a/b" -> "a", top-level "a" -> "" (the root). */
private val FolderOut.parentPath: String
    get() = path.substringBeforeLast('/', "")
