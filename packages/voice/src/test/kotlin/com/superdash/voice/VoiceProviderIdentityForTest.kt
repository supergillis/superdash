package com.superdash.voice

import com.superdash.voice.models.VoiceModelInstallStatus
import com.superdash.voice.models.VoiceModelKind
import com.superdash.voice.models.VoiceModelLanguage
import com.superdash.voice.models.VoiceModelRow
import com.superdash.voice.models.VoiceModelState
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceSttProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceProviderIdentityForTest {
    @Test fun `moonshine identity uses selected model when available`() {
        val identity =
            voiceProviderIdentityFor(
                providerKey = VoiceSttProvider.Moonshine.key,
                selectedModelId = "moonshine-base-en",
                modelState =
                    VoiceModelState(
                        models = listOf(modelRow(id = "moonshine-base-en", status = VoiceModelInstallStatus.Available)),
                    ),
            )

        assertEquals(VoiceProviderIdentity("moonshine", "moonshine-base-en"), identity)
    }

    @Test fun `moonshine identity uses default model when selected model is not available`() {
        val identity =
            voiceProviderIdentityFor(
                providerKey = VoiceSttProvider.Moonshine.key,
                selectedModelId = "moonshine-base-en",
                modelState =
                    VoiceModelState(
                        models = listOf(modelRow(id = "moonshine-base-en", status = VoiceModelInstallStatus.NotInstalled)),
                    ),
            )

        assertEquals(VoiceProviderIdentity("moonshine", "moonshine-tiny-en"), identity)
    }

    @Test fun `non moonshine identity ignores selected model`() {
        val identity =
            voiceProviderIdentityFor(
                providerKey = VoiceSttProvider.HaAssist.key,
                selectedModelId = "moonshine-base-en",
                modelState =
                    VoiceModelState(
                        models = listOf(modelRow(id = "moonshine-base-en", status = VoiceModelInstallStatus.Available)),
                    ),
            )

        assertEquals(VoiceProviderIdentity("ha_assist", null), identity)
    }

    private fun modelRow(
        id: String,
        status: VoiceModelInstallStatus,
    ): VoiceModelRow =
        VoiceModelRow(
            id = id,
            kind = VoiceModelKind.Stt,
            label = id,
            summary = "",
            languages = listOf(VoiceModelLanguage("en", "English")),
            status = status,
            selected = false,
            canDownload = false,
            canDelete = false,
        )
}
