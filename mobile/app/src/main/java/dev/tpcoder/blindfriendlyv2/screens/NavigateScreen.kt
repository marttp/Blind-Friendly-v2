package dev.tpcoder.blindfriendlyv2.screens

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.tpcoder.blindfriendlyv2.service.TTSService

@Composable
fun NavigateScreen(viewModel: NavigateViewModel) {

    val uiState = viewModel.uiState.collectAsState().value
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    viewModel.bindCameraPreview(this, lifecycleOwner)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.performScan()
                        }
                    )
                }
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Blue.copy(alpha = 0.5f))
                .fillMaxHeight(0.15f)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { viewModel.performScan() },
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tap to check obstacles",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = uiState.statusText,
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            invisibleToUser()
                        }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { viewModel.toggleLanguage() },
                            modifier = Modifier
                                .background(Color.Red.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change language",
                                tint = Color.White
                            )
                        }

                        Text(
                            text = when (viewModel.getCurrentLanguage()) {
                                TTSService.LANGUAGE_JAPANESE -> "日本語"
                                TTSService.LANGUAGE_THAI -> "ไทย"
                                else -> "English"
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .semantics {
                                    invisibleToUser()
                                }
                        )
                    }
                }

            }
        }
    }
}