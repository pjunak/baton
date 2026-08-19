package eu.junak.baton.feature.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import eu.junak.baton.core.network.api.PresetEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetAudioProcessorTest {

    @Suppress("DEPRECATION")
    @Test
    fun `hot swaps effect and interrupt bypass without reconfiguring Media3`() {
        val processor = PresetAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(100, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.setEffects(
            listOf(PresetEffect(type = "tremolo", rate = 1.0, depth = 1.0)),
        )

        val affected = process(processor, shortArrayOf(16_384))
        assertEquals(8_192.0, affected.single().toDouble(), 1.0)

        processor.setBypassed(true)
        val dry = process(processor, shortArrayOf(16_384, -8_192))
        assertArrayEquals(shortArrayOf(16_384, -8_192), dry)

        processor.setBypassed(false)
        val affectedAgain = process(processor, shortArrayOf(16_384))
        assertEquals(8_192.0, affectedAgain.single().toDouble(), 1.0)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `recycled output buffer remains safe as the next dry input`() {
        val processor = PresetAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()

        val input = ByteBuffer.allocateDirect(2 * Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
        input.putShort(1_024)
        input.putShort(-2_048)
        input.flip()
        processor.queueInput(input)

        val recycled = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(1_024, recycled.short.toInt())
        assertEquals(-2_048, recycled.short.toInt())
        recycled.clear()
        recycled.putShort(4_096)
        recycled.putShort(-8_192)
        recycled.flip()

        processor.queueInput(recycled)

        val output = processor.output.order(ByteOrder.nativeOrder())
        val actual = ShortArray(output.remaining() / Short.SIZE_BYTES) { output.short }
        assertArrayEquals(shortArrayOf(4_096, -8_192), actual)
    }

    private fun process(processor: PresetAudioProcessor, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)

        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / Short.SIZE_BYTES) { output.short }
    }
}
