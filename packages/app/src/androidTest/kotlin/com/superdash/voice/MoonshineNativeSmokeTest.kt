package com.superdash.voice

import ai.moonshine.voice.JNI
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoonshineNativeSmokeTest {
    @Test
    fun loadsNativeRuntime() {
        JNI.ensureLibraryLoaded()

        assertTrue(JNI.moonshineGetVersion() > 0)
    }
}
