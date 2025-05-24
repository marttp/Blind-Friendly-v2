package dev.tpcoder.blindfriendlywearos.presentation

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.tpcoder.blindfriendlywearos.R

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        timeText = {
            TimeText()
        },
        vignette = {
            Vignette(vignettePosition = VignettePosition.TopAndBottom)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                // Status indicator dot - green when connected, red otherwise
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                uiState.isConnected -> Color(0xFF1B5E20) // Green
                                else -> Color(0xFF880E4F) // Red
                            }
                        )
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { 
                        if (uiState.isConnected) {
                            viewModel.onTapScreen()
                        } else {
                            viewModel.reconnect()
                        }
                    },
                    modifier = Modifier
                        .size(120.dp)
                        .align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    ),
                    enabled = !uiState.isScanning // Always enable the button - either for scanning or reconnecting
                ) {
                    // Show different icons based on state
                    Icon(
                        painter = painterResource(
                            id = when {
                                !uiState.isConnected -> R.drawable.ic_clock
                                uiState.isScanning -> R.drawable.ic_clock
                                else -> R.drawable.ic_eye
                            }
                        ),
                        contentDescription = when {
                            !uiState.isConnected -> "Tap to connect"
                            uiState.isScanning -> "Waiting for response"
                            else -> "Tap to scan"
                        },
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                // Show connection status text
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        uiState.isConnecting -> "Connecting..."
                        !uiState.isConnected -> "Tap to connect"
                        uiState.isScanning -> "Scanning..."
                        else -> "Ready to scan"
                    },
                    color = Color.White,
                    fontSize = 12.sp
                )
                
                // Last detected text
                if (uiState.detectedText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = Color(0xFF424242)
                        ),
                        onClick = { }
                    ) {
                        Text(
                            text = uiState.detectedText,
                            modifier = Modifier.padding(12.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}