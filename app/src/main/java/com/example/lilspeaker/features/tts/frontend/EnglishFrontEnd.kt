package com.example.lilspeaker.features.tts.frontend

import java.text.Normalizer

class EnglishFrontEnd {
    private val dictionary: Map<String, List<String>> = mapOf(
        "hello" to listOf("HH", "AH", "L", "OW"),
        "lil" to listOf("L", "IH", "L"),
        "speaker" to listOf("S", "P", "IY", "K", "ER"),
        "android" to listOf("AE", "N", "D", "R", "OY", "D"),
        "device" to listOf("D", "IH", "V", "AY", "S"),
        "privacy" to listOf("P", "R", "AY", "V", "AH", "S", "IY"),
        "offline" to listOf("AO", "F", "L", "AY", "N")
    )

    fun tokenize(text: String): List<String> {
        val cleaned = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("[^a-z0-9\s']"), " ")
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        val phonemes = mutableListOf<String>()
        for (word in words) {
            val entry = dictionary[word]
            if (entry != null) {
                phonemes += entry
            } else {
                phonemes += fallback(word)
            }
        }
        return phonemes
    }

    private fun fallback(word: String): List<String> {
        val mapping = mapOf(
            'a' to "AH",
            'b' to "B",
            'c' to "K",
            'd' to "D",
            'e' to "EH",
            'f' to "F",
            'g' to "G",
            'h' to "HH",
            'i' to "IH",
            'j' to "JH",
            'k' to "K",
            'l' to "L",
            'm' to "M",
            'n' to "N",
            'o' to "OW",
            'p' to "P",
            'q' to "K",
            'r' to "R",
            's' to "S",
            't' to "T",
            'u' to "UW",
            'v' to "V",
            'w' to "W",
            'x' to "K S",
            'y' to "Y",
            'z' to "Z"
        )
        val result = mutableListOf<String>()
        word.forEach { char ->
            val value = mapping[char]
            if (value != null) {
                value.split(" ").forEach { token ->
                    result += token
                }
            }
        }
        return if (result.isEmpty()) listOf("SP") else result
    }
}
