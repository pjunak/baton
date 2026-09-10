package eu.junak.baton.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueOrderTest {
    @Test
    fun `drag is discarded when another controller changed the queue`() {
        assertNull(moveQueueSlot(listOf(7, 9, 10), 0, 2, expectedQueue = listOf(7, 9)))
        assertEquals(listOf(9, 7), moveQueueSlot(listOf(7, 9), 0, 1, expectedQueue = listOf(7, 9)))
    }

    @Test
    fun `drop targets use measured rows rather than assuming a fixed height`() {
        val rows = listOf(QueueRowBounds(4, -20f, 80f), QueueRowBounds(5, 72f, 120f), QueueRowBounds(6, 204f, 80f))
        assertEquals(4, queueDropTarget(0f, rows))
        assertEquals(5, queueDropTarget(130f, rows))
        assertEquals(6, queueDropTarget(1000f, rows))
        assertNull(queueDropTarget(100f, emptyList()))
    }

    @Test
    fun `auto scroll stays idle in the middle and is bounded at both edges`() {
        assertEquals(0f, queueAutoScrollSpeed(150f, 230f, 0f, 500f, 64f))
        assertEquals(-700f, queueAutoScrollSpeed(-20f, 60f, 0f, 500f, 64f))
        assertEquals(700f, queueAutoScrollSpeed(450f, 530f, 0f, 500f, 64f))
        assertEquals(-350f, queueAutoScrollSpeed(32f, 112f, 0f, 500f, 64f))
    }

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
