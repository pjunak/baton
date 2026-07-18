package eu.junak.baton.core.sync

import eu.junak.baton.core.model.Action
import eu.junak.baton.core.model.DeviceInfo
import eu.junak.baton.core.model.PlayerState
import org.junit.Assert.assertEquals
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

    @Test
    fun `legacy server master is raised while other device levels are preserved`() {
        val actions = deviceVolumeActions(
            state = PlayerState(
                volume = 0.2,
                deviceVolumes = mapOf("phone" to 0.5, "tv" to 1.0),
                connectedDevices = listOf(
                    DeviceInfo("phone", "phone", "Phone"),
                    DeviceInfo("tv", "tv", "TV"),
                ),
            ),
            deviceId = "phone",
            requestedVolume = 0.8,
        )

        assertEquals(Action.SetVolume(0.8), actions[0])
        assertEquals(Action.SetDeviceVolume("phone", 1.0), actions[1])
        assertEquals(Action.SetDeviceVolume("tv", 0.25), actions[2])
    }

    @Test
    fun `legacy muted server can be unmuted`() {
        val actions = deviceVolumeActions(
            state = PlayerState(volume = 0.0),
            deviceId = "phone",
            requestedVolume = 0.6,
        )

        assertEquals(
            listOf(Action.SetVolume(0.6), Action.SetDeviceVolume("phone", 1.0)),
            actions,
        )
    }
}
