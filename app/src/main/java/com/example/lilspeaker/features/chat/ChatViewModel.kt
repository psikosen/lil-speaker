package com.example.lilspeaker.features.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lilspeaker.core.logging.AppLogger
import com.example.lilspeaker.features.assistant.AssistantEvent
import com.example.lilspeaker.features.assistant.AssistantSettings
import com.example.lilspeaker.features.assistant.LocalAssistant
import com.example.lilspeaker.features.download.ModelDownloadManager
import com.example.lilspeaker.features.llm.ChatPromptFormatter
import com.example.lilspeaker.features.llm.LocalLlmEngine
import com.example.lilspeaker.features.privacy.PrivacySettingsRepository
import com.example.lilspeaker.features.tts.KittenTtsEngine
import com.example.lilspeaker.features.tts.TokenSegmenter
import com.example.lilspeaker.features.tts.VoicePlaybackCoordinator
import com.example.lilspeaker.features.tts.VoiceProfile
import com.example.lilspeaker.features.tts.VoiceSelection
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val idGenerator = AtomicLong(0)
    private val downloadManager = ModelDownloadManager(application)
    private val llmEngine = LocalLlmEngine()
    private val ttsEngine = KittenTtsEngine()
    private val playback = VoicePlaybackCoordinator(ttsEngine)
    private val assistant = LocalAssistant(
        formatter = ChatPromptFormatter(),
        llmEngine = llmEngine,
        segmenter = TokenSegmenter(),
        playback = playback
    )
    private val privacy = PrivacySettingsRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val telemetryFlow: StateFlow<Boolean> = privacy.telemetryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val diagnosticsFlow: StateFlow<Boolean> = privacy.diagnosticsAllowed
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var streamingJob: Job? = null

    init {
        viewModelScope.launch {
            playback.observeQueue()
        }
        viewModelScope.launch {
            combine(telemetryFlow, diagnosticsFlow) { telemetry, diagnostics ->
                _uiState.update { current ->
                    current.copy(
                        telemetryEnabled = telemetry,
                        diagnosticsAllowed = diagnostics
                    )
                }
            }.collect { }
        }
        viewModelScope.launch {
            initialiseModels()
        }
    }

    fun updateDraft(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun updateSpeakingRate(rate: Float) {
        _uiState.update { it.copy(speakingRate = rate.coerceIn(0.8f, 1.4f)) }
    }

    fun updateGainDb(gain: Float) {
        _uiState.update { it.copy(gainDb = gain.coerceIn(-6f, 6f)) }
    }

    fun selectVoice(voiceId: String) {
        _uiState.update { state ->
            if (state.selectedVoiceId == voiceId) state else state.copy(selectedVoiceId = voiceId)
        }
    }

    fun updatePerformanceProfile(profile: AssistantSettings.PerformanceProfile) {
        _uiState.update { it.copy(performanceProfile = profile) }
        viewModelScope.launch {
            initialiseModels()
        }
    }

    fun toggleTelemetry(value: Boolean) {
        viewModelScope.launch {
            privacy.setTelemetry(value)
        }
    }

    fun toggleDiagnostics(value: Boolean) {
        viewModelScope.launch {
            privacy.setDiagnostics(value)
        }
    }

    fun stopSpeaking() {
        viewModelScope.launch {
            playback.stopPlayback()
            _uiState.update { it.copy(isSpeaking = false) }
        }
    }

    fun sendMessage() {
        val draft = _uiState.value.draft.trim()
        if (draft.isEmpty()) return
        if (_uiState.value.isProcessing) return
        val timestamp = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = idGenerator.incrementAndGet(),
            role = ChatMessage.Role.User,
            content = draft,
            timestampMillis = timestamp
        )
        _uiState.update { state ->
            state.copy(
                draft = "",
                isProcessing = true,
                partialAssistant = "",
                messages = (state.messages + userMessage).toImmutableList()
            )
        }
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            streamAssistantResponse()
        }
    }

    private suspend fun streamAssistantResponse() {
        val state = _uiState.value
        val selection = resolveVoiceSelection(state)
        val settings = AssistantSettings(
            performanceProfile = state.performanceProfile
        )
        val flow = assistant.respond(state.messages, settings, selection)
        flow.collect { event ->
            when (event) {
                is AssistantEvent.Token -> {
                    _uiState.update { current ->
                        current.copy(
                            partialAssistant = current.partialAssistant + event.value,
                            isSpeaking = true
                        )
                    }
                }
                is AssistantEvent.Segment -> {
                    if (event.text.isBlank()) return@collect
                    val messageId = idGenerator.incrementAndGet()
                    val assistantMessage = ChatMessage(
                        id = messageId,
                        role = ChatMessage.Role.Assistant,
                        content = event.text,
                        timestampMillis = System.currentTimeMillis()
                    )
                    _uiState.update { current ->
                        current.copy(
                            messages = (current.messages + assistantMessage).toImmutableList(),
                            partialAssistant = ""
                        )
                    }
                }
                AssistantEvent.Completed -> {
                    _uiState.update { current ->
                        current.copy(
                            isProcessing = false,
                            partialAssistant = "",
                            isSpeaking = false
                        )
                    }
                }
                is AssistantEvent.Error -> {
                    _uiState.update { current ->
                        current.copy(
                            isProcessing = false,
                            partialAssistant = "",
                            isSpeaking = false
                        )
                    }
                    AppLogger.e(
                        sourceClass = "ChatViewModel",
                        function = "streamAssistantResponse",
                        systemSection = "chat_pipeline",
                        message = "Assistant generation failed",
                        throwable = event.throwable
                    )
                }
            }
        }
    }

    private fun resolveVoiceSelection(state: ChatUiState): VoiceSelection {
        val profile = state.availableVoices.firstOrNull { it.id == state.selectedVoiceId }
            ?: state.availableVoices.firstOrNull()
            ?: VoiceProfile("default", "Default", FloatArray(256) { 0f })
        return VoiceSelection(
            profile = profile,
            speakingRate = state.speakingRate,
            gainDb = state.gainDb
        )
    }

    private suspend fun initialiseModels() {
        val context = getApplication<Application>()
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()
        val llmFile = downloadManager.ensureModel(
            fileName = "lfm2-2.6b-q4_0.gguf",
            url = "https://huggingface.co/LiquidAI/LFM2-2.6B-GGUF/resolve/main/LFM2-2.6B-Q4_0.gguf?download=true",
            checksum = ""
        )
        val ttsFile = downloadManager.ensureModel(
            fileName = "kitten_tts_nano_v0_2.onnx",
            url = "https://huggingface.co/KittenML/kitten-tts-nano-0.2/resolve/main/kitten_tts_nano_v0_2.onnx?download=true",
            checksum = ""
        )
        val voicesFile = downloadManager.ensureModel(
            fileName = "voices.npz",
            url = "https://huggingface.co/KittenML/kitten-tts-nano-0.2/resolve/main/voices.npz?download=true",
            checksum = ""
        )
        llmEngine.ensureLoaded(llmFile, maxContext = 4096, performanceProfile = statePerformanceOffset())
        ttsEngine.prepare(ttsFile, voicesFile)
        val voices = ttsEngine.availableVoices()
        _uiState.update { current ->
            current.copy(
                availableVoices = voices,
                selectedVoiceId = current.selectedVoiceId ?: voices.firstOrNull()?.id
            )
        }
    }

    private fun statePerformanceOffset(): Int {
        return when (_uiState.value.performanceProfile) {
            AssistantSettings.PerformanceProfile.Battery -> 4
            AssistantSettings.PerformanceProfile.Balanced -> 2
            AssistantSettings.PerformanceProfile.Turbo -> 1
        }
    }
}
