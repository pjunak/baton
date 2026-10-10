package eu.junak.baton.ui.main

import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.InterruptState
import eu.junak.baton.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayControlTest {
    @Test
    fun `starting without an active output opens output selection`() {
        assertTrue(shouldSelectOutputBeforePlay(isPlaying = false, hasActiveOutput = false))
    }

    @Test
    fun `starting with an active output proceeds directly`() {
        assertFalse(shouldSelectOutputBeforePlay(isPlaying = false, hasActiveOutput = true))
    }

    @Test
    fun `pause remains available after the last output disappears`() {
        assertFalse(shouldSelectOutputBeforePlay(isPlaying = true, hasActiveOutput = false))
    }

    @Test
    fun `paused ambient can be resumed while an interrupt plays`() {
        val state = PlayerState(isPlaying = false, interrupt = InterruptState(currentTrackId = 7))
        assertEquals(Action.Resume, playPauseAction(state))
    }

    @Test
    fun `playing ambient is paused`() {
        assertEquals(Action.Pause, playPauseAction(PlayerState(isPlaying = true)))
    }
}
