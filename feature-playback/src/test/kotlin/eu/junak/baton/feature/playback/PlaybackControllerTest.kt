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

    @Test
    fun `transport loss keeps the speaker role`() {
        val role = SpeakerRoleReducer()
        assertTrue(role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1).startService)
        val onDrop = role.onDisconnected(enabled = true)
        assertTrue(onDrop.enabled)
        assertFalse(onDrop.rejoin)
        assertFalse(onDrop.startService)
    }

    @Test
    fun `transport loss does not grant a role that was off`() {
        val role = SpeakerRoleReducer()
        assertFalse(role.onDisconnected(enabled = false).enabled)
        // First snapshot without us while the role is off: stay off, no rejoin.
        val decision = role.onState(PlayerState(), phone, enabled = false, now = 0, connectionEpoch = 1)
        assertFalse(decision.enabled)
        assertFalse(decision.rejoin)
    }

    @Test
    fun `membership pruned by a disconnect is rejoined once per connection`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onDisconnected(enabled = true)
        // Reconnect: the server pruned us from the outputs at disconnect.
        val rejoin = role.onState(PlayerState(), phone, enabled = true, now = 1_000, connectionEpoch = 2)
        assertTrue(rejoin.enabled)
        assertTrue(rejoin.rejoin)
        // Confirmation lands: member again, service already running.
        val confirmed = role.onState(active, phone, enabled = true, now = 2_000, connectionEpoch = 2)
        assertTrue(confirmed.enabled)
        assertFalse(confirmed.startService)
    }

    @Test
    fun `a one-output mode playing elsewhere is not taken back on reconnect`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onDisconnected(enabled = true)
        val elsewhere = PlayerState(activeOutputDeviceIds = listOf("room"))
        val yielded = role.onState(elsewhere, phone, enabled = true, now = 1_000, connectionEpoch = 2)
        assertFalse(yielded.enabled)
        assertFalse(yielded.rejoin)
    }

    @Test
    fun `a multi-output mode rejoins beside the other speakers`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onDisconnected(enabled = true)
        val together = PlayerState(activeOutputDeviceIds = listOf("room"), multipleOutputs = true)
        val rejoin = role.onState(together, phone, enabled = true, now = 1_000, connectionEpoch = 2)
        assertTrue(rejoin.enabled)
        assertTrue(rejoin.rejoin)
    }

    @Test
    fun `a conflated reconnect still counts as a first snapshot`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = true, now = 0, connectionEpoch = 1)
        // The disconnected null was conflated away under a busy collector;
        // only the bumped epoch reveals the new connection.
        val rejoin = role.onState(PlayerState(), phone, enabled = true, now = 60_000, connectionEpoch = 2)
        assertTrue(rejoin.enabled)
        assertTrue(rejoin.rejoin)
    }

    @Test
    fun `a racing broadcast does not steal the role while the rejoin is in flight`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onDisconnected(enabled = true)
        role.onState(PlayerState(), phone, enabled = true, now = 1_000, connectionEpoch = 2)
        val racing = role.onState(PlayerState(), phone, enabled = true, now = 3_000, connectionEpoch = 2)
        assertTrue(racing.enabled)
        assertFalse(racing.rejoin)
    }

    @Test
    fun `mid-connection removal relinquishes the role`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        val removed = role.onState(
            PlayerState(activeOutputDeviceIds = listOf("room")),
            phone,
            enabled = true,
            now = 60_000,
            connectionEpoch = 1,
        )
        assertFalse(removed.enabled)
        assertFalse(removed.rejoin)
    }

    @Test
    fun `removal shortly after a confirmed rejoin relinquishes the role`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onDisconnected(enabled = true)
        role.onState(PlayerState(), phone, enabled = true, now = 1_000, connectionEpoch = 2)
        // Confirmation closes the rejoin window...
        role.onState(active, phone, enabled = true, now = 2_000, connectionEpoch = 2)
        // ...so an operator removal seconds later is honored, not mistaken
        // for a broadcast racing the (already confirmed) rejoin.
        val removed = role.onState(PlayerState(), phone, enabled = true, now = 3_000, connectionEpoch = 2)
        assertFalse(removed.enabled)
    }

    @Test
    fun `an unconfirmed rejoin eventually relinquishes the role`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onDisconnected(enabled = true)
        role.onState(PlayerState(), phone, enabled = true, now = 1_000, connectionEpoch = 2)
        // Well past the confirmation window, the server still excludes us.
        val expired = role.onState(PlayerState(), phone, enabled = true, now = 30_000, connectionEpoch = 2)
        assertFalse(expired.enabled)
    }

    @Test
    fun `a stop is not undone by a broadcast that still lists this device`() {
        val role = SpeakerRoleReducer()
        role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1)
        role.onLeaveRequested(now = 1_000)
        val inFlight = role.onState(active, phone, enabled = false, now = 1_100, connectionEpoch = 1)
        assertFalse(inFlight.enabled)
        assertFalse(inFlight.startService)
        // A local re-enable cancels the leave window immediately.
        role.onJoinRequested()
        assertTrue(role.onState(active, phone, enabled = false, now = 1_200, connectionEpoch = 1).enabled)
    }

    @Test
    fun `service starts only on the off-to-on edge`() {
        val role = SpeakerRoleReducer()
        assertTrue(role.onState(active, phone, enabled = false, now = 0, connectionEpoch = 1).startService)
        assertFalse(role.onState(active, phone, enabled = true, now = 1_000, connectionEpoch = 1).startService)
    }
}
