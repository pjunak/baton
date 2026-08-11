package eu.junak.baton.core.network.api

import eu.junak.baton.core.model.ProtocolJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresetManifestTest {

    @Test
    fun `full preset manifest decodes effect-specific parameters`() {
        val payload = """
            [{
              "id":"cave","name":"Cave","description":"Deep room",
              "effects":[
                {"type":"eq","bands":[{"frequency":32,"gain":-2.5}]},
                {"type":"lowpass","frequency":800,"q":0.7},
                {"type":"delay","time":0.25,"feedback":0.3,"wet":0.4},
                {"type":"distortion","amount":40},
                {"type":"tremolo","rate":5,"depth":0.5},
                {"type":"reverb","decay":2,"wet":0.4}
              ],
              "crossfade_ms":3000
            }]
        """.trimIndent()

        val preset = ProtocolJson.decodeFromString<List<PresetManifest>>(payload).single()

        assertEquals("cave", preset.id)
        assertEquals(6, preset.effects.size)
        assertEquals(-2.5, preset.effects[0].bands?.single()?.gain ?: 0.0, 0.0)
        assertEquals(800.0, preset.effects[1].frequency ?: 0.0, 0.0)
        assertEquals(0.3, preset.effects[2].feedback ?: 0.0, 0.0)
        assertEquals(40.0, preset.effects[3].amount ?: 0.0, 0.0)
        assertEquals(5.0, preset.effects[4].rate ?: 0.0, 0.0)
        assertEquals(2.0, preset.effects[5].decay ?: 0.0, 0.0)
        assertEquals(3000, preset.crossfadeMs)
    }

    @Test
    fun `minimal preset defaults to a dry rack`() {
        val preset = ProtocolJson.decodeFromString<PresetManifest>(
            """{"id":"dry","name":"Dry"}""",
        )

        assertEquals(emptyList<PresetEffect>(), preset.effects)
        assertNull(preset.crossfadeMs)
    }
}
