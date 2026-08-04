package eu.junak.baton.ui.main

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
}
