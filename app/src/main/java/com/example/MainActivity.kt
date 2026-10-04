package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.data.model.ScreenState
import com.example.ui.components.ApiKeyDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageAiScreen
import com.example.ui.screens.VoiceAiScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpaceBlack
import com.example.viewmodel.ProAiViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ProAiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SpaceBlack
                ) {
                    ProAiApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ProAiApp(viewModel: ProAiViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val imageAiState by viewModel.imageAiState.collectAsState()
    val voiceAiState by viewModel.voiceAiState.collectAsState()
    val isApiKeyDialogOpen by viewModel.apiKeyDialogOpen.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()
    val currentSpeakingId by viewModel.currentSpeakingId.collectAsState()

    when (currentScreen) {
        ScreenState.HOME -> {
            HomeScreen(
                hasApiKey = hasApiKey,
                onNavigateTo = { screen -> viewModel.navigateTo(screen) },
                onQuickPromptSelected = { prompt ->
                    viewModel.navigateTo(ScreenState.CHAT)
                    viewModel.sendChatMessage(prompt)
                },
                onOpenApiKeyDialog = { viewModel.setApiKeyDialogOpen(true) }
            )
        }

        ScreenState.CHAT -> {
            ChatScreen(
                messages = chatMessages,
                isLoading = isChatLoading,
                hasApiKey = hasApiKey,
                currentSpeakingId = currentSpeakingId,
                onBack = { viewModel.navigateBack() },
                onSendMessage = { text, imageBase64 ->
                    viewModel.sendChatMessage(text, imageBase64)
                },
                onNewChat = { viewModel.startNewChat() },
                onSpeakClick = { message ->
                    if (currentSpeakingId == message.id) {
                        viewModel.stopSpeaking()
                    } else {
                        viewModel.speakText(message.text, message.id)
                    }
                },
                onRetryClick = { viewModel.retryLastMessage() },
                onOpenApiKeyDialog = { viewModel.setApiKeyDialogOpen(true) }
            )
        }

        ScreenState.IMAGE_AI -> {
            ImageAiScreen(
                state = imageAiState,
                onPromptChange = { prompt -> viewModel.updateImagePrompt(prompt) },
                onAspectRatioChange = { ratio -> viewModel.updateImageAspectRatio(ratio) },
                onGenerateClick = { viewModel.generateImage() },
                onBack = { viewModel.navigateBack() }
            )
        }

        ScreenState.VOICE_AI -> {
            VoiceAiScreen(
                state = voiceAiState,
                onVoiceTranscription = { text -> viewModel.onVoiceTranscriptionReceived(text) },
                onReplayAudio = { text -> viewModel.speakText(text) },
                onStopAudio = { viewModel.stopSpeaking() },
                onBack = { viewModel.navigateBack() }
            )
        }
    }

    if (isApiKeyDialogOpen) {
        ApiKeyDialog(
            initialKey = viewModel.getStoredApiKey(),
            onDismiss = { viewModel.setApiKeyDialogOpen(false) },
            onSave = { newKey -> viewModel.saveCustomApiKey(newKey) }
        )
    }
}
