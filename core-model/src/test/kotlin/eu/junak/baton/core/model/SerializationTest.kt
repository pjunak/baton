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
    fun `track dates and composer preserve precision and accept older servers`() {
        val legacy = """{"id":7,"path":"score.flac","title":"Score","artist":"Orchestra","album_artist":"Orchestra","album":"Film","year":2024,"added_at":"2026-09-13"}"""
        val old = ProtocolJson.decodeFromString<Track>(legacy)
        assertEquals("", old.releaseDate)
        assertEquals("", old.originalReleaseDate)
        assertEquals("", old.composer)
        val rich = legacy.dropLast(1) + """, "release_date":"2024-02-29","original_release_date":"1998-07","composer":"久石 譲; Composer B"}"""
        val decoded = ProtocolJson.decodeFromString<Track>(rich)
        assertEquals("2024-02-29", decoded.releaseDate)
        assertEquals("1998-07", decoded.originalReleaseDate)
        assertEquals("久石 譲; Composer B", decoded.composer)
        assertEquals(2024, decoded.year)
        assertEquals(decoded, ProtocolJson.decodeFromString<Track>(ProtocolJson.encodeToString(decoded)))
    }


    @Test
    fun `action carries type discriminator and snake_case fields`() {
        val json = ProtocolJson.encodeToString<Action>(Action.AmbientPlayTrack(trackId = 42))
        assertTrue(json, json.contains("\"type\":\"ambient_play_track\""))
        assertTrue(json, json.contains("\"track_id\":42"))
    }

    @Test
    fun `queue jump serializes its exact zero based position`() {
        assertEquals(
            "{\"type\":\"ambient_jump_queue\",\"position\":2}",
            ProtocolJson.encodeToString<Action>(Action.AmbientJumpQueue(position = 2)),
        )
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
    fun `register advertises absolute volume protocol`() {
        val json = ProtocolJson.encodeToString<Action>(Action.Register("Phone", "phone-1"))
        assertTrue(json, json.contains("\"protocol_version\":2"))
    }

    @Test
    fun `preset action carries ordered ids`() {
        assertEquals(
            "{\"type\":\"set_active_presets\",\"preset_ids\":[\"cave\",\"radio\"]}",
            ProtocolJson.encodeToString<Action>(
                Action.SetActivePresets(listOf("cave", "radio")),
            ),
        )
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
              "revision":5,"is_playing":true,"volume":1.0,
              "default_device_volume":0.7,"device_volumes":{"tv-1":0.4},
              "active_preset_ids":["cave"],"preset_revision":9,
              "connected_devices":[{"device_id":"tv-1","client_id":"tv-1","name":"TV","is_output":true}],
              "ambient":{"current_track_id":7,"queue":[7,8,9],"position_ms":1234,"loop":"follow"},
              "interrupt":null
            }}
        """.trimIndent()
        val msg = ProtocolJson.decodeFromString<ServerMessage>(payload)
        assertTrue(msg is ServerMessage.StateChanged)
        val state = (msg as ServerMessage.StateChanged).state
        assertEquals(true, state.isPlaying)
        assertEquals(1.0, state.volume, 0.0)
        assertEquals(0.7, state.defaultDeviceVolume ?: -1.0, 0.0)
        assertEquals(0.4, state.deviceVolumes["tv-1"] ?: -1.0, 0.0)
        assertEquals(listOf("cave"), state.activePresetIds)
        assertEquals(9, state.presetRevision)
        assertEquals("TV", state.connectedDevices.single().name)
        assertEquals(7, state.ambient.currentTrackId)
        assertEquals(listOf(7, 8, 9), state.ambient.queue)
        assertEquals(1234, state.ambient.positionMs)
        assertEquals(LoopMode.FOLLOW, state.ambient.loop)
        assertNull(state.interrupt)
    }

    @Test
    fun `missing absolute-volume marker identifies legacy server state`() {
        val payload =
            """{"type":"state_snapshot","state":{"volume":0.4,"device_volumes":{"tv-1":0.5}}}"""
        val msg = ProtocolJson.decodeFromString<ServerMessage>(payload)
        assertTrue(msg is ServerMessage.StateSnapshot)
        assertNull((msg as ServerMessage.StateSnapshot).state.defaultDeviceVolume)
        assertEquals(0, msg.state.presetRevision)
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
