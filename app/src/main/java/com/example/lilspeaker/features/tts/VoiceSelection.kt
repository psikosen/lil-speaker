package com.example.lilspeaker.features.tts

import androidx.compose.runtime.Immutable

@Immutable
data class VoiceSelection(
    val profile: VoiceProfile,
    val speakingRate: Float,
    val gainDb: Float
)
