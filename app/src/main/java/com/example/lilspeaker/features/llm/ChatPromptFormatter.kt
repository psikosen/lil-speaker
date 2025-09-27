package com.example.lilspeaker.features.llm

import com.example.lilspeaker.features.chat.ChatMessage

class ChatPromptFormatter {
    fun format(messages: List<ChatMessage>): String {
        if (messages.isEmpty()) {
            return "<|startoftext|><|im_start|>system\nYou are Lil Speaker, a thoughtful and private on-device assistant.<|im_end|><|im_start|>assistant"
        }
        val builder = StringBuilder()
        builder.append("<|startoftext|>")
        var hasSystem = false
        messages.forEach { message ->
            when (message.role) {
                ChatMessage.Role.System -> {
                    hasSystem = true
                    builder.append("<|im_start|>system\n")
                    builder.append(message.content.trim())
                    builder.append("<|im_end|>")
                }
                ChatMessage.Role.User -> {
                    builder.append("<|im_start|>user\n")
                    builder.append(message.content.trim())
                    builder.append("<|im_end|>")
                }
                ChatMessage.Role.Assistant -> {
                    builder.append("<|im_start|>assistant\n")
                    builder.append(message.content.trim())
                    builder.append("<|im_end|>")
                }
            }
        }
        if (!hasSystem) {
            builder.insert(
                0,
                "<|startoftext|><|im_start|>system\nYou are Lil Speaker, a thoughtful and private on-device assistant.<|im_end|>"
            )
        }
        builder.append("<|im_start|>assistant")
        return builder.toString()
    }
}
