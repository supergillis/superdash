package com.superdash.settings

sealed interface SettingsDestination {
    val title: String

    sealed class TopLevel(
        override val title: String,
        val summary: String,
    ) : SettingsDestination {
        data object HomeAssistant : TopLevel("Home Assistant", "Connection, authentication, and dashboard")

        data object Kiosk : TopLevel("Kiosk", "Device behavior and launch settings")

        data object Sidebar : TopLevel("Sidebar", "Quick access rail")

        data object Voice : TopLevel("Voice", "Wake word and voice assistant")

        data object Screensaver : TopLevel("Screensaver", "Idle display, overlays, and photos")

        data object Doorbell : TopLevel("Doorbell", "Camera overlay triggers")

        data object Esphome : TopLevel("ESPHome", "Home Assistant ESPHome protocol")

        data object Admin : TopLevel("Admin", "Diagnostics and maintenance")
    }

    sealed class Child(
        override val title: String,
        val parent: TopLevel,
    ) : SettingsDestination {
        data object VoiceSpeechPipeline : Child("Speech pipeline", TopLevel.Voice)

        data object VoiceLocalModels : Child("Local models", TopLevel.Voice)

        data object VoiceCommandRecording : Child("Command recording", TopLevel.Voice)

        data object VoiceAdvancedTuning : Child("Advanced tuning", TopLevel.Voice)

        data object ImmichPhotos : Child("Immich photos", TopLevel.Screensaver)
    }

    companion object {
        val topLevel =
            listOf(
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
