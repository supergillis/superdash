package com.superdash.voice.action

import kotlinx.coroutines.flow.Flow

typealias VoiceActionProvider = (audio: Flow<ShortArray>) -> Flow<VoiceActionEvent>
