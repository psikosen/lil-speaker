package com.example.lilspeaker.features.assistant

import com.example.lilspeaker.core.logging.AppLogger
import com.example.lilspeaker.features.chat.ChatMessage
import com.example.lilspeaker.features.llm.ChatPromptFormatter
import com.example.lilspeaker.features.llm.LocalLlmEngine
import com.example.lilspeaker.features.llm.LlmGenerationRequest
import com.example.lilspeaker.features.tts.TokenSegmenter
import com.example.lilspeaker.features.tts.VoicePlaybackCoordinator
import com.example.lilspeaker.features.tts.VoiceSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class LocalAssistant(
    private val formatter: ChatPromptFormatter,
    private val llmEngine: LocalLlmEngine,
    private val segmenter: TokenSegmenter,
    private val playback: VoicePlaybackCoordinator
) {
    fun respond(
        messages: List<ChatMessage>,
        assistantSettings: AssistantSettings,
        voiceSelection: VoiceSelection
    ): Flow<AssistantEvent> = channelFlow {
        val prompt = formatter.format(messages)
        AppLogger.i(
            sourceClass = "LocalAssistant",
            function = "respond",
            systemSection = "llm_request",
            message = "Dispatching prompt with ${messages.size} turns"
        )
        val completionRequest = LlmGenerationRequest(
            prompt = prompt,
            settings = assistantSettings
        )
        val streaming = llmEngine.stream(completionRequest)
        val localSegmenter = segmenter.newSession()
        try {
            streaming.collect { token ->
                send(AssistantEvent.Token(token))
                val segments = localSegmenter.append(token)
                segments.forEach { segment ->
                    send(AssistantEvent.Segment(segment))
                    playback.enqueue(segment, voiceSelection)
                }
            }
            val tail = localSegmenter.flush()
            tail.forEach { segment ->
                send(AssistantEvent.Segment(segment))
                playback.enqueue(segment, voiceSelection)
            }
            send(AssistantEvent.Completed)
        } catch (t: Throwable) {
            send(AssistantEvent.Error(t))
            AppLogger.e(
                sourceClass = "LocalAssistant",
                function = "respond",
                systemSection = "llm_request",
                message = "Generation failed",
                throwable = t
            )
        }
    }
}
