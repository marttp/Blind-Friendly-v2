package dev.tpcoder.blindfriendlyv2.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

class AIService(private val context: Context) {
    private var llmInference: LlmInference? = null

    // Optimize session options for faster inference
    private val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTemperature(0.1f)  // Lower temperature for more deterministic and faster responses
        .setTopK(5)  // Reduce from 20 to 5 for faster processing
        .setGraphOptions(
            GraphOptions.builder()
                .setEnableVisionModality(true)
                .build()
        )
        .build()

    // Path to manually pushed model
    private val modelPath = "/data/local/tmp/llm/gemma-3n-E2B.task"

    // Flag to track initialization status
    private var isInitialized = false
    private var initializationError: String? = null

    // Cache for prompts to avoid rebuilding them each time
    private val promptCache = mutableMapOf<String, String>()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d("AIService", "Initializing AI model from path: $modelPath")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxNumImages(1)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            // Pre-cache prompts for all supported languages
            TTSService.LANGUAGE_ENGLISH.let { promptCache[it] = buildNavigationPrompt(it) }
            TTSService.LANGUAGE_JAPANESE.let { promptCache[it] = buildNavigationPrompt(it) }
            TTSService.LANGUAGE_THAI.let { promptCache[it] = buildNavigationPrompt(it) }

            isInitialized = true
            Log.d("AIService", "AI model initialized successfully")
        } catch (e: Exception) {
            isInitialized = false
            initializationError = e.message
            Log.e("AIService", "Failed to load model from $modelPath: ${e.message}", e)
            throw Exception("Failed to load model from $modelPath: ${e.message}")
        }
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        selectedLanguage: String
    ): String = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            Log.e("AIService", "Model not initialized. Error: $initializationError")
            return@withContext "Error: AI model not initialized. Please restart the app."
        }

        llmInference ?: run {
            Log.e("AIService", "LlmInference is null despite initialization flag")
            return@withContext "Error: AI model unavailable. Please restart the app."
        }

        // Optimize image processing - resize bitmap to reduce processing time
        // Most vision models don't need full resolution images
        val resizedBitmap = if (bitmap.width > 512 || bitmap.height > 512) {
            val scaleFactor = 512f / bitmap.width.coerceAtLeast(bitmap.height)
            bitmap.scale(
                (bitmap.width * scaleFactor).toInt(),
                (bitmap.height * scaleFactor).toInt()
            )
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(resizedBitmap).build()

        // Use cached prompt to avoid string building overhead
        val prompt = promptCache[selectedLanguage] ?: buildNavigationPrompt(selectedLanguage).also {
            promptCache[selectedLanguage] = it
        }

        return@withContext try {
            Log.d("AIService", "Starting image analysis")
            val startTime = System.currentTimeMillis()

            val result =
                LlmInferenceSession.createFromOptions(llmInference, sessionOptions).use { s ->
                    s.addQueryChunk(prompt)
                    s.addImage(mpImage)
                    s.generateResponse()
                }

            val endTime = System.currentTimeMillis()
            Log.d("AIService", "Image analysis completed in ${endTime - startTime}ms")

            result
        } catch (e: Exception) {
            Log.e("AIService", "Error analyzing image: ${e.message}", e)
            "Unable to analyze path: ${e.message}"
        }
    }

    private fun buildNavigationPrompt(language: String): String {
        return when (language) {
            TTSService.LANGUAGE_JAPANESE -> """
            視覚障碍者ナビゲーション補助。この画像から前方の障害物や危険を特定し、その名前と距離を簡潔に説明してください。
            - 最大15単語
            - 前方0-10メートルのみ
            - 物体の名前と距離を含める
            - 歩行に関連する情報のみ
            - ステータスを先頭に付ける: Clear/Obstacle/Danger
            
            例: 「縁石が1メートル先」「自転車が3メートル先」「前方の経路はクリア」「左折可能」
        """.trimIndent()

            TTSService.LANGUAGE_THAI -> """
            ผู้ช่วยนำทางสำหรับคนตาบอด วิเคราะห์ภาพนี้และบอกชื่อสิ่งกีดขวางหรืออันตรายข้างหน้าพร้อมระยะทาง
            - สูงสุด 15 คำ
            - เฉพาะระยะ 0-10 เมตรข้างหน้า
            - ระบุชื่อของวัตถุและระยะทาง
            - เฉพาะข้อมูลที่เกี่ยวกับการเดิน
            - นำหน้าด้วยสถานะ: Clear/Obstacle/Danger
            
            ตัวอย่าง: "ขอบทาง 1 เมตรข้างหน้า" "รถจักรยาน 3 เมตรข้างหน้า" "เส้นทางโล่ง" "เลี้ยวซ้ายได้"
        """.trimIndent()

            else -> """
            Navigation assistant for blind people. Identify obstacles or hazards ahead from this image, specifying their name and distance.
            - Max 15 words
            - Only 0-10 meters ahead
            - Include the object's name and distance
            - Only walking-related info
            - Prefix with state: Clear/Obstacle/Danger
            
            Examples: "Curb 1 meter ahead" "Bicycle 3 meters ahead" "Clear path" "Left turn available"
        """.trimIndent()
        }
    }

    fun shutdown() {
        llmInference?.close()
        llmInference = null
        isInitialized = false
    }
}
