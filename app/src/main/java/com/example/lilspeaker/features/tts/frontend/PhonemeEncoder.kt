package com.example.lilspeaker.features.tts.frontend

class PhonemeEncoder {
    private val inventory = listOf(
        "SP", "AA", "AE", "AH", "AO", "AW", "AY", "B", "CH", "D", "DH",
        "EH", "ER", "EY", "F", "G", "HH", "IH", "IY", "JH", "K", "L",
        "M", "N", "NG", "OW", "OY", "P", "R", "S", "SH", "T", "TH",
        "UH", "UW", "V", "W", "Y", "Z"
    )
    private val indices = inventory.withIndex().associate { it.value to it.index }

    fun encode(phonemes: List<String>): IntArray {
        val buffer = IntArray(phonemes.size)
        phonemes.forEachIndexed { index, phoneme ->
            buffer[index] = indices[phoneme] ?: indices.getValue("SP")
        }
        return buffer
    }
}
