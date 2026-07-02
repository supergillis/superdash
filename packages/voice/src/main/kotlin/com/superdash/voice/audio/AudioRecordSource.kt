package com.superdash.voice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.superdash.core.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val SAMPLE_RATE_HZ = 16000
private const val FALLBACK_SAMPLE_RATE_HZ = 44100
private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
private const val FRAME_DURATION_MS = 10
private const val RESTART_DELAY_MS = 1_000L
private val AUDIO_SOURCE_CANDIDATES =
    listOf(
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.DEFAULT,
    )
private val SAMPLE_RATE_CANDIDATES =
    listOf(
        SAMPLE_RATE_HZ,
        FALLBACK_SAMPLE_RATE_HZ,
    )

private val log = Log("AudioRecordSource")

/** Zero-length frame published between consecutive AudioRecord sessions so the
 *  wake-word runner can reset stateful buffers (mel feature window, probability
 *  history, streaming model variable tensors) across a mic restart. */
internal val RESTART_SENTINEL: ShortArray = ShortArray(0)

/** Captures 16kHz mono PCM-16 audio in 10ms (160-sample) frames from the device mic.
 *
 *  Self-restarting: if AudioRecord init fails or read() returns an error code (mic
 *  stolen by another app, audio HAL reset, mic hardware revoked at runtime) the
 *  inner record is released and a new one is constructed after a short delay. The
 *  flow stays alive across these events so the wake-word loop survives transient
 *  hardware reshuffles. This matters for an unattended kiosk. */
class AudioRecordSource {
    @SuppressLint("MissingPermission") // RECORD_AUDIO checked by caller (SettingsActivity launcher).
    fun frames(): Flow<ShortArray> = framesFromSessions(::openRecord)

    private fun framesFromSessions(openSession: () -> AudioRecord?): Flow<ShortArray> =
        recordingSessionsFlow(
            openSession = {
                openSession()?.let { record ->
                    AudioRecordSession(
                        sampleRate = record.sampleRate,
                        read = { buffer, offset, size -> record.read(buffer, offset, size) },
                        release = {
                            runCatching { record.stop() }
                            runCatching { record.release() }
                        },
                    )
                }
            },
        ).flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun openRecord(): AudioRecord? {
        for (audioSource in AUDIO_SOURCE_CANDIDATES) {
            for (sampleRate in SAMPLE_RATE_CANDIDATES) {
                val frameSamples = frameSamplesForRate(sampleRate)
                val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
                val bufferSize = maxOf(minBuffer, frameSamples * 2 * 4)
                val record =
                    AudioRecord(
                        audioSource,
                        sampleRate,
                        CHANNEL,
                        ENCODING,
                        bufferSize,
                    )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    log.w(
                        "AudioRecord init failed",
                        null,
                        "source" to audioSourceName(audioSource),
                        "sampleRate" to sampleRate,
                        "state" to record.state,
                        "minBuffer" to minBuffer,
                        "bufferSize" to bufferSize,
                    )
                    runCatching { record.release() }
                    continue
                }
                val started =
                    runCatching {
                        record.startRecording()
                        record.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    }.getOrElse { throwable ->
                        log.w(
                            "AudioRecord start failed",
                            throwable,
                            "source" to audioSourceName(audioSource),
                            "sampleRate" to sampleRate,
                        )
                        false
                    }
                if (started) {
                    log.i(
                        "AudioRecord started",
                        "source" to audioSourceName(audioSource),
                        "sampleRate" to record.sampleRate,
                        "minBuffer" to minBuffer,
                        "bufferSize" to bufferSize,
                    )
                    return record
                }
                runCatching { record.release() }
            }
        }
        log.w("AudioRecord open failed for all sources; will retry")
        return null
    }

    private fun audioSourceName(audioSource: Int): String =
        when (audioSource) {
            MediaRecorder.AudioSource.MIC -> {
                "MIC"
            }
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> {
                "VOICE_RECOGNITION"
            }
            MediaRecorder.AudioSource.DEFAULT -> {
                "DEFAULT"
            }
            else -> {
                audioSource.toString()
            }
        }
}

/** Abstracts one AudioRecord lifetime. Used so the session loop is unit-testable
 *  without an Android runtime (AudioRecord requires native HAL access). */
internal class AudioRecordSession(
    val sampleRate: Int,
    val read: (ShortArray, Int, Int) -> Int,
    val release: () -> Unit,
)

/** Drives sequential AudioRecord sessions and emits a zero-length sentinel
 *  between them so wake-word state can reset on mic restart. Public for tests. */
internal fun recordingSessionsFlow(openSession: () -> AudioRecordSession?): Flow<ShortArray> =
    flow {
        var emittedAnyFrame = false
        while (true) {
            val session = openSession()
            if (session == null) {
                delay(RESTART_DELAY_MS)
                continue
            }
            if (emittedAnyFrame) {
                emit(RESTART_SENTINEL)
            }
            val frameSamples = frameSamplesForRate(session.sampleRate)
            val frame = ShortArray(frameSamples)
            try {
                readLoop@ while (true) {
                    var read = 0
                    while (read < frameSamples) {
                        val readResult = session.read(frame, read, frameSamples - read)
                        if (readResult <= 0) {
                            log.w("AudioRecord.read failed; restarting", null, "readResult" to readResult)
                            break@readLoop
                        }
                        read += readResult
                    }
                    if (read < frameSamples) {
                        break
                    }
                    val output =
                        if (session.sampleRate == SAMPLE_RATE_HZ) {
                            frame.copyOf()
                        } else {
                            downsampleLinear(frame.copyOf(), fromRate = session.sampleRate, toRate = SAMPLE_RATE_HZ)
                        }
                    emit(output)
                    emittedAnyFrame = true
                }
            } finally {
                session.release()
            }
            delay(RESTART_DELAY_MS)
        }
    }

internal fun frameSamplesForRate(sampleRate: Int): Int = sampleRate * FRAME_DURATION_MS / 1000

internal fun downsampleLinear(
    input: ShortArray,
    fromRate: Int,
    toRate: Int,
): ShortArray {
    if (fromRate == toRate) {
        return input.copyOf()
    }
    require(fromRate > toRate) { "downsampling requires fromRate > toRate" }
    val ratio = fromRate.toDouble() / toRate
    val output = ShortArray((input.size / ratio).toInt())
    for (i in output.indices) {
        val position = i * ratio
        val sourceIndex = position.toInt()
        val fraction = position - sourceIndex
        output[i] =
            if (sourceIndex + 1 < input.size) {
                (input[sourceIndex] * (1.0 - fraction) + input[sourceIndex + 1] * fraction).toInt().toShort()
            } else {
                input[sourceIndex]
            }
    }
    return output
}
