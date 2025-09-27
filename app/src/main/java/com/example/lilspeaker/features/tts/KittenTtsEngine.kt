package com.example.lilspeaker.features.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.lilspeaker.core.logging.AppLogger
import com.example.lilspeaker.features.tts.frontend.EnglishFrontEnd
import com.example.lilspeaker.features.tts.frontend.PhonemeEncoder
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KittenTtsEngine(
    private val frontEnd: EnglishFrontEnd = EnglishFrontEnd(),
    private val encoder: PhonemeEncoder = PhonemeEncoder(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var voiceProfiles: List<VoiceProfile> = emptyList()
    private var audioTrack: AudioTrack? = null
    private val prepared = AtomicBoolean(false)
    private val sampleRate = 24_000

    suspend fun prepare(modelFile: File, voicesFile: File) {
        withContext(dispatcher) {
            if (!modelFile.exists()) {
                throw IllegalStateException("Kitten model missing at ${modelFile.absolutePath}")
            }
            session = environment.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
            val loader = VoiceLibrary()
            voiceProfiles = loader.load(voicesFile)
            prepared.set(true)
            ensureAudioTrack()
        }
    }

    fun availableVoices(): List<VoiceProfile> = voiceProfiles

    suspend fun speak(text: String, selection: VoiceSelection) {
        if (!prepared.get()) {
            throw IllegalStateException("Kitten engine not prepared")
        }
        if (text.isBlank()) return
        val ortSession = session ?: throw IllegalStateException("Session missing")
        val phonemes = frontEnd.tokenize(text)
        val ids = encoder.encode(phonemes)
        val idBuffer = LongArray(ids.size) { ids[it].toLong() }
        val voice = selection.profile.embedding
        val gainScalar = 10f.pow(selection.gainDb / 20f)
        val inputs = HashMap<String, OnnxTensor>()
        withContext(dispatcher) {
            val phonemeTensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(idBuffer),
                longArrayOf(1, idBuffer.size.toLong())
            )
            val voiceTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(voice),
                longArrayOf(1, voice.size.toLong())
            )
            val speedTensor = OnnxTensor.createTensor(
                environment,
                floatArrayOf(selection.speakingRate)
            )
            inputs[PHONEME_INPUT] = phonemeTensor
            inputs[VOICE_INPUT] = voiceTensor
            inputs[SPEED_INPUT] = speedTensor
            val results = ortSession.run(inputs)
            results.use { collection ->
                val audioTensor = collection[OUTPUT_NAME]
                val audio = audioTensor.value as FloatArray
                val adjusted = FloatArray(audio.size) { index ->
                    (audio[index] * gainScalar).coerceIn(-1f, 1f)
                }
                play(adjusted)
            }
            inputs.values.forEach { it.close() }
        }
        AppLogger.i(
            sourceClass = "KittenTtsEngine",
            function = "speak",
            systemSection = "tts_pipeline",
            message = "Rendered segment"
        )
    }

    suspend fun stop() {
        withContext(dispatcher) {
            audioTrack?.pause()
            audioTrack?.flush()
        }
    }

    private fun ensureAudioTrack() {
        if (audioTrack != null) return
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(sampleRate * 4)
            .build().apply {
                play()
            }
    }

    private fun play(audio: FloatArray) {
        ensureAudioTrack()
        val track = audioTrack ?: return
        track.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
    }

    companion object {
        private const val PHONEME_INPUT = "phoneme_ids"
        private const val VOICE_INPUT = "voice_embedding"
        private const val SPEED_INPUT = "speaking_rate"
        private const val OUTPUT_NAME = "audio"
    }
}
