package com.superdash.settings

import androidx.annotation.StringRes
import com.superdash.R

sealed interface SettingsDestination {
    @get:StringRes
    val titleRes: Int

    sealed class TopLevel(
        @StringRes override val titleRes: Int,
        @StringRes val summaryRes: Int,
    ) : SettingsDestination {
        data object General : TopLevel(R.string.settings_general_title, R.string.settings_general_summary)

        data object HomeAssistant : TopLevel(
            R.string.settings_home_assistant_title,
            R.string.settings_home_assistant_summary,
        )

        data object Kiosk : TopLevel(R.string.settings_kiosk_title, R.string.settings_kiosk_summary)

        data object Sidebar : TopLevel(R.string.settings_sidebar_title, R.string.settings_sidebar_summary)

        data object Voice : TopLevel(R.string.settings_voice_title, R.string.settings_voice_summary)

        data object Screensaver : TopLevel(R.string.settings_screensaver_title, R.string.settings_screensaver_summary)

        data object Doorbell : TopLevel(R.string.settings_doorbell_title, R.string.settings_doorbell_summary)

        data object Esphome : TopLevel(R.string.settings_esphome_title, R.string.settings_esphome_summary)

        data object Admin : TopLevel(R.string.settings_admin_title, R.string.settings_admin_summary)
    }

    sealed class Child(
        @StringRes override val titleRes: Int,
        val parent: TopLevel,
    ) : SettingsDestination {
        data object VoiceSpeechPipeline : Child(R.string.settings_voice_speech_pipeline_title, TopLevel.Voice)

        data object VoiceLocalModels : Child(R.string.settings_voice_local_models_title, TopLevel.Voice)

        data object VoiceCommandRecording : Child(R.string.settings_voice_command_recording_title, TopLevel.Voice)

        data object VoiceAdvancedTuning : Child(R.string.settings_voice_advanced_tuning_title, TopLevel.Voice)

        data object ImmichPhotos : Child(R.string.settings_immich_photos_title, TopLevel.Screensaver)
    }

    companion object {
        val topLevel =
            listOf(
                TopLevel.General,
                TopLevel.HomeAssistant,
                TopLevel.Kiosk,
                TopLevel.Sidebar,
                TopLevel.Voice,
                TopLevel.Screensaver,
                TopLevel.Doorbell,
                TopLevel.Esphome,
                TopLevel.Admin,
            )

        val voiceChildren =
            listOf(
                Child.VoiceSpeechPipeline,
                Child.VoiceLocalModels,
                Child.VoiceCommandRecording,
                Child.VoiceAdvancedTuning,
            )

        val ImmichPhotos = Child.ImmichPhotos
    }
}
