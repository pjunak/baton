package eu.junak.baton.feature.playback

import eu.junak.baton.core.model.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioYieldTest {
    private val singleOutput = PlayerState(isPlaying = true, multipleOutputs = false)
    private val multipleOutputs = PlayerState(isPlaying = true, multipleOutputs = true)

    @Test
    fun `one-output mode pauses the server when the audio is taken`() {
        val yield = AudioYield().apply { onState(true) }
        assertTrue(yield.onAudioLost(singleOutput))
    }

    @Test
    fun `multi-output mode keeps the room playing`() {
        val yield = AudioYield().apply { onState(true) }
        assertFalse(yield.onAudioLost(multipleOutputs))
    }

    @Test
    fun `an already paused server needs no pause`() {
        val yield = AudioYield()
        assertFalse(yield.onAudioLost(singleOutput.copy(isPlaying = false)))
        assertFalse(yield.onAudioLost(null))
    }

    @Test
    fun `the next track does not take the audio back`() {
        val yield = AudioYield().apply { onState(true) }
        yield.onAudioLost(multipleOutputs)
        // The server keeps playing and advances; each broadcast is still "playing".
        assertFalse(yield.onState(true))
        assertFalse(yield.onState(true))
    }

    @Test
    fun `a fresh server play reclaims the audio`() {
        val yield = AudioYield().apply { onState(true) }
        yield.onAudioLost(singleOutput)
        assertFalse(yield.onState(false)) // our pause lands
        assertTrue(yield.onState(true)) // someone pressed play
        assertFalse(yield.yielded)
    }

    @Test
    fun `a local play reclaims the audio while the room plays on`() {
        val yield = AudioYield().apply { onState(true) }
        yield.onAudioLost(multipleOutputs)
        yield.onLocalPlay()
        assertTrue(yield.onState(true))
    }

    @Test
    fun `a new speaker role starts unyielded`() {
        val yield = AudioYield().apply { onState(true) }
        yield.onAudioLost(multipleOutputs)
        yield.reset()
        assertTrue(yield.onState(true))
    }
}
