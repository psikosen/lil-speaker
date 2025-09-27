package com.example.lilspeaker.features.chat

import com.example.lilspeaker.features.assistant.AssistantSettings
import com.example.lilspeaker.features.tts.VoiceProfile

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isProcessing: Boolean = false,
    val partialAssistant: String = "",
    val availableVoices: List<VoiceProfile> = emptyList(),
    val selectedVoiceId: String? = null,
    val speakingRate: Float = 1.0f,
    val gainDb: Float = 0.0f,
    val telemetryEnabled: Boolean = false,
    val diagnosticsAllowed: Boolean = false,
    val isSpeaking: Boolean = false,
    val performanceProfile: AssistantSettings.PerformanceProfile = AssistantSettings.PerformanceProfile.Balanced
)
