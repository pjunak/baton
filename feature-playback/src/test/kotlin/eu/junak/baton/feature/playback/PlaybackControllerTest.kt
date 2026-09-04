package eu.junak.baton.feature.playback

import eu.junak.baton.core.model.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {
    private val phone = "phone"
    private val active = PlayerState(activeOutputDeviceIds = listOf(phone))

    @Test
    fun `remote removal revokes music and SFX permission and can be reversed`() {
        assertTrue(isCanonicalOutput(active, phone))
        val otherSpeaker = active.copy(activeOutputDeviceIds = listOf("room"))
        assertFalse(isCanonicalOutput(otherSpeaker, phone))
        assertTrue(isCanonicalOutput(active, phone))
    }

    @Test
    fun `disconnect and reconnect require a new active snapshot`() {
        assertTrue(isCanonicalOutput(active, phone))
        assertFalse(isCanonicalOutput(null, phone))
        assertFalse(isCanonicalOutput(PlayerState(activeOutputDeviceIds = listOf("room")), phone))
        assertTrue(isCanonicalOutput(active, phone))
    }

    @Test
    fun `unselected phone never has audio permission`() {
        assertFalse(isCanonicalOutput(PlayerState(), phone))
    }
}
