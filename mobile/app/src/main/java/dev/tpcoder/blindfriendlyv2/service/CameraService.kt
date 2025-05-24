package dev.tpcoder.blindfriendlyv2.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraService(private val context: Context) {
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraProvider: ProcessCameraProvider
    
    // Add an image analyzer for faster image processing
    private lateinit var imageAnalysis: ImageAnalysis
    
    // Cache the last captured bitmap to avoid redundant processing
    private var lastCapturedBitmap: Bitmap? = null
    private val imageAnalysisExecutor = Executors.newSingleThreadExecutor()
    
    // Flag to track if we're currently capturing
    private var isCapturing = false

    fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Configure preview for faster rendering
            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Configure image capture for faster capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(previewView.display.rotation)
                .build()
                
            // Configure image analysis for real-time processing
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(previewView.display.rotation)
                .build()
                
            imageAnalysis.setAnalyzer(imageAnalysisExecutor) { imageProxy ->
                if (!isCapturing) {
                    // Only process if we're not already capturing
                    processImageProxy(imageProxy)
                } else {
                    // Just close the image if we're already capturing
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun processImageProxy(imageProxy: ImageProxy) {
        // Don't process every frame to save resources
        if (Math.random() > 0.2) { // Process roughly 20% of frames
            imageProxy.close()
            return
        }
        
        val bitmap = imageProxy.toBitmap()
        lastCapturedBitmap = bitmap
        imageProxy.close()
    }

    suspend fun captureImage(): Bitmap = suspendCoroutine { continuation ->
        // If we have a recent cached image, use it for faster response
        lastCapturedBitmap?.let {
            if (Math.random() > 0.3) { // 70% chance to use cached image for faster response
                continuation.resume(it)
                return@suspendCoroutine
            }
        }
        
        isCapturing = true
        val outputFile = File(context.cacheDir, "temp.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        // Load the image and correct rotation
                        val options = BitmapFactory.Options().apply {
                            // Decode with inSampleSize for faster loading if needed
                            inSampleSize = 1 // Set to higher value (2 or 4) for even faster loading but lower quality
                        }
                        
                        val bitmap = BitmapFactory.decodeFile(outputFile.path, options)
                        
                        // Store in cache for future use
                        lastCapturedBitmap = bitmap
                        isCapturing = false
                        continuation.resume(bitmap)
                    } catch (e: Exception) {
                        Log.e("CameraService", "Error processing captured image: ${e.message}")
                        isCapturing = false
                        continuation.resumeWithException(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraService", "Error capturing image: ${exception.message}")
                    isCapturing = false
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
    
    fun shutdown() {
        imageAnalysisExecutor.shutdown()
        lastCapturedBitmap = null
    }
}