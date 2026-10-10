package eu.junak.baton.ui.console

import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.InterruptState
import eu.junak.baton.core.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekActionTest {
    @Test
    fun `seeking without an interrupt moves ambient`() {
        assertEquals(Action.AmbientSeek(1_000), seekAction(PlayerState(), 1_000))
    }

    @Test
    fun `seeking during an interrupt moves the interrupt the bar shows`() {
        val state = PlayerState(interrupt = InterruptState(currentTrackId = 7))
        assertEquals(Action.InterruptSeek(1_000), seekAction(state, 1_000))
    }
}
