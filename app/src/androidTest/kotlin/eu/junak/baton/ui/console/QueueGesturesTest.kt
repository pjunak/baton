package eu.junak.baton.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import eu.junak.baton.core.model.Track
import eu.junak.baton.ui.theme.BatonTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class QueueGesturesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val entries = (1..40).map { id ->
        ConsoleViewModel.QueueEntry(id, Track(id, "$id.mp3", "Track $id", "Artist", "", "", addedAt = "2026-09-10"))
    }
    private val ui = mutableStateOf(ConsoleViewModel.UiState(connected = true, queue = entries))
    private val moves = mutableListOf<Triple<Int, Int, List<Int>?>>()
    private var plays = 0

    @After fun releasePointer() {
        compose.mainClock.autoAdvance = false
        runCatching { compose.onNodeWithTag("queue_list").performTouchInput { cancel() } }
        compose.mainClock.autoAdvance = true
    }

    private fun show() {
        compose.setContent {
            BatonTheme(dynamicColor = false) {
                QueueListContent(
                    ui.value,
                    QueueCallbacks(
                        onMove = { from, to, expected -> moves += Triple(from, to, expected) },
                        onJump = { plays++ }, onRemove = {}, onClear = {}, coverUrl = { null },
                    ),
                    Modifier.fillMaxSize(), PaddingValues(16.dp),
                )
            }
        }
    }

    private fun startDrag() {
        val list = compose.onNodeWithTag("queue_list")
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithContentDescription("Reorder Track 1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        compose.mainClock.autoAdvance = false
        list.performTouchInput { down(handle) }
        compose.mainClock.advanceTimeBy(700)
        list.performTouchInput { moveTo(Offset(handle.x, height - 30f), delayMillis = 500) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("queue_drag_preview").assertExists()
    }

    @Test fun draggingHandleScrollsAndCommitsOneMoveWithoutPlaying() {
        show()
        startDrag()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("queue_list").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(0, plays)
            assertEquals(1, moves.size)
            assertEquals(0, moves.single().first)
            assertTrue("Holding at the edge should reach beyond the initially visible rows", moves.single().second > 12)
            assertEquals(entries.map { it.trackId }, moves.single().third)
        }
    }

    @Test fun externalQueueChangeCancelsAnActiveDrag() {
        show()
        startDrag()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread { ui.value = ui.value.copy(queue = entries.drop(1)) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("queue_list").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertTrue(moves.isEmpty()); assertEquals(0, plays) }
    }
}
