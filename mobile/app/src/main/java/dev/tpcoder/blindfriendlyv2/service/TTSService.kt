package dev.tpcoder.blindfriendlyv2.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

class TTSService(
    context: Context,
    private var language: String = LANGUAGE_ENGLISH
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false
    
    // Define supported languages
    companion object {
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_THAI = "th"
        const val LANGUAGE_JAPANESE = "ja"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setLanguage(language)
            isReady = true
        }
    }

    fun setLanguage(languageCode: String): Boolean {
        if (!isReady) return false
        
        val locale = when (languageCode) {
            LANGUAGE_JAPANESE -> Locale("ja", "JP")
            LANGUAGE_THAI -> Locale("th", "TH")
            else -> Locale.US
        }
        
        val result = tts.setLanguage(locale)
        language = languageCode
        
        return result != TextToSpeech.LANG_MISSING_DATA && 
               result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun getCurrentLanguage(): String {
        return language
    }

    fun speak(text: String, priority: Boolean = false) {
        if (!isReady) return
        val utteranceId = UUID.randomUUID().toString()
        stop()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun isLanguageAvailable(languageCode: String): Boolean {
        if (!isReady) return false

        val locale = when (languageCode) {
            LANGUAGE_JAPANESE -> Locale("ja", "JP")
            LANGUAGE_THAI -> Locale("th", "TH")
            else -> Locale.US
        }
        
        val result = tts.isLanguageAvailable(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && 
               result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun stop() {
        tts.stop()
    }
    
    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}