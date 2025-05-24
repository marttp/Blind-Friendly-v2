package dev.tpcoder.blindfriendlyv2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import dev.tpcoder.blindfriendlyv2.screens.LoadingScreen
import dev.tpcoder.blindfriendlyv2.screens.NavigateScreen
import dev.tpcoder.blindfriendlyv2.screens.NavigateViewModel
import dev.tpcoder.blindfriendlyv2.ui.theme.BlindFriendlyV2Theme

class MainActivity : ComponentActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 101
    }

    private lateinit var viewModel: NavigateViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[NavigateViewModel::class.java]
        setContent {
            BlindFriendlyV2Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Collect the model state to determine which screen to show
                    val modelState by viewModel.modelState.collectAsState()
                    
                    if (modelState.isLoading || !modelState.isInitialized) {
                        // Show loading screen while model is initializing
                        LoadingScreen(
                            message = if (modelState.isLoading) "Initializing AI Model..." else "Waiting for model initialization...",
                            error = modelState.error
                        )
                    } else {
                        // Show main navigation screen once model is initialized
                        NavigateScreen(viewModel)
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Check Bluetooth permissions (API 31+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }
}