package com.superdash.voice

import com.superdash.ha.HaAssistClient
import com.superdash.ha.HaServiceCallClient
import com.superdash.ha.HaTokenProvider
import com.superdash.ha.HaWebSocketClient
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow HA surface that the voice module needs from its host.
 *
 * Replaces the previous direct dependency on the app-layer `HaSubgraph`.
 * Apps construct an implementation that delegates to whichever HA wiring
 * they own, keeping the voice module unaware of cross-feature subgraph
 * composition.
 */
interface HaVoiceCollaborator {
    /** Latest HA base URL (e.g. `http://homeassistant.local:8123`) or null when unset. */
    val haUrl: StateFlow<String?>

    /** Long-lived HA token provider for authenticated HTTP requests (TTS playback, etc.). */
    val tokenProvider: HaTokenProvider

    /** Service-call client for executing HA actions selected by local intent recognition. */
    val serviceCalls: HaServiceCallClient

    /** Assist client for audio/text pipelines that the HA-backed STT/TTS path uses. */
    val assistClient: HaAssistClient

    /** WebSocket client exposing live entity/area/registry snapshots used by intent matching. */
    val webSocketClient: HaWebSocketClient
}
