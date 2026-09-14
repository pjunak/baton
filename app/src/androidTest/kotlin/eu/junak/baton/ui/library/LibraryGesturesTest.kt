package eu.junak.baton.ui.library

import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import eu.junak.baton.core.model.Track
import eu.junak.baton.core.network.api.*
import eu.junak.baton.ui.theme.BatonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Compose gestures against deterministic local data, without a server or signed-in account. */
class LibraryGesturesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val api = FakeLibrary()
    private lateinit var browser: LibraryBrowser
    private lateinit var actions: LibraryCallbacks
    private val connected = mutableStateOf(true)
    private var plays = 0
    private var enqueues = 0
    private val playedFolders = mutableListOf<String>()

    @Before fun setup() {
        browser = LibraryBrowser(api, scope)
        actions = LibraryCallbacks(
            onBack = browser::back,
            onUp = browser::goUp,
            onAncestor = browser::openAncestor,
            onFolder = browser::openFolder,
            onQuery = browser::onQueryChange,
            onRefresh = browser::refresh,
            onPlayFolder = { playedFolders += it },
            onPlay = { plays++ },
            onEnqueue = { enqueues++ },
            onInterrupt = { plays++ },
            onContainingFolder = { browser.openFolder(parentLibraryPath(it.path)) },
            onScroll = browser::rememberScroll,
            coverUrl = { null },
        )
    }

    @After fun cleanup() { scope.cancel() }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val ui by browser.state.collectAsState()
            BatonTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) { LibraryScreenContent(ui, connected.value, actions) }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        compose.waitForIdle()
    }

    @Test fun rootHasNoControlShelfAndNestedFoldersKeepBreadcrumbs() {
        show()
        compose.onNodeWithText("Library").assertDoesNotExist()
        compose.onNodeWithText("Folders").assertDoesNotExist()
        compose.onNodeWithText("Tracks").assertDoesNotExist()
        compose.onNodeWithText("Play this folder").assertDoesNotExist()
        val search = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val refresh = compose.onNodeWithContentDescription("Refresh").fetchSemanticsNode().boundsInRoot
        assertTrue(refresh.top >= search.top && refresh.bottom <= search.bottom)
        compose.onNodeWithText("Ambience").performClick()
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithContentDescription("Up").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("", browser.state.value.location.path) }
    }

    @Test fun folderLongPressPlaysTheTouchedPathAndTapStillNavigates() {
        show()
        compose.onNodeWithText("Ambience").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf("Ambience"), playedFolders)
            assertEquals("", browser.state.value.location.path)
            assertEquals(0, plays)
        }
        compose.onNodeWithText("Ambience").performClick()
        compose.runOnIdle { assertEquals("Ambience", browser.state.value.location.path) }
        compose.onNodeWithText("Rain").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(listOf("Ambience", "Ambience/Rain"), playedFolders)
            assertEquals("Ambience", browser.state.value.location.path)
        }
    }

    @Test fun folderSwipePlaysOnceAndShortSwipesOrRestorationDoNotReplay() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("Ambience").performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.05f, centerY), 600)
        }
        compose.runOnIdle {
            assertEquals(listOf("Ambience"), playedFolders)
            assertEquals("", browser.state.value.location.path)
            assertEquals(0, enqueues)
        }
        compose.onNodeWithText("Ambience").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Ambience").performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.5f, centerY), 700)
        }
        compose.runOnIdle { assertEquals(listOf("Ambience"), playedFolders) }
    }

    @Test fun folderPlaybackHasALabelledAccessibilityAction() {
        show()
        compose.onNodeWithText("Ambience").assert(SemanticsMatcher("Play folder action is labelled") {
            it.config[SemanticsActions.OnLongClick].label == "Play folder Ambience"
        }).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle { assertEquals(listOf("Ambience"), playedFolders) }
    }

    @Test fun offlineFoldersCanOpenButCannotPlay() {
        connected.value = false
        show()
        compose.onNodeWithText("Ambience").assertIsEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            .performTouchInput { longClick() }
        compose.onNodeWithText("Ambience").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertTrue(playedFolders.isEmpty()) }
        compose.onNodeWithText("Ambience").performClick()
        compose.runOnIdle { assertEquals("Ambience", browser.state.value.location.path) }
    }

    @Test fun emptyFoldersCanOpenButCannotPlay() {
        show()
        compose.onNodeWithText("Empty").assertIsEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            .performTouchInput { longClick() }
        compose.onNodeWithText("Empty").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertTrue(playedFolders.isEmpty()) }
        compose.onNodeWithText("Empty").performClick()
        compose.runOnIdle { assertEquals("Empty", browser.state.value.location.path) }
    }

    @Test fun tapPlaysAndLongPressMenuOmitsPlayNow() {
        show()
        screenshot("library")
        compose.onNodeWithText("Rain 1").performClick()
        compose.runOnIdle { assertEquals(1, plays) }
        compose.onNodeWithText("Rain 1").performTouchInput { longClick() }
        compose.onNodeWithText("Play now").assertDoesNotExist()
        compose.onNodeWithText("Play as interrupt").assertIsDisplayed()
        screenshot("track-actions")
        compose.onNodeWithText("Add to queue").performClick()
        compose.runOnIdle { assertEquals(1, enqueues); assertEquals(1, plays) }
    }

    @Test fun swipeEnqueuesOnceAndRestoringUiDoesNotReplayIt() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("Rain 1").performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.05f, centerY), 600)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, enqueues); assertEquals(0, plays) }
        compose.onNodeWithText("Rain 1").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(1, enqueues) }
        compose.onNodeWithText("Rain 1").performTouchInput {
            swipe(Offset(width * 0.6f, centerY), Offset(width * 0.5f, centerY), 700)
        }
        compose.runOnIdle { assertEquals(1, enqueues) }
    }

    @Test fun offlineRowsCannotPlayOrEnqueueButOptionsRemainAvailable() {
        connected.value = false
        show()
        compose.onNodeWithText("Rain 1").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText("Rain 1").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, plays); assertEquals(0, enqueues) }
        compose.onNodeWithContentDescription("More options for Rain 1").performClick()
        compose.onNodeWithText("Add to queue").assertIsNotEnabled()
    }

    @Test fun pullRefreshAndVisibleRefreshBothReloadTheCurrentFolder() {
        show()
        val first = api.treeCalls
        compose.onNodeWithTag("library_list").performTouchInput {
            swipe(Offset(centerX, 20f), Offset(centerX, height * 0.8f), 600)
        }
        compose.runOnIdle { assertEquals(first + 1, api.treeCalls); assertTrue(playedFolders.isEmpty()) }
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.runOnIdle { assertEquals(first + 2, api.treeCalls) }
    }

    @Test fun backRestoresScrollAndCancelledPredictiveBackKeepsTheFolder() {
        show()
        compose.onNodeWithTag("library_list").performScrollToIndex(20)
        var originalIndex = 0
        compose.runOnIdle {
            originalIndex = browser.state.value.location.firstVisibleItem
            assertTrue(originalIndex > 0)
            browser.openFolder("Ambience")
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val dispatcher = compose.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 300f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { assertEquals("Ambience", browser.state.value.location.path) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("", browser.state.value.location.path)
            assertEquals(originalIndex, browser.state.value.location.firstVisibleItem)
        }
    }

    @Test fun searchResultCanOpenItsFolderAndBackReturnsToSearch() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("rain")
        compose.waitUntil(5_000) { browser.state.value.location.searching && !browser.state.value.loading }
        compose.onNodeWithContentDescription("More options for Rain 1").performClick()
        compose.onNodeWithText("Open containing folder").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("Ambience", browser.state.value.location.path) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("rain", browser.state.value.location.query) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    private class FakeLibrary : LibraryApi {
        var treeCalls = 0
        private val tracks = (1..50).map { id -> Track(id, "Ambience/rain$id.mp3", "Rain $id", "Field recordings", "", "", addedAt = "2026-09-10") }
        override suspend fun tree(path: String): TreeResponse { treeCalls++; return TreeResponse(path, tracks) }
        override suspend fun folders() = FoldersResponse(listOf(
            FolderOut("Ambience", "Ambience", 50, true),
            FolderOut("Rain", "Ambience/Rain", 50, false),
            FolderOut("Empty", "Empty", 0, false),
        ))
        override suspend fun search(query: String, limit: Int, offset: Int, sort: String, order: String) = SearchResponse(tracks, 50, limit, offset, sort, order)
        override suspend fun track(id: Int) = tracks.first { it.id == id }
        override suspend fun tracks(ids: String) = tracks
    }
}
