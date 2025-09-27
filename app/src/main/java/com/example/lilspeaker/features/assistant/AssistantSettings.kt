package com.example.lilspeaker.features.assistant

import androidx.compose.runtime.Immutable

@Immutable
data class AssistantSettings(
    val temperature: Float = 0.3f,
    val minP: Float = 0.15f,
    val repetitionPenalty: Float = 1.05f,
    val maxContextTokens: Int = 4096,
    val performanceProfile: PerformanceProfile = PerformanceProfile.Balanced
) {
    @Immutable
    enum class PerformanceProfile(val threadsOffset: Int) {
        Battery(4),
        Balanced(2),
        Turbo(1)
    }
}
