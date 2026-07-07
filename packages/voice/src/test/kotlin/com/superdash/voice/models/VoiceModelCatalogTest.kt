package com.superdash.voice.models

import ai.moonshine.voice.JNI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelCatalogTest {
    @Test
    fun `default selected STT model is bundled Moonshine tiny English`() {
        val model = VoiceModelCatalog.requireModel(VoiceModelIds.DEFAULT_STT_MODEL_ID)

        assertEquals(VoiceModelKind.Stt, model.kind)
        assertEquals("Moonshine tiny English", model.label)
        assertEquals(listOf(VoiceModelLanguage("en", "English")), model.languages)
        assertTrue(model.source is VoiceModelSource.BundledAsset)
        assertEquals(JNI.MOONSHINE_MODEL_ARCH_TINY, model.moonshineModelArch)
    }

    @Test
    fun `bundled Moonshine model declares exact expected files`() {
        val model = VoiceModelCatalog.requireModel(VoiceModelIds.MOONSHINE_TINY_EN)

        assertEquals(
            listOf("encoder_model.ort", "decoder_model_merged.ort", "tokenizer.bin"),
            model.files.map { file -> file.relativePath },
        )
        assertEquals(13281600L, model.files.first { file -> file.relativePath == "encoder_model.ort" }.bytes)
        assertEquals(
            "94e90a4654fc45cdfedb77c4c08e1739f48862998e58fada384b25118134f221",
            model.files.first { file -> file.relativePath == "encoder_model.ort" }.sha256,
        )
    }

    @Test
    fun `intent embedding default is selectable and has no files`() {
        val model = VoiceModelCatalog.requireModel(VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID)

        assertEquals(VoiceModelKind.IntentEmbedding, model.kind)
        assertTrue(model.isSelectable)
        assertTrue(model.files.isEmpty())
        assertFalse(model.canDownload)
    }

    @Test
    fun `Moonshine base English is downloadable with verified file metadata`() {
        val model = VoiceModelCatalog.requireModel(VoiceModelIds.MOONSHINE_BASE_EN)

        assertEquals(VoiceModelKind.Stt, model.kind)
        assertEquals("Moonshine base English", model.label)
        assertTrue(model.canDownload)
        assertEquals(JNI.MOONSHINE_MODEL_ARCH_BASE, model.moonshineModelArch)
        assertEquals(
            "https://download.moonshine.ai/model/base-en/quantized/base-en",
            (model.source as VoiceModelSource.Remote).baseUrl,
        )
        assertEquals(
            141001190L,
            model.files.sumOf { file -> file.bytes },
        )
        assertEquals(
            "7c66495948d0d08ec1af454cd4b5514862ae6511e94712a60e6d83eaec8dc8cf",
            model.files.first { file -> file.relativePath == "encoder_model.ort" }.sha256,
        )
    }
}
