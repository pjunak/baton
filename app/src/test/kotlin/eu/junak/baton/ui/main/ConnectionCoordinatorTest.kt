package eu.junak.baton.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionCoordinatorTest {
    @Test
    fun `sync sleeps only when both UI and speaker are inactive`() {
        assertFalse(shouldKeepSyncConnected(uiStarted = false, speakerEnabled = false))
        assertTrue(shouldKeepSyncConnected(uiStarted = true, speakerEnabled = false))
        assertTrue(shouldKeepSyncConnected(uiStarted = false, speakerEnabled = true))
        assertTrue(shouldKeepSyncConnected(uiStarted = true, speakerEnabled = true))
    }
}
