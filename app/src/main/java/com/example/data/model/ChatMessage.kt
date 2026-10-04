package com.example.data.model

import java.util.UUID

enum class MessageStatus {
    SENT,
    LOADING,
    ERROR
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val imageBase64: String? = null,
    val imageMimeType: String? = "image/jpeg"
)

enum class ScreenState {
    HOME,
    CHAT,
    IMAGE_AI,
    VOICE_AI
}

data class QuickPrompt(
    val title: String,
    val prompt: String,
    val category: String,
    val iconName: String
)

val defaultQuickPrompts = listOf(
    QuickPrompt(
        title = "Quantum Computing",
        prompt = "Explain quantum computing and qubits in simple, intuitive terms.",
        category = "Science",
        iconName = "science"
    ),
    QuickPrompt(
        title = "Code Architecture",
        prompt = "Write a high-performance modern Kotlin coroutine state pattern.",
        category = "Coding",
        iconName = "code"
    ),
    QuickPrompt(
        title = "Futuristic Pitch",
        prompt = "Brainstorm a compelling 60-second pitch for a 2035 AI robotics venture.",
        category = "Idea",
        iconName = "lightbulb"
    ),
    QuickPrompt(
        title = "Cyber Security",
        prompt = "What are the most critical zero-trust architecture principles in 2026?",
        category = "Tech",
        iconName = "security"
    )
)
