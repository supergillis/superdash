package com.superdash.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.superdash.core.log.Log
import com.superdash.voice.wake.MicroWakeWordRunner
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val log = Log("WakeTest")

/** Debug-only: feed a WAV file through [MicroWakeWordRunner] without going through
 *  the real microphone. Trigger with:
 *
 *      adb shell am broadcast \
 *        -a com.superdash.DEBUG_WAKE_TEST \
 *        --es path /sdcard/heyjarvis.wav \
 *        --es word hey_jarvis \
 *        -p com.superdash
 *
 *  WAV must be 16kHz mono PCM-16. Logs sample count, max amplitude, and each
 *  fire (or "no fire" at end). */
class WakeWordTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path =
            intent.getStringExtra("path") ?: run {
                log.w("missing 'path' extra")
                return
            }
        val word = intent.getStringExtra("word") ?: WakeWordModel.DEFAULT_ID
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runTest(context, path, word)
            } catch (t: Throwable) {
                log.w("wake word test failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runTest(context: Context, path: String, word: String) {
        val file = File(path)
        if (!file.exists()) {
            log.w("file not found", null, "path" to path)
            return
        }
        val samples = readWav16kMonoPcm16(file) ?: return
        var maxAmp = 0
        for (sample in samples) {
            val abs = if (sample.toInt() < 0) -sample.toInt() else sample.toInt()
            if (abs > maxAmp) {
                maxAmp = abs
            }
        }
        log.i(
            "loaded WAV",
            "path" to path,
            "samples" to samples.size,
            "durationMs" to samples.size * 1000 / 16000,
            "peak" to maxAmp,
        )

        var maxProb = 0f
        var probCount = 0
        val app = context.applicationContext as com.superdash.SuperdashApp
        MicroWakeWordRunner(
            context = app,
            wakeWord = word,
            probeListener = { prob ->
                if (prob > maxProb) {
                    maxProb = prob
                }
                probCount++
            },
        ).use { runner ->
            val frames =
                flow {
                    var i = 0
                    while (i + 160 <= samples.size) {
                        emit(samples.copyOfRange(i, i + 160))
                        i += 160
                    }
                }
            val fires = runner.detect(frames).toList()
            if (fires.isEmpty()) {
                log.i("no wake fire", "frames" to samples.size / 160, "peakProb" to maxProb, "probCount" to probCount)
            } else {
                log.i("wake fired", "count" to fires.size, "peakProb" to maxProb)
            }
        }
    }

    /** Minimal WAV parser. Accepts 16-bit PCM mono @ 16kHz; rejects others. */
    private fun readWav16kMonoPcm16(file: File): ShortArray? {
        val bytes = file.readBytes()
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
            log.w("not a WAV", null, "path" to file.path)
            return null
        }
        var pos = 12
        var dataOffset = -1
        var dataSize = 0
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (id) {
                "fmt " -> {
                    channels =
                        ByteBuffer
                            .wrap(bytes, pos + 10, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                    sampleRate = ByteBuffer.wrap(bytes, pos + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample =
                        ByteBuffer
                            .wrap(bytes, pos + 22, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                }
                "data" -> {
                    dataOffset = pos + 8
                    dataSize = size
                }
            }
            pos += 8 + size
        }
        if (sampleRate != 16000 || channels != 1 || bitsPerSample != 16 || dataOffset < 0) {
            log.w(
                "WAV format not 16kHz/mono/PCM-16",
                null,
                "sampleRate" to sampleRate,
                "channels" to channels,
                "bitsPerSample" to bitsPerSample,
            )
            return null
        }
        val sampleCount = dataSize / 2
        val out = ShortArray(sampleCount)
        val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) out[i] = bb.short
        return out
    }
}
