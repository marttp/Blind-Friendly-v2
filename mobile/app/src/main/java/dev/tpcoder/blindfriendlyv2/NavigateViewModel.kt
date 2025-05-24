package dev.tpcoder.blindfriendlyv2

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.tpcoder.blindfriendlyv2.service.CameraService
import dev.tpcoder.blindfriendlyv2.service.TTSService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class NavigateViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraService = CameraService(application)
    private val ttsService = TTSService(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        checkLanguageAvailability()
    }

    private fun checkLanguageAvailability() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            val isThaiAvailable = ttsService.isLanguageAvailable(TTSService.LANGUAGE_THAI)
            val isJapaneseAvailable = ttsService.isLanguageAvailable(TTSService.LANGUAGE_JAPANESE)
            if (!isThaiAvailable && !isJapaneseAvailable) {
                exitProcess(1)
            }
        }
    }

    fun bindCameraPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraService.bindPreview(previewView, lifecycleOwner)
    }

    fun performScan() {
        viewModelScope.launch {
            try {
                // Capture image
                val bitmap = cameraService.captureImage()

                // Analyze with AI
                // val result = aiService.analyzeImage(bitmap)

                // Update UI and speak
                val text = when (_uiState.value.currentLanguage) {
                    TTSService.LANGUAGE_JAPANESE -> "こんにちは"
                    TTSService.LANGUAGE_THAI -> "สวัสดี"
                    else -> "Hello"
                }
                _uiState.update { it.copy(statusText = text) }
                speak(text)

                // Send to watch
                // bluetoothService.sendMessage(result)

            } catch (e: Exception) {
                _uiState.update { it.copy(statusText = "Error scanning") }
            }
        }
    }

    fun toggleLanguage() {
        val nextIndex =
            (_uiState.value.currentLanguageIndex + 1) % uiState.value.availableLanguages.size
        val newLanguage = uiState.value.availableLanguages[nextIndex]
        val success = ttsService.setLanguage(newLanguage)
        if (success) {
            _uiState.update {
                it.copy(
                    currentLanguageIndex = nextIndex,
                    currentLanguage = newLanguage
                )
            }
            val languageChangedText = when (newLanguage) {
                TTSService.LANGUAGE_JAPANESE -> "日本語に切り替えました"
                TTSService.LANGUAGE_THAI -> "เปลี่ยนเป็นภาษาไทย"
                else -> "Change to English"
            }
            speak(languageChangedText)
        }
    }

    fun getCurrentLanguage(): String {
        return ttsService.getCurrentLanguage()
    }

    fun speak(text: String, priority: Boolean = false) {
        ttsService.speak(text, priority)
    }

    override fun onCleared() {
        super.onCleared()
        // Shutdown TTS when ViewModel is cleared
        ttsService.shutdown()
    }
}

data class UiState(
    val statusText: String = "",
    var currentLanguageIndex: Int = 0,
    val availableLanguages: List<String> = listOf(
        TTSService.LANGUAGE_ENGLISH,
        TTSService.LANGUAGE_THAI,
        TTSService.LANGUAGE_JAPANESE
    ),
    val currentLanguage: String = availableLanguages[currentLanguageIndex],
)