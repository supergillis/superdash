package com.superdash.voice.models

import ai.moonshine.voice.JNI

enum class VoiceModelKind {
    Stt,
    IntentEmbedding,
}

data class VoiceModelLanguage(
    val tag: String,
    val label: String,
)

sealed interface VoiceModelSource {
    data class BundledAsset(
        val assetDir: String,
    ) : VoiceModelSource

    data class Remote(
        val baseUrl: String,
    ) : VoiceModelSource

    data object BuiltInNone : VoiceModelSource
}

data class VoiceModelFile(
    val relativePath: String,
    val bytes: Long,
    val sha256: String,
    val remotePath: String = relativePath,
)

data class VoiceModelCatalogEntry(
    val id: String,
    val kind: VoiceModelKind,
    val label: String,
    val providerKey: String?,
    val languages: List<VoiceModelLanguage>,
    val files: List<VoiceModelFile>,
    val source: VoiceModelSource,
    val isSelectable: Boolean = true,
    val moonshineModelArch: Int? = null,
    val summary: String,
) {
    val canDownload: Boolean get() = source is VoiceModelSource.Remote
}

object VoiceModelIds {
    const val MOONSHINE_TINY_EN = "moonshine-tiny-en"
    const val MOONSHINE_BASE_EN = "moonshine-base-en"
    const val INTENT_EMBEDDING_NONE = "intent-embedding-none"
    const val DEFAULT_STT_MODEL_ID = MOONSHINE_TINY_EN
    const val DEFAULT_INTENT_EMBEDDING_MODEL_ID = INTENT_EMBEDDING_NONE
}

object VoiceModelCatalog {
    private val english = VoiceModelLanguage(tag = "en", label = "English")

    val models: List<VoiceModelCatalogEntry> =
        listOf(
            VoiceModelCatalogEntry(
                id = VoiceModelIds.MOONSHINE_TINY_EN,
                kind = VoiceModelKind.Stt,
                label = "Moonshine tiny English",
                providerKey = "moonshine",
                languages = listOf(english),
                source = VoiceModelSource.BundledAsset("models/moonshine/tiny-en"),
                moonshineModelArch = JNI.MOONSHINE_MODEL_ARCH_TINY,
                summary = "Bundled offline English STT fallback.",
                files =
                    listOf(
                        VoiceModelFile(
                            relativePath = "encoder_model.ort",
                            bytes = 13281600L,
                            sha256 = "94e90a4654fc45cdfedb77c4c08e1739f48862998e58fada384b25118134f221",
                        ),
                        VoiceModelFile(
                            relativePath = "decoder_model_merged.ort",
                            bytes = 30412256L,
                            sha256 = "cf524c4862d36e9e5ab032eddc73637efd822d70e868ac575cf1a46e1e4708a0",
                        ),
                        VoiceModelFile(
                            relativePath = "tokenizer.bin",
                            bytes = 249974L,
                            sha256 = "6884b35fd6377d4c4d32336a0bc152f36b64d1e45b6503683cdc238250a8472d",
                        ),
                    ),
            ),
            VoiceModelCatalogEntry(
                id = VoiceModelIds.MOONSHINE_BASE_EN,
                kind = VoiceModelKind.Stt,
                label = "Moonshine base English",
                providerKey = "moonshine",
                languages = listOf(english),
                source = VoiceModelSource.Remote("https://download.moonshine.ai/model/base-en/quantized/base-en"),
                moonshineModelArch = JNI.MOONSHINE_MODEL_ARCH_BASE,
                summary = "Downloadable English STT model with better accuracy than tiny.",
                files =
                    listOf(
                        VoiceModelFile(
                            relativePath = "encoder_model.ort",
                            bytes = 31326816L,
                            sha256 = "7c66495948d0d08ec1af454cd4b5514862ae6511e94712a60e6d83eaec8dc8cf",
                        ),
                        VoiceModelFile(
                            relativePath = "decoder_model_merged.ort",
                            bytes = 109424400L,
                            sha256 = "d9d7b333af34bc552580576ddcf248a1c6c839e0d3b43b09afb9376ed009899d",
                        ),
                        VoiceModelFile(
                            relativePath = "tokenizer.bin",
                            bytes = 249974L,
                            sha256 = "6884b35fd6377d4c4d32336a0bc152f36b64d1e45b6503683cdc238250a8472d",
                        ),
                    ),
            ),
            VoiceModelCatalogEntry(
                id = VoiceModelIds.INTENT_EMBEDDING_NONE,
                kind = VoiceModelKind.IntentEmbedding,
                label = "No local intent embedding",
                providerKey = null,
                languages = emptyList(),
                files = emptyList(),
                source = VoiceModelSource.BuiltInNone,
                summary = "Use Home Assistant Assist intent handling.",
            ),
        )

    fun requireModel(id: String): VoiceModelCatalogEntry =
        models.firstOrNull { model -> model.id == id }
            ?: error("Unknown voice model: $id")

    fun modelsForKind(kind: VoiceModelKind): List<VoiceModelCatalogEntry> =
        models.filter { model -> model.kind == kind }
}
