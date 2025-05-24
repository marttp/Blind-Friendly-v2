package dev.tpcoder.blindfriendlyv2.screens

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import dev.tpcoder.blindfriendlyv2.service.AIService
import dev.tpcoder.blindfriendlyv2.service.BluetoothService
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
    private val bluetoothService = BluetoothService(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    
    private val _modelState = MutableStateFlow(ModelState())
    val modelState: StateFlow<ModelState> = _modelState

    init {
        initializeModel()
        initializeBluetooth()
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
                _uiState.update { it.copy(statusText = "Checking...") }
                // Capture image
                val bitmap = cameraService.captureImage()
                val currentLanguage = _uiState.value.currentLanguage
                // Analyze with AI
                 val textResult = aiService.analyzeImage(bitmap, currentLanguage)
                // Update UI and speak
                _uiState.update { it.copy(statusText = textResult) }
                speak(textResult)
                sendToWatch(textResult)
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(statusText = "Error checking") }
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
            _uiState.update { it.copy(statusText = languageChangedText) }
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

    fun sendToWatch(message: String) {
//        val pattern = when {
//            result.contains("clear", ignoreCase = true) -> 1
//            result.contains("careful", ignoreCase = true) ||
//                    result.contains("caution", ignoreCase = true) -> 2
//            result.contains("stop", ignoreCase = true) ||
//                    result.contains("danger", ignoreCase = true) -> 3
//            else -> 2
//        }
        // Send to watch
        bluetoothService.sendToWatch(1, "clear")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initializeBluetooth() {
        bluetoothService.startServer { message ->
            if (message == "SCAN_REQUEST") {
                performScan()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Shutdown TTS when ViewModel is cleared
        ttsService.shutdown()
        aiService.shutdown()
        cameraService.shutdown()
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