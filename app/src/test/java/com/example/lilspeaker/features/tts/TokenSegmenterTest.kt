package com.example.lilspeaker.features.tts

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TokenSegmenterTest {
    @Test
    fun emitsSegmentsOnPunctuationAndLength() {
        val segmenter = TokenSegmenter(maxChars = 20).newSession()
        val first = segmenter.append("Hello, ")
        val second = segmenter.append("world. ")
        val third = segmenter.append("This is a long sentence that should trigger a flush.")

        assertThat(first).isNotEmpty()
        assertThat(second).isNotEmpty()
        assertThat(third).isNotEmpty()
    }

    @Test
    fun flushEmitsRemainingBuffer() {
        val segmenter = TokenSegmenter().newSession()
        segmenter.append("partial text")
        val flushed = segmenter.flush()
        assertThat(flushed).containsExactly("partial text")
    }
}
