package com.example.lilspeaker.features.tts

import androidx.compose.runtime.Immutable

@Immutable
data class VoiceProfile(
    val id: String,
    val displayName: String,
    val embedding: FloatArray
)
