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

class AIService(private val context: Context) {
    private var llmInference: LlmInference? = null
    private val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
        .setTemperature(0.3f)  // Lower temperature for consistent navigation
        .setTopK(20)
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

    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            Log.d("AIService", "Initializing AI model from path: $modelPath")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxNumImages(1)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
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
        
        val inference = llmInference ?: run {
            Log.e("AIService", "LlmInference is null despite initialization flag")
            return@withContext "Error: AI model unavailable. Please restart the app."
        }
        
        val mpImage = BitmapImageBuilder(bitmap).build()
        val prompt = buildNavigationPrompt(selectedLanguage)
        return@withContext try {
            Log.d("AIService", "Starting image analysis")
            LlmInferenceSession.createFromOptions(inference, sessionOptions)
                .use { session ->
                    session.addQueryChunk(prompt)
                    session.addImage(mpImage)
                    session.generateResponse()
                }.also {
                    Log.d("AIService", "Image analysis completed successfully")
                }
        } catch (e: Exception) {
            Log.e("AIService", "Error analyzing image: ${e.message}", e)
            "Unable to analyze path: ${e.message}"
        }
    }

    private fun buildNavigationPrompt(language: String): String {
        return when (language) {
            TTSService.LANGUAGE_JAPANESE -> """
                あなたは視覚障碍者のためのナビゲーションアシスタントです。この画像を分析し、重要なナビゲーション情報のみを提供してください。

                ルール：
                非常に簡潔に - 指示ごとに最大15単語
                直前の経路（前方0～10メートル）のみに焦点を当てる
                障害物や危険を優先する
                簡単な距離を使用：「2メートル先」、「5メートル先」、「すぐに」
                歩行経路に影響するものだけを言及する
                
                期待される応答形式：
                「障害物が3メートル先にあります」
                「前方の経路はクリアです」
                「左折可能です」
                「注意、縁石がすぐにあります」
                「人が5メートル先から近づいています」
                「低い枝が2メートル先にあります」
                
                この画像を分析し、最も重要なナビゲーション指示のみで応答してください
            """.trimIndent()
            TTSService.LANGUAGE_THAI -> """
                คุณคือผู้ช่วยนำทางสำหรับคนตาบอด วิเคราะห์ภาพนี้และให้เฉพาะข้อมูลการนำทางที่สำคัญเท่านั้น
                
                กฎ:
                กระชับมาก - สูงสุด 15 คำต่อคำแนะนำ
                เน้นเฉพาะเส้นทางข้างหน้า (0-10 เมตร)
                ให้ความสำคัญกับสิ่งกีดขวางและอันตราย
                ใช้ระยะทางง่ายๆ: "2 เมตร", "5 เมตร", "ทันที"
                กล่าวถึงเฉพาะสิ่งที่ส่งผลต่อเส้นทางเดิน
                
                รูปแบบการตอบกลับที่คาดหวัง:
                "สิ่งกีดขวางอยู่ข้างหน้า 3 เมตร"
                "เส้นทางข้างหน้าโล่ง"
                "เลี้ยวซ้ายได้"
                "ระวัง ขอบทางอยู่ข้างหน้าทันที"
                "คนกำลังเดินมาข้างหน้า 5 เมตร"
                "กิ่งไม้ต่ำอยู่ข้างหน้า 2 เมตร"
                
                วิเคราะห์ภาพนี้และตอบกลับด้วยคำแนะนำการนำทางที่สำคัญที่สุดเท่านั้น
            """.trimIndent()
            else -> """
                You are a navigation assistant for blind people. Analyze this image and provide ONLY critical navigation information.
                
                Rules:
                1. Be extremely concise - maximum 15 words per instruction
                2. Focus only on immediate path (0-10 meters ahead)
                3. Prioritize obstacles and hazards
                4. Use simple distance: "2 meters", "5 meters", "immediately"
                5. Mention only what affects walking path
                
                Expected responses format:
                - "Obstacle 3 meters ahead"
                - "Clear path ahead"
                - "Turn left available"
                - "Careful, curb immediately ahead"
                - "Person approaching 5 meters"
                - "Low branch 2 meters ahead"
                
                Analyze this image and respond with ONLY the most important navigation instruction
                """.trimIndent()
        }
    }

    fun shutdown() {
        llmInference?.close()
        llmInference = null
        isInitialized = false
    }
}
