package eu.junak.baton.ui.library

import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.api.FolderOut
import eu.junak.baton.core.network.api.FoldersResponse
import eu.junak.baton.core.network.api.LibraryApi
import eu.junak.baton.core.network.api.SearchResponse
import eu.junak.baton.core.network.api.TreeResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBrowserTest {
    @Test
    fun `back restores parent scroll position and root hands back to Android`() = runTest {
        val browser = LibraryBrowser(FakeLibrary(), this)
        advanceUntilIdle()
        browser.rememberScroll(browser.state.value.location.key, 17, 23)
        browser.openFolder("a")
        advanceUntilIdle()
        browser.rememberScroll(browser.state.value.location.key, 4, 56)
        browser.openFolder("a/forest")
        advanceUntilIdle()
        browser.back()
        advanceUntilIdle()
        assertEquals(LibraryLocation("a", firstVisibleItem = 4, scrollOffset = 56), browser.state.value.location)
        browser.back()
        advanceUntilIdle()
        assertEquals(LibraryLocation(firstVisibleItem = 17, scrollOffset = 23), browser.state.value.location)
        assertNull(browser.state.value.backTarget)
    }

    @Test
    fun `search result folder returns to the query and result position`() = runTest {
        val api = FakeLibrary()
        val browser = LibraryBrowser(api, this)
        advanceUntilIdle()
        browser.openFolder("a")
        advanceUntilIdle()
        browser.onQueryChange("rain")
        advanceUntilIdle()
        browser.rememberScroll(browser.state.value.location.key, 8, 12)
        browser.openFolder("b")
        advanceUntilIdle()
        browser.back()
        advanceUntilIdle()
        assertEquals(LibraryLocation("a", "rain", 8, 12), browser.state.value.location)
        browser.back()
        advanceUntilIdle()
        assertEquals("a", browser.state.value.location.path)
        assertFalse(browser.state.value.location.searching)
    }

    @Test
    fun `ancestor jump drops descendants without a back loop`() = runTest {
        val browser = LibraryBrowser(FakeLibrary(), this)
        advanceUntilIdle()
        browser.openFolder("a")
        browser.openFolder("a/forest")
        advanceUntilIdle()
        browser.openAncestor("")
        advanceUntilIdle()
        assertNull(browser.state.value.backTarget)
        browser.back()
        assertEquals("", browser.state.value.location.path)
    }

    @Test
    fun `search debounces and clearing it cancels pending results`() = runTest {
        val api = FakeLibrary()
        val browser = LibraryBrowser(api, this)
        advanceUntilIdle()
        browser.onQueryChange("r")
        advanceTimeBy(200)
        browser.onQueryChange("ra")
        advanceTimeBy(200)
        assertTrue(api.queries.isEmpty())
        browser.onQueryChange("")
        advanceUntilIdle()
        assertTrue(api.queries.isEmpty())
        assertFalse(browser.state.value.location.searching)
        assertNull(browser.state.value.backTarget)
    }

    @Test
    fun `older folder response cannot replace newer navigation`() = runTest {
        val old = CompletableDeferred<TreeResponse>()
        val api = FakeLibrary().apply {
            treeResponse = { path ->
                if (path == "a") withContext(NonCancellable) { old.await() }
                else TreeResponse(path, listOf(track(path)))
            }
        }
        val browser = LibraryBrowser(api, this)
        advanceUntilIdle()
        browser.openFolder("a")
        runCurrent()
        browser.openFolder("b")
        runCurrent()
        assertEquals("b", browser.state.value.content!!.tracks.single().path)
        old.complete(TreeResponse("a", listOf(track("a"))))
        advanceUntilIdle()
        assertEquals("b", browser.state.value.location.path)
        assertEquals("b", browser.state.value.content!!.tracks.single().path)
        assertFalse(browser.state.value.failed)
    }

    @Test
    fun `search completion after opening a folder cannot overwrite that folder`() = runTest {
        val old = CompletableDeferred<SearchResponse>()
        val api = FakeLibrary().apply { searchResponse = { withContext(NonCancellable) { old.await() } } }
        val browser = LibraryBrowser(api, this)
        advanceUntilIdle()
        browser.onQueryChange("rain")
        advanceTimeBy(300)
        runCurrent()
        browser.openFolder("b")
        runCurrent()
        old.complete(search(listOf(track("rain"))))
        advanceUntilIdle()
        assertFalse(browser.state.value.location.searching)
        assertEquals("b", browser.state.value.content!!.tracks.single().path)
    }

    @Test
    fun `refresh retains contents and position on failure and retry updates hierarchy`() = runTest {
        val api = FakeLibrary()
        val browser = LibraryBrowser(api, this)
        advanceUntilIdle()
        browser.openFolder("a")
        advanceUntilIdle()
        browser.rememberScroll(browser.state.value.location.key, 6, 9)
        val original = browser.state.value.content
        api.treeResponse = { error("Unavailable") }
        browser.refresh()
        assertEquals(original, browser.state.value.content)
        assertTrue(browser.state.value.loading)
        advanceUntilIdle()
        assertTrue(browser.state.value.failed)
        assertEquals(original, browser.state.value.content)
        assertEquals(6, browser.state.value.location.firstVisibleItem)
        api.hierarchy += FolderOut("new", "a/new", 0, false)
        api.treeResponse = { TreeResponse(it, listOf(track("updated"))) }
        browser.refresh()
        advanceUntilIdle()
        assertFalse(browser.state.value.failed)
        assertTrue(browser.state.value.content!!.folders.any { it.path == "a/new" })
        assertEquals("updated", browser.state.value.content!!.tracks.single().path)
    }

    @Test
    fun `failed search offers retry while preserving the query`() = runTest {
        val api = FakeLibrary().apply { searchResponse = { error("Unavailable") } }
        val browser = LibraryBrowser(api, this)
        advanceUntilIdle()
        browser.onQueryChange("rain")
        advanceUntilIdle()
        assertTrue(browser.state.value.failed)
        assertEquals("rain", browser.state.value.location.query)
        api.searchResponse = { search(listOf(track(it))) }
        browser.refresh()
        advanceUntilIdle()
        assertFalse(browser.state.value.failed)
        assertEquals("rain", browser.state.value.content!!.tracks.single().path)
    }

    @Test
    fun `saved navigation restores search history and scroll after recreation`() = runTest {
        var saved = LibraryNavigation()
        val browser = LibraryBrowser(FakeLibrary(), this, saveNavigation = { saved = it })
        advanceUntilIdle()
        browser.openFolder("a")
        advanceUntilIdle()
        browser.rememberScroll(browser.state.value.location.key, 12, 15)
        browser.onQueryChange("rain")
        advanceUntilIdle()
        browser.rememberScroll(browser.state.value.location.key, 3, 20)
        val restored = LibraryBrowser(FakeLibrary(), this, Json.decodeFromString(Json.encodeToString(saved)))
        advanceUntilIdle()
        assertEquals(LibraryLocation("a", "rain", 3, 20), restored.state.value.location)
        restored.back()
        advanceUntilIdle()
        assertEquals(LibraryLocation("a", firstVisibleItem = 12, scrollOffset = 15), restored.state.value.location)
    }

    private class FakeLibrary : LibraryApi {
        var hierarchy = listOf(FolderOut("a", "a", 2, true), FolderOut("forest", "a/forest", 1, false), FolderOut("b", "b", 1, false))
        val queries = mutableListOf<String>()
        var treeResponse: suspend (String) -> TreeResponse = { TreeResponse(it, listOf(track(it))) }
        var searchResponse: suspend (String) -> SearchResponse = { search(listOf(track(it))) }
        override suspend fun tree(path: String) = treeResponse(path)
        override suspend fun folders() = FoldersResponse(hierarchy)
        override suspend fun search(query: String, limit: Int, offset: Int, sort: String, order: String): SearchResponse {
            queries += query
            return searchResponse(query)
        }
        override suspend fun track(id: Int) = track("$id")
        override suspend fun tracks(ids: String) = emptyList<Track>()
    }

    companion object {
        private fun track(path: String) = Track(path.hashCode(), path, path, "", "", "", addedAt = "2026-09-10")
        private fun search(tracks: List<Track>) = SearchResponse(tracks, tracks.size, 100, 0, "artist", "asc")
    }
}
