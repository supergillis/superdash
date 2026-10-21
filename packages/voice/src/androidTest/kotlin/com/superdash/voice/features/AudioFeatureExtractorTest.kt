package com.superdash.voice.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class AudioFeatureExtractorTest {
    @Test fun resetMakesExtractorMatchAFreshOne() {
        val tone = ShortArray(SAMPLE_RATE_HZ) { index -> (sin(index * TONE_STEP) * TONE_AMPLITUDE).toInt().toShort() }

        val expected = AudioFeatureExtractor(SAMPLE_RATE_HZ, STEP_SIZE_MS).use { it.extract(tone) }
        val actual =
            AudioFeatureExtractor(SAMPLE_RATE_HZ, STEP_SIZE_MS).use { extractor ->
                extractor.extract(tone)
                extractor.reset()
                extractor.extract(tone)
            }

        assertEquals(expected.map { it.toList() }, actual.map { it.toList() })
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val STEP_SIZE_MS = 10
        const val TONE_STEP = 0.2
        const val TONE_AMPLITUDE = 8000
    }
}
