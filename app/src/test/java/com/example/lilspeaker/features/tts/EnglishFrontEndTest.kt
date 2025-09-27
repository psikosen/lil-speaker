package com.example.lilspeaker.features.tts

import com.example.lilspeaker.features.tts.frontend.EnglishFrontEnd
import com.example.lilspeaker.features.tts.frontend.PhonemeEncoder
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnglishFrontEndTest {
    private val frontEnd = EnglishFrontEnd()
    private val encoder = PhonemeEncoder()

    @Test
    fun tokenizesKnownWords() {
        val phonemes = frontEnd.tokenize("Hello Lil Speaker")
        assertThat(phonemes).containsAtLeast("HH", "OW", "L")
    }

    @Test
    fun encodesToIds() {
        val phonemes = frontEnd.tokenize("privacy")
        val ids = encoder.encode(phonemes)
        assertThat(ids).isNotEmpty()
    }
}
