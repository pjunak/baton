package eu.junak.baton.core.sync

import eu.junak.baton.core.model.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateRevisionTest {

    @Test
    fun `older state change is rejected`() {
        assertFalse(
            shouldAcceptStateChange(
                current = PlayerState(revision = 5, deviceVolumes = mapOf("tv-1" to 0.4)),
                incoming = PlayerState(revision = 4),
            ),
        )
    }

    @Test
    fun `equal revision is accepted for presence updates`() {
        assertTrue(
            shouldAcceptStateChange(
                current = PlayerState(revision = 5),
                incoming = PlayerState(revision = 5),
            ),
        )
    }

    @Test
    fun `first and newer states are accepted`() {
        assertTrue(shouldAcceptStateChange(null, PlayerState(revision = 1)))
        assertTrue(
            shouldAcceptStateChange(
                current = PlayerState(revision = 1),
                incoming = PlayerState(revision = 2),
            ),
        )
    }
}
