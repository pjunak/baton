package eu.junak.baton.feature.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import eu.junak.baton.core.network.api.PresetEffect
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Media3 PCM stage that applies the active mode's ordered preset rack. The
 * processor remains installed as a passthrough when the rack is empty so a
 * state push can enable or bypass effects without rebuilding ExoPlayer.
 */
@OptIn(UnstableApi::class)
internal class PresetAudioProcessor : BaseAudioProcessor() {
    private data class RequestedConfig(
        val effects: List<PresetEffect> = emptyList(),
        val bypassed: Boolean = false,
        val version: Long = 0,
    )

    private val requested = AtomicReference(RequestedConfig())
    private var appliedVersion = -1L
    private var effectChain: PresetEffectChain? = null
    private var frameBuffer = FloatArray(0)

    fun setEffects(effects: List<PresetEffect>) {
        val snapshot = effects.toList()
        requested.updateAndGet { current ->
            if (current.effects == snapshot) current else current.copy(
                effects = snapshot,
                version = current.version + 1,
            )
        }
    }

    /** Interrupts use a dry lane in the web engine, so the shared native lane
     *  bypasses its ambient rack while an interrupt owns ExoPlayer. */
    fun setBypassed(bypassed: Boolean) {
        requested.updateAndGet { current ->
            if (current.bypassed == bypassed) current else current.copy(
                bypassed = bypassed,
                version = current.version + 1,
            )
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        applyRequestedConfig()
        val outputBuffer = replaceOutputBuffer(inputBuffer.remaining())
        val chain = effectChain
        if (chain == null) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val channelCount = inputAudioFormat.channelCount
        val frame = frameBuffer
        val frameSize = channelCount * PCM_16_BYTES
        while (inputBuffer.remaining() >= frameSize) {
            for (channel in 0 until channelCount) {
                frame[channel] = inputBuffer.short / PCM_16_SCALE
            }
            chain.process(frame)
            for (channel in 0 until channelCount) {
                outputBuffer.putShort(toPcm16(frame[channel]))
            }
        }
        // Media3 supplies complete PCM frames. Preserve any unexpected tail
        // bytes instead of consuming or padding them.
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        appliedVersion = -1L
        effectChain = null
        frameBuffer = FloatArray(inputAudioFormat.channelCount)
    }

    override fun onReset() {
        appliedVersion = -1L
        effectChain = null
        frameBuffer = FloatArray(0)
    }

    private fun applyRequestedConfig() {
        val desired = requested.get()
        if (desired.version == appliedVersion) return
        effectChain = if (desired.bypassed || desired.effects.isEmpty()) {
            null
        } else {
            PresetEffectChain(
                sampleRate = inputAudioFormat.sampleRate,
                channelCount = inputAudioFormat.channelCount,
                effects = desired.effects,
            )
        }
        appliedVersion = desired.version
    }

    private fun toPcm16(sample: Float): Short {
        if (!sample.isFinite()) return 0
        val scaled = when {
            sample >= 1f -> Short.MAX_VALUE.toInt()
            sample <= -1f -> Short.MIN_VALUE.toInt()
            else -> (sample * PCM_16_SCALE).roundToInt()
        }
        return scaled.toShort()
    }

    private companion object {
        const val PCM_16_BYTES = 2
        const val PCM_16_SCALE = 32768f
    }
}

/** Stateful, float-domain DSP rack shared by the Media3 adapter and JVM tests. */
internal class PresetEffectChain(
    sampleRate: Int,
    channelCount: Int,
    effects: List<PresetEffect>,
) {
    private val nodes: List<FrameEffect> = effects.mapNotNull { effect ->
        buildEffect(effect, sampleRate, channelCount)
    }

    fun process(frame: FloatArray) {
        for (node in nodes) node.process(frame)
    }
}

private interface FrameEffect {
    fun process(frame: FloatArray)
}

private fun buildEffect(
    effect: PresetEffect,
    sampleRate: Int,
    channelCount: Int,
): FrameEffect? = when (effect.type) {
    "eq" -> graphicEq(effect, sampleRate, channelCount)
    "lowpass" -> BiquadCascade(
        listOf(
            lowPass(
                frequency = finiteOr(effect.frequency, 800.0),
                q = finiteOr(effect.q, 0.7),
                sampleRate = sampleRate,
            ),
        ),
        channelCount,
    )
    "highpass" -> BiquadCascade(
        listOf(
            highPass(
                frequency = finiteOr(effect.frequency, 200.0),
                q = finiteOr(effect.q, 0.7),
                sampleRate = sampleRate,
            ),
        ),
        channelCount,
    )
    "bandpass" -> BiquadCascade(
        listOf(
            bandPass(
                frequency = finiteOr(effect.frequency, 1_000.0),
                q = finiteOr(effect.q, 0.7),
                sampleRate = sampleRate,
            ),
        ),
        channelCount,
    )
    "delay" -> DelayEffect(
        sampleRate = sampleRate,
        channelCount = channelCount,
        timeSeconds = finiteOr(effect.time, 0.25).coerceIn(0.0, 5.0),
        feedback = finiteOr(effect.feedback, 0.3).coerceIn(0.0, 1.0),
        wet = finiteOr(effect.wet, 0.4).coerceIn(0.0, 1.0),
    )
    "distortion" -> DistortionEffect(max(0.0, finiteOr(effect.amount, 50.0)))
    "tremolo" -> TremoloEffect(
        sampleRate = sampleRate,
        rate = max(0.01, finiteOr(effect.rate, 5.0)),
        depth = finiteOr(effect.depth, 0.5).coerceIn(0.0, 1.0),
    )
    "reverb" -> ReverbEffect(
        sampleRate = sampleRate,
        channelCount = channelCount,
        decaySeconds = max(0.05, finiteOr(effect.decay, 2.0)),
        wet = finiteOr(effect.wet, 0.4).coerceIn(0.0, 1.0),
    )
    else -> null
}

private fun graphicEq(effect: PresetEffect, sampleRate: Int, channelCount: Int): FrameEffect {
    val rawBands = effect.bands.orEmpty()
    val coefficients = EQ_FREQUENCIES.mapIndexed { index, frequency ->
        val gain = rawBands.getOrNull(index)?.gain
            ?.takeIf(Double::isFinite)
            ?.coerceIn(EQ_GAIN_MIN, EQ_GAIN_MAX)
            ?: 0.0
        peaking(frequency, EQ_BAND_Q, gain, sampleRate)
    }
    return BiquadCascade(coefficients, channelCount)
}

private data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
)

private class BiquadCascade(
    coefficients: List<BiquadCoefficients>,
    channelCount: Int,
) : FrameEffect {
    private val filters = coefficients.map { BiquadFilter(it, channelCount) }

    override fun process(frame: FloatArray) {
        for (channel in frame.indices) {
            var sample = frame[channel].toDouble()
            for (filter in filters) sample = filter.process(channel, sample)
            frame[channel] = sample.toFloat()
        }
    }
}

private class BiquadFilter(
    private val coefficients: BiquadCoefficients,
    channelCount: Int,
) {
    private val x1 = DoubleArray(channelCount)
    private val x2 = DoubleArray(channelCount)
    private val y1 = DoubleArray(channelCount)
    private val y2 = DoubleArray(channelCount)

    fun process(channel: Int, input: Double): Double {
        val output =
            coefficients.b0 * input +
                coefficients.b1 * x1[channel] +
                coefficients.b2 * x2[channel] -
                coefficients.a1 * y1[channel] -
                coefficients.a2 * y2[channel]
        x2[channel] = x1[channel]
        x1[channel] = input
        y2[channel] = y1[channel]
        y1[channel] = output
        return output
    }
}

private fun lowPass(frequency: Double, q: Double, sampleRate: Int): BiquadCoefficients {
    val common = filterCommon(frequency, q, sampleRate)
    val b0 = (1 - common.cosine) / 2
    val b1 = 1 - common.cosine
    return normalize(b0, b1, b0, common.a0, common.a1, common.a2)
}

private fun highPass(frequency: Double, q: Double, sampleRate: Int): BiquadCoefficients {
    val common = filterCommon(frequency, q, sampleRate)
    val b0 = (1 + common.cosine) / 2
    val b1 = -(1 + common.cosine)
    return normalize(b0, b1, b0, common.a0, common.a1, common.a2)
}

private fun bandPass(frequency: Double, q: Double, sampleRate: Int): BiquadCoefficients {
    val common = filterCommon(frequency, q, sampleRate)
    return normalize(
        common.alpha,
        0.0,
        -common.alpha,
        common.a0,
        common.a1,
        common.a2,
    )
}

private fun peaking(
    frequency: Double,
    q: Double,
    gainDb: Double,
    sampleRate: Int,
): BiquadCoefficients {
    val safeFrequency = safeFrequency(frequency, sampleRate)
    val safeQ = q.coerceIn(MIN_Q, MAX_Q)
    val omega = 2 * PI * safeFrequency / sampleRate
    val cosine = cos(omega)
    val alpha = sin(omega) / (2 * safeQ)
    val amplitude = 10.0.pow(gainDb / 40)
    return normalize(
        1 + alpha * amplitude,
        -2 * cosine,
        1 - alpha * amplitude,
        1 + alpha / amplitude,
        -2 * cosine,
        1 - alpha / amplitude,
    )
}

private data class FilterCommon(
    val cosine: Double,
    val alpha: Double,
    val a0: Double,
    val a1: Double,
    val a2: Double,
)

private fun filterCommon(frequency: Double, q: Double, sampleRate: Int): FilterCommon {
    val omega = 2 * PI * safeFrequency(frequency, sampleRate) / sampleRate
    val cosine = cos(omega)
    val alpha = sin(omega) / (2 * q.coerceIn(MIN_Q, MAX_Q))
    return FilterCommon(
        cosine = cosine,
        alpha = alpha,
        a0 = 1 + alpha,
        a1 = -2 * cosine,
        a2 = 1 - alpha,
    )
}

private fun normalize(
    b0: Double,
    b1: Double,
    b2: Double,
    a0: Double,
    a1: Double,
    a2: Double,
): BiquadCoefficients = BiquadCoefficients(
    b0 = b0 / a0,
    b1 = b1 / a0,
    b2 = b2 / a0,
    a1 = a1 / a0,
    a2 = a2 / a0,
)

private class DelayEffect(
    sampleRate: Int,
    channelCount: Int,
    timeSeconds: Double,
    private val feedback: Double,
    private val wet: Double,
) : FrameEffect {
    private val buffers = Array(channelCount) {
        FloatArray(max(1, (timeSeconds * sampleRate).roundToInt()))
    }
    private var index = 0

    override fun process(frame: FloatArray) {
        for (channel in frame.indices) {
            val input = frame[channel]
            val delayed = buffers[channel][index]
            buffers[channel][index] = (input + delayed * feedback).toFloat()
            frame[channel] = (input + delayed * wet).toFloat()
        }
        index = (index + 1) % buffers[0].size
    }
}

private class DistortionEffect(private val amount: Double) : FrameEffect {
    override fun process(frame: FloatArray) {
        for (channel in frame.indices) {
            val input = frame[channel].toDouble().coerceIn(-1.0, 1.0)
            frame[channel] = (
                ((3 + amount) * input * DISTORTION_SCALE) /
                    (PI + amount * abs(input))
                ).toFloat()
        }
    }
}

private class TremoloEffect(
    sampleRate: Int,
    rate: Double,
    private val depth: Double,
) : FrameEffect {
    private val phaseStep = 2 * PI * rate / sampleRate
    private var phase = 0.0

    override fun process(frame: FloatArray) {
        val gain = 1 - depth / 2 + sin(phase) * depth / 2
        for (channel in frame.indices) frame[channel] = (frame[channel] * gain).toFloat()
        phase += phaseStep
        if (phase >= 2 * PI) phase -= 2 * PI
    }
}

/**
 * Lightweight Schroeder reverb with an RT60-derived comb feedback. Web Audio
 * uses a generated convolution impulse, so exact samples are intentionally not
 * shared; the decay and wet controls retain the same audible semantics without
 * doing multi-second convolution on ExoPlayer's realtime thread.
 */
private class ReverbEffect(
    sampleRate: Int,
    channelCount: Int,
    decaySeconds: Double,
    private val wet: Double,
) : FrameEffect {
    private val channels = Array(channelCount) { channel ->
        ReverbChannel(sampleRate, decaySeconds, channel)
    }

    override fun process(frame: FloatArray) {
        for (channel in frame.indices) {
            val input = frame[channel]
            frame[channel] = (input + channels[channel].process(input) * wet).toFloat()
        }
    }
}

private class ReverbChannel(
    sampleRate: Int,
    decaySeconds: Double,
    channel: Int,
) {
    private val combs = COMB_TUNINGS.mapIndexed { index, tuning ->
        val stereoSpread = if (channel % 2 == 0) 0 else REVERB_STEREO_SPREAD
        val frames = scaleDelay(tuning + stereoSpread + index, sampleRate)
        val delaySeconds = frames.toDouble() / sampleRate
        CombFilter(frames, 10.0.pow(-3 * delaySeconds / decaySeconds))
    }
    private val allPasses = ALL_PASS_TUNINGS.map { tuning ->
        val stereoSpread = if (channel % 2 == 0) 0 else REVERB_STEREO_SPREAD
        AllPassFilter(scaleDelay(tuning + stereoSpread, sampleRate))
    }

    fun process(input: Float): Double {
        var output = combs.sumOf { it.process(input.toDouble()) } / combs.size
        for (filter in allPasses) output = filter.process(output)
        return output
    }
}

private class CombFilter(size: Int, private val feedback: Double) {
    private val buffer = DoubleArray(size)
    private var index = 0

    fun process(input: Double): Double {
        val output = buffer[index]
        buffer[index] = input + output * feedback
        index = (index + 1) % buffer.size
        return output
    }
}

private class AllPassFilter(size: Int) {
    private val buffer = DoubleArray(size)
    private var index = 0

    fun process(input: Double): Double {
        val buffered = buffer[index]
        val output = -input + buffered
        buffer[index] = input + buffered * ALL_PASS_FEEDBACK
        index = (index + 1) % buffer.size
        return output
    }
}

private fun scaleDelay(framesAt44k: Int, sampleRate: Int): Int =
    max(1, (framesAt44k * sampleRate / REVERB_REFERENCE_RATE).roundToInt())

private fun safeFrequency(frequency: Double, sampleRate: Int): Double =
    frequency.coerceIn(MIN_FREQUENCY, sampleRate * NYQUIST_MARGIN)

private fun finiteOr(value: Double?, fallback: Double): Double =
    value?.takeIf(Double::isFinite) ?: fallback

private val EQ_FREQUENCIES = doubleArrayOf(
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

private val COMB_TUNINGS = intArrayOf(1_116, 1_188, 1_277, 1_356)
private val ALL_PASS_TUNINGS = intArrayOf(556, 441, 341, 225)

private const val EQ_BAND_Q = 1.414
private const val EQ_GAIN_MIN = -12.0
private const val EQ_GAIN_MAX = 12.0
private const val MIN_Q = 0.0001
private const val MAX_Q = 1_000.0
private const val MIN_FREQUENCY = 10.0
private const val NYQUIST_MARGIN = 0.49
private const val DISTORTION_SCALE = 20 * PI / 180
private const val REVERB_REFERENCE_RATE = 44_100.0
private const val REVERB_STEREO_SPREAD = 23
private const val ALL_PASS_FEEDBACK = 0.5
