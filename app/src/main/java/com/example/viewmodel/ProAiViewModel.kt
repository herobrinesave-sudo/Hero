package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.gemini.GeminiMessage
import com.example.data.gemini.GeminiRepository
import com.example.data.gemini.GeminiResult
import com.example.data.model.ChatMessage
import com.example.data.model.MessageStatus
import com.example.data.model.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class ImageAiState(
    val prompt: String = "",
    val aspectRatio: String = "1:1",
    val isLoading: Boolean = false,
    val generatedBase64: String? = null,
    val description: String? = null,
    val errorMessage: String? = null
)

data class VoiceAiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isThinking: Boolean = false,
    val spokenText: String = "",
    val aiResponse: String = "",
    val soundWaveLevel: Float = 0.2f
)

class ProAiViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository = GeminiRepository(application)
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private val _currentScreen = MutableStateFlow(ScreenState.HOME)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _imageAiState = MutableStateFlow(ImageAiState())
    val imageAiState: StateFlow<ImageAiState> = _imageAiState.asStateFlow()

    private val _voiceAiState = MutableStateFlow(VoiceAiState())
    val voiceAiState: StateFlow<VoiceAiState> = _voiceAiState.asStateFlow()

    private val _apiKeyDialogOpen = MutableStateFlow(false)
    val apiKeyDialogOpen: StateFlow<Boolean> = _apiKeyDialogOpen.asStateFlow()

    private val _hasApiKey = MutableStateFlow(repository.hasValidApiKey())
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _currentSpeakingId = MutableStateFlow<String?>(null)
    val currentSpeakingId: StateFlow<String?> = _currentSpeakingId.asStateFlow()

    init {
        textToSpeech = TextToSpeech(application, this)
        initWelcomeMessage()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceAiState.value = _voiceAiState.value.copy(isSpeaking = true)
                }

                override fun onDone(utteranceId: String?) {
                    _voiceAiState.value = _voiceAiState.value.copy(isSpeaking = false)
                    _currentSpeakingId.value = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _voiceAiState.value = _voiceAiState.value.copy(isSpeaking = false)
                    _currentSpeakingId.value = null
                }
            })
        }
    }

    private fun initWelcomeMessage() {
        val welcome = ChatMessage(
            text = "Welcome to Pro AI! I am your advanced AI assistant powered by Gemini intelligence. How can I assist you today?",
            isFromUser = false
        )
        _chatMessages.value = listOf(welcome)
    }

    fun navigateTo(screen: ScreenState) {
        _currentScreen.value = screen
        stopSpeaking()
        if (screen == ScreenState.VOICE_AI && _voiceAiState.value.aiResponse.isEmpty()) {
            _voiceAiState.value = _voiceAiState.value.copy(
                aiResponse = "Ready. Tap the microphone or speak to interact with Pro AI Voice."
            )
        }
    }

    fun navigateBack(): Boolean {
        return if (_currentScreen.value != ScreenState.HOME) {
            _currentScreen.value = ScreenState.HOME
            stopSpeaking()
            true
        } else {
            false
        }
    }

    fun setApiKeyDialogOpen(open: Boolean) {
        _apiKeyDialogOpen.value = open
    }

    fun saveCustomApiKey(key: String) {
        repository.saveCustomApiKey(key)
        _hasApiKey.value = repository.hasValidApiKey()
        _apiKeyDialogOpen.value = false
    }

    fun getStoredApiKey(): String {
        return repository.getApiKey()
    }

    fun startNewChat() {
        stopSpeaking()
        initWelcomeMessage()
    }

    fun sendChatMessage(userText: String, attachedImageBase64: String? = null) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() && attachedImageBase64 == null) return

        val userMessage = ChatMessage(
            text = trimmed.ifEmpty { "Analyzing attached image..." },
            isFromUser = true,
            imageBase64 = attachedImageBase64
        )

        val loadingMessage = ChatMessage(
            text = "",
            isFromUser = false,
            status = MessageStatus.LOADING
        )

        val updatedList = _chatMessages.value.toMutableList().apply {
            add(userMessage)
            add(loadingMessage)
        }
        _chatMessages.value = updatedList
        _isChatLoading.value = true

        viewModelScope.launch {
            // Build conversation history for Gemini
            val history = updatedList.dropLast(1).map {
                GeminiMessage(
                    role = if (it.isFromUser) "user" else "model",
                    text = it.text,
                    imageBase64 = it.imageBase64
                )
            }

            val result = repository.generateChatResponse(history)

            val currentList = _chatMessages.value.toMutableList()
            val lastIndex = currentList.indexOfLast { it.id == loadingMessage.id }

            when (result) {
                is GeminiResult.Success -> {
                    val aiMsg = ChatMessage(
                        id = loadingMessage.id,
                        text = result.data,
                        isFromUser = false,
                        status = MessageStatus.SENT
                    )
                    if (lastIndex != -1) {
                        currentList[lastIndex] = aiMsg
                    } else {
                        currentList.add(aiMsg)
                    }
                    _chatMessages.value = currentList
                }
                is GeminiResult.Error -> {
                    // Check if fallback response can be provided if API key missing
                    val responseText = if (result.isApiKeyMissing) {
                        generateDemoFallbackAnswer(trimmed)
                    } else {
                        "⚠️ ${result.message}"
                    }

                    val aiMsg = ChatMessage(
                        id = loadingMessage.id,
                        text = responseText,
                        isFromUser = false,
                        status = if (result.isApiKeyMissing) MessageStatus.SENT else MessageStatus.ERROR
                    )
                    if (lastIndex != -1) {
                        currentList[lastIndex] = aiMsg
                    } else {
                        currentList.add(aiMsg)
                    }
                    _chatMessages.value = currentList
                }
            }
            _isChatLoading.value = false
        }
    }

    fun retryLastMessage() {
        val lastUserMessage = _chatMessages.value.findLast { it.isFromUser }
        if (lastUserMessage != null) {
            // remove trailing error messages
            _chatMessages.value = _chatMessages.value.filter { it.status != MessageStatus.ERROR }
            sendChatMessage(lastUserMessage.text, lastUserMessage.imageBase64)
        }
    }

    // --- Voice AI Integration ---

    fun onVoiceTranscriptionReceived(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _voiceAiState.value = _voiceAiState.value.copy(
            spokenText = trimmed,
            isThinking = true,
            isListening = false
        )

        viewModelScope.launch {
            val history = listOf(
                GeminiMessage(
                    role = "user",
                    text = trimmed
                )
            )

            val result = repository.generateChatResponse(
                history,
                systemPrompt = "You are Pro AI Voice, a voice conversational assistant. Keep answers natural, concise (under 3 sentences), highly articulate, and directly spoken."
            )

            val reply = when (result) {
                is GeminiResult.Success -> result.data
                is GeminiResult.Error -> {
                    if (result.isApiKeyMissing) {
                        generateDemoFallbackAnswer(trimmed)
                    } else {
                        result.message
                    }
                }
            }

            _voiceAiState.value = _voiceAiState.value.copy(
                aiResponse = reply,
                isThinking = false
            )

            // Auto-speak reply
            speakText(reply)
        }
    }

    fun setVoiceListening(listening: Boolean) {
        _voiceAiState.value = _voiceAiState.value.copy(isListening = listening)
    }

    fun speakText(text: String, messageId: String? = null) {
        if (text.isEmpty()) return
        stopSpeaking()
        _currentSpeakingId.value = messageId
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, messageId ?: "voice_ai")
        _voiceAiState.value = _voiceAiState.value.copy(isSpeaking = true)
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        _currentSpeakingId.value = null
        _voiceAiState.value = _voiceAiState.value.copy(isSpeaking = false)
    }

    // --- Image AI Integration ---

    fun updateImagePrompt(prompt: String) {
        _imageAiState.value = _imageAiState.value.copy(prompt = prompt, errorMessage = null)
    }

    fun updateImageAspectRatio(ratio: String) {
        _imageAiState.value = _imageAiState.value.copy(aspectRatio = ratio)
    }

    fun generateImage() {
        val prompt = _imageAiState.value.prompt.trim()
        if (prompt.isEmpty()) return

        _imageAiState.value = _imageAiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            val result = repository.generateImage(prompt, _imageAiState.value.aspectRatio)
            when (result) {
                is GeminiResult.Success -> {
                    _imageAiState.value = _imageAiState.value.copy(
                        isLoading = false,
                        generatedBase64 = result.data.first,
                        description = result.data.second
                    )
                }
                is GeminiResult.Error -> {
                    _imageAiState.value = _imageAiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /**
     * Smart local assistant demonstration responses for zero-friction preview exploration.
     */
    private fun generateDemoFallbackAnswer(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! I am Pro AI, running Gemini intelligence. I can help you solve complex problems, write and debug code, generate creative concepts, and synthesize data. What would you like to explore?"
            lower.contains("quantum") ->
                "Quantum computing uses quantum bits or qubits. Unlike classical bits that are strictly 0 or 1, qubits can exist in a superposition of both states simultaneously. Combined with quantum entanglement, this unlocks exponential computation speeds for molecular simulation, cryptography, and complex optimization."
            lower.contains("code") || lower.contains("kotlin") ->
                "Here is an idiomatic Kotlin Coroutine StateFlow pattern:\n\n```kotlin\nclass StateHolder {\n    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)\n    val uiState: StateFlow<UiState> = _uiState.asStateFlow()\n    \n    fun fetch() = viewModelScope.launch {\n        _uiState.value = UiState.Success(data)\n    }\n}\n```\n\nThis guarantees unidirectional data flow and thread-safe UI updates."
            lower.contains("pitch") || lower.contains("idea") ->
                "Here is a high-impact pitch for 2035:\n\n\"NeuralMesh: Autonomous edge-AI swarms that synthesize localized sensor data in real-time, eliminating cloud latency for next-generation robotics, planetary rovers, and smart cities.\""
            else ->
                "Pro AI processed your inquiry: \"$prompt\".\n\n💡 To enable live real-time Gemini 3.5 Flash queries, add your GEMINI_API_KEY in the Secrets panel or click the top-right Key icon to paste your API key."
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
