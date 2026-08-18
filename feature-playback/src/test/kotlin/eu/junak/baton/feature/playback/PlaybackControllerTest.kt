package eu.junak.baton.feature.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {

    @Test
    fun `canonical activation starts local playback`() {
        assertTrue(
            shouldStartLocalPlayback(
                canonicallyActive = true,
                locallyEnabled = false,
            ),
        )
    }

    @Test
    fun `inactive membership does not start local playback`() {
        assertFalse(
            shouldStartLocalPlayback(
                canonicallyActive = false,
                locallyEnabled = false,
            ),
        )
    }

    @Test
    fun `an enabled local service is not started twice`() {
        assertFalse(
            shouldStartLocalPlayback(
                canonicallyActive = true,
                locallyEnabled = true,
            ),
        )
    }
}
