package com.superdash

import android.app.Application
import com.superdash.ha.HaAssistClient
import com.superdash.ha.HaServiceCallClient
import com.superdash.ha.HaTokenProvider
import com.superdash.ha.HaWebSocketClient
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.HaVoiceCollaborator
import com.superdash.voice.VoiceModule
import com.superdash.voice.VoiceSettings
import com.superdash.voice.models.VoiceModelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-side wiring that exposes [HaSubgraph] to the voice module through a
 * narrow [HaVoiceCollaborator] interface. All voice-internal logic lives
 * in `:packages/voice`; this class is just composition glue.
 */
class VoiceSubgraph(
    application: Application,
    voiceSettings: VoiceSettings,
    eventBus: KioskEventBus,
    haSubgraph: HaSubgraph,
    voiceModelState: Flow<VoiceModelState>,
    scope: CoroutineScope,
) {
    private val module: VoiceModule =
        VoiceModule(
            application = application,
            voiceSettings = voiceSettings,
            ha = HaSubgraphVoiceCollaborator(haSubgraph),
            voiceModelState = voiceModelState,
            eventBus = eventBus,
            scope = scope,
        )

    val ttsPlayer get() = module.ttsPlayer
    val providerRegistry get() = module.providerRegistry
    val coordinator get() = module.coordinator
    val captureLoop get() = module.captureLoop
    val recordingComponent get() = module.recordingComponent
    val recordingRepository get() = module.recordingRepository
    val recordingService get() = module.recordingService
}

private class HaSubgraphVoiceCollaborator(
    private val haSubgraph: HaSubgraph,
) : HaVoiceCollaborator {
    override val haUrl: StateFlow<String?> get() = haSubgraph.haUrlFlow
    override val tokenProvider: HaTokenProvider get() = haSubgraph.tokenProvider
    override val serviceCalls: HaServiceCallClient get() = haSubgraph.serviceCalls
    override val assistClient: HaAssistClient get() = haSubgraph.assistClient
    override val webSocketClient: HaWebSocketClient get() = haSubgraph.client
}
