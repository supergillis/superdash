package com.superdash.voice

import com.superdash.ha.HaConnectionState
import com.superdash.ha.HaTokens

internal object VoiceServiceRunPolicy {
    fun shouldRun(
        voiceEnabled: Boolean,
        haUrl: String?,
        tokens: HaTokens?,
        haState: HaConnectionState,
    ): Boolean =
        voiceEnabled &&
            !haUrl.isNullOrBlank() &&
            tokens != null &&
            haState !is HaConnectionState.NeedsReauth
}
