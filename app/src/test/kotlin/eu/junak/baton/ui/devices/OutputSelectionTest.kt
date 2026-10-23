package eu.junak.baton.ui.devices

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputSelectionTest {

    @Test
    fun `single-output selection replaces the current output`() {
        assertEquals(
            listOf("tv"),
            nextActiveOutputs(
                current = listOf("phone"),
                deviceId = "tv",
                on = true,
                allowMultiple = false,
            ),
        )
    }

    @Test
    fun `multi-output selection adds another output`() {
        assertEquals(
            listOf("phone", "tv"),
            nextActiveOutputs(
                current = listOf("phone"),
                deviceId = "tv",
                on = true,
                allowMultiple = true,
            ),
        )
    }

    @Test
    fun `turning an output off preserves the remaining outputs`() {
        assertEquals(
            listOf("tv"),
            nextActiveOutputs(
                current = listOf("phone", "tv"),
                deviceId = "phone",
                on = false,
                allowMultiple = true,
            ),
        )
    }
}
