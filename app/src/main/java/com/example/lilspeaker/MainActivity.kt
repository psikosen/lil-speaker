package com.example.lilspeaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.lilspeaker.features.assistant.AssistantSettings
import com.example.lilspeaker.features.chat.ChatMessage
import com.example.lilspeaker.features.chat.ChatViewModel
import com.example.lilspeaker.features.chat.MessageComposer
import com.example.lilspeaker.features.tts.VoiceProfile
import com.example.lilspeaker.ui.theme.LilSpeakerTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.lilspeaker.R

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LilSpeakerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            ChatTopBar(
                telemetryEnabled = state.telemetryEnabled,
                diagnosticsAllowed = state.diagnosticsAllowed,
                onTelemetryChanged = viewModel::toggleTelemetry,
                onDiagnosticsChanged = viewModel::toggleDiagnostics
            )
        },
        bottomBar = {
            Column {
                VoiceControls(
                    voices = state.availableVoices,
                    selectedVoiceId = state.selectedVoiceId,
                    speakingRate = state.speakingRate,
                    gainDb = state.gainDb,
                    performanceProfile = state.performanceProfile,
                    onVoiceSelected = viewModel::selectVoice,
                    onRateChange = viewModel::updateSpeakingRate,
                    onGainChange = viewModel::updateGainDb,
                    onStop = viewModel::stopSpeaking,
                    onProfileSelected = viewModel::updatePerformanceProfile
                )
                MessageComposer(
                    value = state.draft,
                    onValueChange = viewModel::updateDraft,
                    onSend = viewModel::sendMessage,
                    sending = state.isProcessing
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages) { message ->
                ChatBubble(message = message)
            }
            if (state.partialAssistant.isNotBlank()) {
                item(key = "streaming") {
                    ChatBubble(
                        message = ChatMessage(
                            id = -1,
                            role = ChatMessage.Role.Assistant,
                            content = state.partialAssistant,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val bubbleColor = if (message.role == ChatMessage.Role.User) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(
        color = bubbleColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message.role.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    telemetryEnabled: Boolean,
    diagnosticsAllowed: Boolean,
    onTelemetryChanged: (Boolean) -> Unit,
    onDiagnosticsChanged: (Boolean) -> Unit
) {
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.app_name)) },
        actions = {
            Column(
                modifier = Modifier.padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(id = R.string.telemetry_label))
                    Switch(checked = telemetryEnabled, onCheckedChange = onTelemetryChanged)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(id = R.string.diagnostics_label))
                    Switch(checked = diagnosticsAllowed, onCheckedChange = onDiagnosticsChanged)
                }
            }
        }
    )
}

@Composable
private fun VoiceControls(
    voices: List<VoiceProfile>,
    selectedVoiceId: String?,
    speakingRate: Float,
    gainDb: Float,
    performanceProfile: AssistantSettings.PerformanceProfile,
    onVoiceSelected: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onGainChange: (Float) -> Unit,
    onStop: () -> Unit,
    onProfileSelected: (AssistantSettings.PerformanceProfile) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VoiceDropdown(voices = voices, selectedVoiceId = selectedVoiceId, onVoiceSelected = onVoiceSelected)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(id = R.string.speaking_rate_label, speakingRate))
                Slider(
                    value = speakingRate,
                    onValueChange = onRateChange,
                    valueRange = 0.8f..1.4f,
                    colors = SliderDefaults.colors()
                )
                Text(text = stringResource(id = R.string.gain_label, gainDb))
                Slider(
                    value = gainDb,
                    onValueChange = onGainChange,
                    valueRange = -6f..6f
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PerformanceChips(
                    selected = performanceProfile,
                    onSelected = onProfileSelected
                )
                IconButton(onClick = onStop) {
                    Icon(imageVector = Icons.Filled.Pause, contentDescription = stringResource(id = R.string.stop_playback))
                }
            }
        }
    }
}

@Composable
private fun VoiceDropdown(
    voices: List<VoiceProfile>,
    selectedVoiceId: String?,
    onVoiceSelected: (String) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded.value = true }) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = selectedVoiceLabel(voices, selectedVoiceId))
        }
        DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(text = voice.displayName) },
                    onClick = {
                        expanded.value = false
                        onVoiceSelected(voice.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun selectedVoiceLabel(voices: List<VoiceProfile>, selectedVoiceId: String?): String {
    return voices.firstOrNull { it.id == selectedVoiceId }?.displayName ?: stringResource(id = R.string.voice_default)
}

@Composable
private fun PerformanceChips(
    selected: AssistantSettings.PerformanceProfile,
    onSelected: (AssistantSettings.PerformanceProfile) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistantSettings.PerformanceProfile.values().forEach { profile ->
            AssistChip(
                onClick = { onSelected(profile) },
                label = { Text(text = profile.name) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (profile == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    labelColor = if (profile == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
