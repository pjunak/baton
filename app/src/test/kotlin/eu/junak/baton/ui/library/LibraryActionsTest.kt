package eu.junak.baton.ui.library

import eu.junak.baton.core.model.Action
import org.junit.Assert.*
import org.junit.Test

class LibraryActionsTest {
    @Test
    fun `offline actions never send`() {
        assertEquals(LibraryActionResult.FAILED, sendLibraryAction(Action.AmbientPlayTrack(1), true, false, true) {
            fail("Offline action sent")
            true
        })
    }

    @Test
    fun `all playback starts require an output and are not deferred`() {
        val starts = listOf(Action.AmbientPlayTrack(1), Action.AmbientPlayFolder("a"), Action.FireInterruptTrack(trackId = 1))
        starts.forEach { action ->
            assertEquals(LibraryActionResult.SELECT_OUTPUT, sendLibraryAction(action, true, true, false) {
                fail("Playback started without an output")
                true
            })
        }
    }

    @Test
    fun `enqueue works without an output and sends exactly once`() {
        val sent = mutableListOf<Action>()
        val action = Action.AmbientEnqueue(trackId = 4)
        assertEquals(LibraryActionResult.SENT, sendLibraryAction(action, false, true, false) { sent += it; true })
        assertEquals(listOf(action), sent)
    }

    @Test
    fun `socket rejection is reported even after connection check`() {
        assertEquals(LibraryActionResult.FAILED, sendLibraryAction(Action.AmbientPlayTrack(1), true, true, true) { false })
    }
}
