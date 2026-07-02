package com.superdash.voice.wake

import android.content.Context
import com.superdash.core.log.Log
import com.superdash.voice.features.AudioFeatureExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val MEL_BINS = 40
private const val FRAMES_PER_INFERENCE = 3
private const val INPUT_BYTES = FRAMES_PER_INFERENCE * MEL_BINS // int8 => 1 byte per value
private const val COOLDOWN_CHUNKS = 150 // 1500ms at 10ms/chunk

private val log = Log("MicroWakeWord")

data class WakeEvent(
    val word: String,
)

class MicroWakeWordRunner(
    private val context: Context,
    private val wakeWord: String,
    private val probeListener: ((Float) -> Unit)? = null,
) : WakeWordDetector {
    private val model = WakeWordModel.require(wakeWord)
    private var featureExtractor = AudioFeatureExtractor(sampleRateHz = 16000, stepSizeMs = 10)
    private val interpreter: Interpreter
    private val inputZeroPoint: Int
    private val inputScale: Float
    private val outputZeroPoint: Int
    private val outputScale: Float
    private val featureBuffer = ArrayDeque<FloatArray>()
    private val probHistory = ArrayDeque<Float>()
    private var cooldownChunks = 0

    // Guards interpreter and featureExtractor native handles. step() runs on a
    // single-threaded dispatcher while close() can be called from any thread;
    // without synchronization, close() can free the TF Lite interpreter while
    // an inference run is in flight (TF Lite Interpreter is not thread-safe).
    private val nativeLock = Any()

    @Volatile
    private var closed: Boolean = false

    init {
        val asset = context.assets.open(model.assetPath).use { it.readBytes() }
        val direct = ByteBuffer.allocateDirect(asset.size).order(ByteOrder.nativeOrder())
        direct.put(asset)
        direct.rewind()
        interpreter = Interpreter(direct)
        val inputT = interpreter.getInputTensor(0).quantizationParams()
        val outputT = interpreter.getOutputTensor(0).quantizationParams()
        inputZeroPoint = inputT.zeroPoint
        inputScale = inputT.scale
        outputZeroPoint = outputT.zeroPoint
        outputScale = outputT.scale
    }

    /** Returns a Flow of wake-word fires. The returned Flow drives streaming
     *  inference while collected: each input audio frame is fed through the mel
     *  feature extractor and the int8-quantized TF Lite classifier on a
     *  single-threaded dispatcher (TF Lite Interpreter is not thread-safe), and
     *  a [WakeEvent] is emitted whenever the sliding-window probability exceeds
     *  the configured threshold. */
    override fun detect(audio: Flow<ShortArray>): Flow<WakeEvent> =
        channelFlow {
            audio
                .onEach { samples ->
                    val maybeFire = step(samples)
                    if (maybeFire != null) {
                        trySend(maybeFire)
                    }
                }.flowOn(
                    Dispatchers.Default.limitedParallelism(1),
                ).collect { /* drives the onEach side-effect; values themselves discarded */ }
        }

    private fun step(samples: ShortArray): WakeEvent? =
        synchronized(nativeLock) {
            if (closed) {
                return@synchronized null
            }
            // Empty array is the AudioRecord-restart sentinel from AudioRecordSource.
            // Stateful buffers and the streaming TF Lite variable tensors must be
            // reset; carrying mel features or probability history across a mic
            // restart produces spurious wake fires or stuck cooldown.
            if (samples.isEmpty()) {
                interpreter.resetVariableTensors()
                featureBuffer.clear()
                probHistory.clear()
                cooldownChunks = 0
                featureExtractor.close()
                featureExtractor = AudioFeatureExtractor(sampleRateHz = 16000, stepSizeMs = 10)
                log.i("reset on audio restart sentinel", "word" to wakeWord)
                return@synchronized null
            }
            val newFrames = featureExtractor.extract(samples)
            for (frame in newFrames) {
                featureBuffer.addLast(frame)
            }
            var fired: WakeEvent? = null
            // Stride = FRAMES_PER_INFERENCE (3 frames). The model is a streaming
            // model with internal state; it expects 3 fresh feature frames
            // per inference, then clears. Overlapping windows (stride 1) corrupt
            // the streaming state.
            while (featureBuffer.size >= FRAMES_PER_INFERENCE) {
                val window = (0 until FRAMES_PER_INFERENCE).map { featureBuffer.removeFirst() }
                val prob = runInference(window)
                probeListener?.invoke(prob)
                if (cooldownChunks > 0) {
                    cooldownChunks--
                } else {
                    probHistory.addLast(prob)
                    while (probHistory.size > model.slidingWindowAverageSize) {
                        probHistory.removeFirst()
                    }
                    val state = WakeDecision.State(probHistory.toList(), cooldownChunksRemaining = 0)
                    if (
                        WakeDecision.classify(
                            state = state,
                            threshold = model.probabilityCutoff,
                            windowSize = model.slidingWindowAverageSize,
                        ) is WakeDecision.Outcome.Fire
                    ) {
                        log.i("wake word fired", "word" to wakeWord, "avg" to probHistory.average())
                        fired = WakeEvent(wakeWord)
                        interpreter.resetVariableTensors()
                        probHistory.clear()
                        cooldownChunks = COOLDOWN_CHUNKS
                    }
                }
            }
            fired
        }

    private fun runInference(window: List<FloatArray>): Float {
        val input = ByteBuffer.allocateDirect(INPUT_BYTES).order(ByteOrder.nativeOrder())
        for (frame in window) {
            for (mel in frame) {
                val quantized = (mel / inputScale + inputZeroPoint).toInt().coerceIn(-128, 127)
                input.put(quantized.toByte())
            }
        }
        input.rewind()
        val output = ByteBuffer.allocateDirect(1).order(ByteOrder.nativeOrder())
        interpreter.run(input, output)
        output.rewind()
        // The model output tensor is uint8 (verified via TFLite inspection on
        // all 5 bundled mWW models). Java/Kotlin Byte is signed, so we mask
        // with 0xFF to read it as unsigned. Without this, raw values 128..255
        // wrap to -128..-1 and the dequantized probability is always
        // negative/wrong (was producing a constant 0.0117 for any input).
        val raw = output.get().toInt() and 0xFF
        val prob = (raw - outputZeroPoint) * outputScale
        return prob.coerceIn(0f, 1f)
    }

    override fun close() {
        synchronized(nativeLock) {
            if (closed) {
                return
            }
            closed = true
            interpreter.close()
            featureExtractor.close()
        }
    }
}
