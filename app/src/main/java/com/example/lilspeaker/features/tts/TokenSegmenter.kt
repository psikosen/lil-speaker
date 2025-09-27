package com.example.lilspeaker.features.tts

class TokenSegmenter(
    private val maxChars: Int = 800
) {
    fun newSession(): Session = Session(maxChars)

    class Session(private val maxChars: Int) {
        private val buffer = StringBuilder()
        private var lastEmissionTimestamp = System.nanoTime()
        private val softTimeoutNanos = 1_200_000_000L
        private val breakChars = setOf('.', '!', '?', ';', ':', ',', '—', '\n')

        fun append(token: String): List<String> {
            val emissions = mutableListOf<String>()
            buffer.append(token)
            val now = System.nanoTime()
            if (buffer.length >= maxChars) {
                emissions += flushBuffer()
            } else {
                val lastChar = buffer.lastOrNull()
                if (lastChar != null && breakChars.contains(lastChar)) {
                    emissions += flushBuffer()
                } else if (now - lastEmissionTimestamp > softTimeoutNanos) {
                    emissions += flushBuffer()
                }
            }
            return emissions
        }

        fun flush(): List<String> {
            if (buffer.isEmpty()) return emptyList()
            return listOf(flushBuffer())
        }

        private fun flushBuffer(): String {
            val value = buffer.toString().trim()
            buffer.clear()
            lastEmissionTimestamp = System.nanoTime()
            return value
        }
    }
}
