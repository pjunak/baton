package eu.junak.baton.ui.library

import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.api.FolderOut
import eu.junak.baton.core.network.api.LibraryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class LibraryLocation(
    val path: String = "",
    val query: String = "",
    val firstVisibleItem: Int = 0,
    val scrollOffset: Int = 0,
) {
    val key: String get() = "$path\u0000$query"
    val searching: Boolean get() = query.isNotBlank()
}

@Serializable
data class LibraryNavigation(
    val current: LibraryLocation = LibraryLocation(),
    val history: List<LibraryLocation> = emptyList(),
    val positions: List<LibraryLocation> = emptyList(),
)

data class LibraryContent(
    val folders: List<FolderOut> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

data class LibraryBrowserState(
    val location: LibraryLocation = LibraryLocation(),
    val backTarget: LibraryLocation? = null,
    val content: LibraryContent? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
)

/** Local browsing state only. Playback remains exclusively owned by the server. */
internal class LibraryBrowser(
    private val api: LibraryApi,
    private val scope: CoroutineScope,
    initial: LibraryNavigation = LibraryNavigation(),
    private val saveNavigation: (LibraryNavigation) -> Unit = {},
) {
    private var navigation = initial
    private val _state = MutableStateFlow(LibraryBrowserState())
    val state = _state.asStateFlow()
    private var folders: List<FolderOut>? = null
    private val cache = linkedMapOf<String, LibraryContent>()
    private var request: Job? = null
    private var generation = 0L

    init {
        load()
    }

    fun openFolder(path: String) {
        val next = remembered(LibraryLocation(path))
        if (next.key == navigation.current.key) return
        navigate(next, navigation.history + navigation.current)
    }

    /** An explicit ancestor jump trims descendants; Back still restores a search origin. */
    fun openAncestor(path: String) {
        if (path == navigation.current.path && !navigation.current.searching) return
        val index = navigation.history.indexOfLast { it.path == path && !it.searching }
        if (index >= 0) navigate(navigation.history[index], navigation.history.take(index))
        else navigate(remembered(LibraryLocation(path)), emptyList())
    }

    fun goUp() {
        val current = navigation.current
        if (current.path.isNotEmpty()) openAncestor(parentLibraryPath(current.path))
    }

    fun back() {
        val target = backTarget() ?: return
        navigate(target, navigation.history.dropLast(1))
    }

    fun onQueryChange(value: String) {
        val query = value.takeIf { it.isNotBlank() }.orEmpty()
        val current = navigation.current
        if (query == current.query) return
        if (query.isEmpty()) {
            val origin = navigation.history.lastOrNull()
            if (origin != null && origin.path == current.path && !origin.searching) back()
            else openAncestor(current.path)
            return
        }
        val history = if (current.searching) navigation.history else navigation.history + current
        navigate(LibraryLocation(current.path, query), history, debounce = true)
    }

    fun refresh() = load(refresh = true)

    fun rememberScroll(key: String, index: Int, offset: Int) {
        val old = if (navigation.current.key == key) navigation.current
        else navigation.positions.lastOrNull { it.key == key }
            ?: navigation.history.lastOrNull { it.key == key }
            ?: return
        val position = old.copy(firstVisibleItem = index.coerceAtLeast(0), scrollOffset = offset.coerceAtLeast(0))
        if (position == old) return
        navigation = navigation.copy(
            current = if (navigation.current.key == key) position else navigation.current,
            history = navigation.history.map { if (it.key == key) position else it },
            positions = (navigation.positions.filterNot { it.key == key } + position).takeLast(MAX_LOCATIONS),
        )
        _state.value = _state.value.copy(location = navigation.current, backTarget = backTarget())
        saveNavigation(navigation)
    }

    private fun remembered(location: LibraryLocation): LibraryLocation =
        navigation.positions.lastOrNull { it.key == location.key } ?: location

    private fun backTarget(): LibraryLocation? = navigation.history.lastOrNull()
        ?: navigation.current.let {
            when {
                it.searching -> remembered(LibraryLocation(it.path))
                it.path.isNotEmpty() -> remembered(LibraryLocation(parentLibraryPath(it.path)))
                else -> null
            }
        }

    private fun navigate(next: LibraryLocation, history: List<LibraryLocation>, debounce: Boolean = false) {
        val old = navigation.current
        navigation = navigation.copy(
            current = next,
            history = history.takeLast(MAX_LOCATIONS),
            positions = (navigation.positions.filterNot { it.key == old.key } + old).takeLast(MAX_LOCATIONS),
        )
        saveNavigation(navigation)
        load(debounce = debounce)
    }

    private fun load(refresh: Boolean = false, debounce: Boolean = false) {
        val token = ++generation
        request?.cancel()
        val location = navigation.current
        val previous = cache[location.key]
        val needsLoad = refresh || previous == null || (!location.searching && location.path.isEmpty())
        _state.value = LibraryBrowserState(location, backTarget(), previous, loading = needsLoad)
        if (!needsLoad) return
        request = scope.launch {
            try {
                if (debounce) delay(SEARCH_DEBOUNCE_MS)
                var newFolders: List<FolderOut>? = null
                val content = if (location.searching) {
                    LibraryContent(tracks = api.search(location.query).tracks)
                } else {
                    coroutineScope {
                        val tree = async { api.tree(location.path) }
                        val hierarchy = if (refresh || folders == null || location.path.isEmpty()) {
                            api.folders().folders.also { newFolders = it }
                        } else requireNotNull(folders)
                        val response = tree.await()
                        check(response.path == location.path) { "Unexpected folder response" }
                        LibraryContent(
                            hierarchy.filter { parentLibraryPath(it.path) == location.path },
                            response.tracks,
                        )
                    }
                }
                // Some data sources can still complete after their request was cancelled.
                if (token != generation) return@launch
                newFolders?.let {
                    folders = it
                    cache.clear()
                }
                cache.remove(location.key)
                cache[location.key] = content
                while (cache.size > MAX_CACHED_VIEWS) cache.remove(cache.keys.first())
                _state.value = _state.value.copy(content = content, loading = false, failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (token == generation) _state.value = _state.value.copy(loading = false, failed = true)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MAX_LOCATIONS = 50
        const val MAX_CACHED_VIEWS = 12
    }
}

internal fun parentLibraryPath(path: String): String = path.substringBeforeLast('/', "")
