package com.superdash.ha

data class HaVoiceExposureSnapshot(
    val exposedEntityIds: Set<String>,
    val loaded: Boolean,
)
