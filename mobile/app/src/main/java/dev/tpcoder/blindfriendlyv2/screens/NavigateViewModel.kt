package dev.tpcoder.blindfriendlyv2.screens

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.tpcoder.blindfriendlyv2.service.AIService
import dev.tpcoder.blindfriendlyv2.service.CameraService
import dev.tpcoder.blindfriendlyv2.service.TTSService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NavigateViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraService = CameraService(application)
    private val ttsService = TTSService(application)
    private val aiService = AIService(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    
    private val _modelState = MutableStateFlow(ModelState())
    val modelState: StateFlow<ModelState> = _modelState

    init {
        initializeModel()
    }
    
    private fun initializeModel() {
        viewModelScope.launch {
            _modelState.update { it.copy(isLoading = true, error = null) }
            try {
                aiService.initialize()
                _modelState.update { it.copy(isLoading = false, isInitialized = true) }
            } catch (e: Exception) {
                _modelState.update { 
                    it.copy(
                        isLoading = false, 
                        isInitialized = false,
                        error = e.message ?: "Unknown error initializing model"
                    ) 
                }
            }
        }
    }

    fun bindCameraPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraService.bindPreview(previewView, lifecycleOwner)
    }

    fun performScan() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(statusText = "Scanning...") }
                // Capture image
                val bitmap = cameraService.captureImage()
                val currentLanguage = _uiState.value.currentLanguage
                // Analyze with AI
                 val textResult = aiService.analyzeImage(bitmap, currentLanguage)
                // Update UI and speak
                _uiState.update { it.copy(statusText = textResult) }
                speak(textResult)
                // Send to watch
                // bluetoothService.sendMessage(result)

            } catch (e: Exception) {
                e.printStackTrace()
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
    
    fun retryModelInitialization() {
        initializeModel()
    }

    override fun onCleared() {
        super.onCleared()
        // Shutdown TTS when ViewModel is cleared
        ttsService.shutdown()
        aiService.shutdown()
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

data class ModelState(
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val error: String? = null
)