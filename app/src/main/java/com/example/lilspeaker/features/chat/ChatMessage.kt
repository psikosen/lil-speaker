package com.example.lilspeaker.features.chat

data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val timestampMillis: Long
) {
    enum class Role(val label: String) {
        System("System"),
        User("You"),
        Assistant("Lil Speaker")
    }
}
