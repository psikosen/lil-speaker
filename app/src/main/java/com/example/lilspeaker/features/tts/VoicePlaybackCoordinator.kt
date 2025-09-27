package com.example.lilspeaker.features.tts

import com.example.lilspeaker.core.logging.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class VoicePlaybackCoordinator(
    private val ttsEngine: KittenTtsEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val queue = Channel<Pair<String, VoiceSelection>>(capacity = Channel.UNLIMITED)

    suspend fun enqueue(text: String, selection: VoiceSelection) {
        queue.send(text to selection)
    }

    suspend fun observeQueue() {
        for ((text, selection) in queue) {
            withContext(dispatcher) {
                ttsEngine.speak(text, selection)
            }
        }
    }

    suspend fun stopPlayback() {
        while (queue.tryReceive().isSuccess) {
            // Drain queued items
        }
        withContext(dispatcher) {
            ttsEngine.stop()
            delay(50)
        }
        AppLogger.i(
            sourceClass = "VoicePlaybackCoordinator",
            function = "stopPlayback",
            systemSection = "tts_pipeline",
            message = "Playback halted"
        )
    }
}
