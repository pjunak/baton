package eu.junak.baton.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wire contract with the backend: the `type` discriminator, the
 * camelCase ↔ snake_case mapping, polymorphic decoding, and forward-compatible
 * tolerance of unknown keys. These are the parts most likely to silently drift
 * from `protocol.py`.
 */
class SerializationTest {

    @Test
    fun `action carries type discriminator and snake_case fields`() {
        val json = ProtocolJson.encodeToString<Action>(Action.AmbientPlayTrack(trackId = 42))
        assertTrue(json, json.contains("\"type\":\"ambient_play_track\""))
        assertTrue(json, json.contains("\"track_id\":42"))
    }

    @Test
    fun `object action serializes to just the discriminator`() {
        assertEquals("{\"type\":\"pause\"}", ProtocolJson.encodeToString<Action>(Action.Pause))
    }

    @Test
    fun `defaults are encoded so the server sees explicit values`() {
        val json = ProtocolJson.encodeToString<Action>(Action.FireSfx("tavern", "dnd/door.ogg"))
        assertTrue(json, json.contains("\"volume\":1.0"))
        assertTrue(json, json.contains("\"soundboard_id\":\"tavern\""))
        assertTrue(json, json.contains("\"item_path\":\"dnd/door.ogg\""))
    }

    @Test
    fun `nullable required field is written as null`() {
        // set_active_mode.mode_id is nullable-but-required: null means "clear".
        assertEquals(
            "{\"type\":\"set_active_mode\",\"mode_id\":null}",
            ProtocolJson.encodeToString<Action>(Action.SetActiveMode(modeId = null)),
        )
    }

    @Test
    fun `state_changed decodes into PlayerState`() {
        val payload = """
            {"type":"state_changed","state":{
              "is_playing":true,"volume":0.5,
              "ambient":{"current_track_id":7,"queue":[7,8,9],"position_ms":1234,"loop":"follow"},
              "interrupt":null
            }}
        """.trimIndent()
        val msg = ProtocolJson.decodeFromString<ServerMessage>(payload)
        assertTrue(msg is ServerMessage.StateChanged)
        val state = (msg as ServerMessage.StateChanged).state
        assertEquals(true, state.isPlaying)
        assertEquals(0.5, state.volume, 0.0)
        assertEquals(7, state.ambient.currentTrackId)
        assertEquals(listOf(7, 8, 9), state.ambient.queue)
        assertEquals(1234, state.ambient.positionMs)
        assertEquals(LoopMode.FOLLOW, state.ambient.loop)
        assertNull(state.interrupt)
    }

    @Test
    fun `unknown keys are ignored for forward compatibility`() {
        val payload = """{"type":"error","detail":"nope","future_field":123}"""
        val msg = ProtocolJson.decodeFromString<ServerMessage>(payload)
        assertTrue(msg is ServerMessage.Error)
        assertEquals("nope", (msg as ServerMessage.Error).detail)
    }

    @Test
    fun `sfx_fired decodes with snake_case fields`() {
        val payload =
            """{"type":"sfx_fired","soundboard_id":"tavern","item_path":"dnd/door.ogg","volume":0.8}"""
        val msg = ProtocolJson.decodeFromString<ServerMessage>(payload)
        assertTrue(msg is ServerMessage.SfxFired)
        msg as ServerMessage.SfxFired
        assertEquals("tavern", msg.soundboardId)
        assertEquals("dnd/door.ogg", msg.itemPath)
    }
}
