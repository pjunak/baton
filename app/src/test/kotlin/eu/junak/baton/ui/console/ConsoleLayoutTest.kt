package eu.junak.baton.ui.console

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLayoutTest {
    @Test
    fun `landscape phone uses split console`() {
        assertTrue(useWideConsoleLayout(widthDp = 800f, heightDp = 360f))
    }

    @Test
    fun `large portrait window uses split console`() {
        assertTrue(useWideConsoleLayout(widthDp = 840f, heightDp = 1100f))
    }

    @Test
    fun `portrait phone keeps stacked console`() {
        assertFalse(useWideConsoleLayout(widthDp = 412f, heightDp = 915f))
    }
}
