package eu.junak.baton.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueOrderTest {
    @Test
    fun `move uses queue position when ids repeat`() {
        assertEquals(listOf(7, 9, 7), moveQueueSlot(listOf(7, 7, 9), 1, 2))
    }

    @Test
    fun `move returns null for invalid or unchanged positions`() {
        val queue = listOf(7, 9)

        assertNull(moveQueueSlot(queue, -1, 0))
        assertNull(moveQueueSlot(queue, 0, 2))
        assertNull(moveQueueSlot(queue, 1, 1))
    }
}
