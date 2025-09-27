package com.example.lilspeaker.features.llm

import com.example.lilspeaker.features.assistant.AssistantSettings

data class LlmGenerationRequest(
    val prompt: String,
    val settings: AssistantSettings
)
