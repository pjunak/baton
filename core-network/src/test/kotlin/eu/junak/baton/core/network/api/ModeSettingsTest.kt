package eu.junak.baton.core.network.api

import eu.junak.baton.core.model.ProtocolJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeSettingsTest {

    @Test
    fun `mode update sends only the speaker policy`() {
        assertEquals(
            "{\"multiple_outputs\":true}",
            ProtocolJson.encodeToString(ModeUpdate(multipleOutputs = true)),
        )
    }

    @Test
    fun `mode summary reads the speaker policy and tolerates older servers`() {
        val current = ProtocolJson.decodeFromString<ModeSummary>(
            """{"id":"table","name":"Table","default_soundboard":null,"multiple_outputs":true}""",
        )
        assertTrue(current.multipleOutputs)
        val older = ProtocolJson.decodeFromString<ModeSummary>("""{"id":"table","name":"Table"}""")
        assertFalse(older.multipleOutputs)
    }
}
