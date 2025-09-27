package com.example.lilspeaker.features.assistant

import androidx.compose.runtime.Immutable

@Immutable
sealed interface AssistantEvent {
    @Immutable
    data class Token(val value: String) : AssistantEvent

    @Immutable
    data class Segment(val text: String) : AssistantEvent

    @Immutable
    data object Completed : AssistantEvent

    @Immutable
    data class Error(val throwable: Throwable) : AssistantEvent
}
