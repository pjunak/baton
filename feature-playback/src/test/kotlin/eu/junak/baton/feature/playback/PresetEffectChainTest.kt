package eu.junak.baton.feature.playback

import eu.junak.baton.core.network.api.EqBand
import eu.junak.baton.core.network.api.PresetEffect
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetEffectChainTest {

    @Test
    fun `graphic EQ matches the requested centre-band gain`() {
        val flat = PresetEffectChain(SAMPLE_RATE, 1, emptyList())
        val bands = EQ_FREQUENCIES.mapIndexed { index, frequency ->
            EqBand(frequency, if (index == 5) 6.0 else 0.0)
        }
        val boosted = PresetEffectChain(
            SAMPLE_RATE,
            1,
            listOf(PresetEffect(type = "eq", bands = bands)),
        )

        val flatRms = sineRms(flat, 1_000.0)
        val boostedRms = sineRms(boosted, 1_000.0)
        val gainDb = 20 * log10(boostedRms / flatRms)

        assertEquals(6.0, gainDb, 0.25)
    }

    @Test
    fun `low pass attenuates highs more than lows`() {
        val lowChain = chain(PresetEffect(type = "lowpass", frequency = 800.0, q = 0.7))
        val highChain = chain(PresetEffect(type = "lowpass", frequency = 800.0, q = 0.7))

        val lowRms = sineRms(lowChain, 100.0)
        val highRms = sineRms(highChain, 8_000.0)

        assertTrue(lowRms > highRms * 20)
    }

    @Test
    fun `high pass attenuates lows more than highs`() {
        val lowChain = chain(PresetEffect(type = "highpass", frequency = 800.0, q = 0.7))
        val highChain = chain(PresetEffect(type = "highpass", frequency = 800.0, q = 0.7))

        val lowRms = sineRms(lowChain, 100.0)
        val highRms = sineRms(highChain, 8_000.0)

        assertTrue(highRms > lowRms * 20)
    }

    @Test
    fun `band pass retains its centre more than distant frequencies`() {
        val lowChain = chain(PresetEffect(type = "bandpass", frequency = 1_000.0, q = 2.0))
        val centreChain = chain(PresetEffect(type = "bandpass", frequency = 1_000.0, q = 2.0))
        val highChain = chain(PresetEffect(type = "bandpass", frequency = 1_000.0, q = 2.0))

        val lowRms = sineRms(lowChain, 100.0)
        val centreRms = sineRms(centreChain, 1_000.0)
        val highRms = sineRms(highChain, 8_000.0)

        assertTrue(centreRms > lowRms * 5)
        assertTrue(centreRms > highRms * 5)
    }

    @Test
    fun `delay emits dry signal and a correctly timed wet echo`() {
        val chain = PresetEffectChain(
            sampleRate = 1_000,
            channelCount = 1,
            effects = listOf(
                PresetEffect(type = "delay", time = 0.01, feedback = 0.0, wet = 0.5),
            ),
        )
        val output = FloatArray(12)
        repeat(output.size) { frame ->
            val sample = floatArrayOf(if (frame == 0) 1f else 0f)
            chain.process(sample)
            output[frame] = sample[0]
        }

        assertEquals(1.0f, output[0], 0.0f)
        assertEquals(0.0f, output[9], 0.0f)
        assertEquals(0.5f, output[10], 0.0001f)
    }

    @Test
    fun `tremolo follows the web engine gain envelope`() {
        val chain = PresetEffectChain(
            sampleRate = 100,
            channelCount = 1,
            effects = listOf(PresetEffect(type = "tremolo", rate = 1.0, depth = 1.0)),
        )
        val output = FloatArray(76)
        repeat(output.size) { frame ->
            val sample = floatArrayOf(1f)
            chain.process(sample)
            output[frame] = sample[0]
        }

        assertEquals(0.5f, output[0], 0.0001f)
        assertEquals(1.0f, output[25], 0.0001f)
        assertEquals(0.0f, output[75], 0.0001f)
    }

    @Test
    fun `distortion uses an odd bounded waveshaper`() {
        val chain = PresetEffectChain(
            SAMPLE_RATE,
            2,
            listOf(PresetEffect(type = "distortion", amount = 50.0)),
        )
        val samples = floatArrayOf(-0.5f, 0.5f)

        chain.process(samples)

        assertTrue(samples.all { it.isFinite() && it in -1f..1f })
        assertEquals(-samples[0], samples[1], 0.0001f)
        assertTrue(samples[1] != 0.5f)
    }

    @Test
    fun `reverb produces a finite decaying tail after the dry impulse`() {
        val chain = chain(PresetEffect(type = "reverb", decay = 0.5, wet = 0.8))
        var tailEnergy = 0.0
        repeat(6_000) { frame ->
            val sample = floatArrayOf(if (frame == 0) 1f else 0f)
            chain.process(sample)
            assertTrue(sample[0].isFinite())
            if (frame > 1_000) tailEnergy += sample[0] * sample[0]
        }

        assertTrue(tailEnergy > 0.001)
    }

    @Test
    fun `filter state is isolated between stereo channels`() {
        val chain = PresetEffectChain(
            SAMPLE_RATE,
            2,
            listOf(PresetEffect(type = "highpass", frequency = 200.0, q = 0.7)),
        )
        repeat(128) { frame ->
            val samples = floatArrayOf(if (frame == 0) 1f else 0f, 0f)
            chain.process(samples)
            assertEquals(0.0f, samples[1], 0.0f)
        }
    }

    private fun chain(effect: PresetEffect): PresetEffectChain =
        PresetEffectChain(SAMPLE_RATE, 1, listOf(effect))

    private fun sineRms(chain: PresetEffectChain, frequency: Double): Double {
        var sumSquares = 0.0
        var measured = 0
        repeat(SAMPLE_RATE) { frame ->
            val sample = floatArrayOf(sin(2 * PI * frequency * frame / SAMPLE_RATE).toFloat())
            chain.process(sample)
            if (frame >= SETTLE_FRAMES) {
                sumSquares += sample[0] * sample[0]
                measured += 1
            }
        }
        return sqrt(sumSquares / measured)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val SETTLE_FRAMES = 4_800
        val EQ_FREQUENCIES = listOf(
            32.0,
            64.0,
            125.0,
            250.0,
            500.0,
            1_000.0,
            2_000.0,
            4_000.0,
            8_000.0,
            16_000.0,
        )
    }
}
