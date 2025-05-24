package dev.tpcoder.blindfriendlywearos.presentation

import android.app.Application
import android.content.ContentValues.TAG
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tpcoder.blindfriendlywearos.presentation.service.BluetoothService
import dev.tpcoder.blindfriendlywearos.presentation.service.HapticService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothService = BluetoothService(application)
    private val hapticService = HapticService(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    
    // Track if we've already sent a scan request that hasn't been responded to
    private var scanRequestSent = false

    init {
        setupBluetoothConnection()
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupBluetoothConnection() {
        viewModelScope.launch {
            tryConnectBluetooth()
        }
    }
    
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun tryConnectBluetooth() {
        // Update UI to show we're trying to connect
        _uiState.update { it.copy(isConnecting = true) }
        
        bluetoothService.startConnection(
            onConnected = {
                _uiState.update { 
                    it.copy(
                        isConnected = true, 
                        isConnecting = false
                    ) 
                }
                hapticService.vibrateConnected()
                // Reset scan request flag when connection is established
                scanRequestSent = false
                
                Log.d("MainViewModel", "Bluetooth connected")
            },
            onDisconnected = {
                _uiState.update { 
                    it.copy(
                        isConnected = false,
                        isConnecting = false
                    ) 
                }
                // Reset scan request flag when disconnected
                scanRequestSent = false
                
                Log.d("MainViewModel", "Bluetooth disconnected")
            },
            onMessageReceived = { message ->
                Log.d("MainViewModel", "Message received: $message")
                handlePhoneMessage(message)
                // Reset scan request flag when any message is received
                scanRequestSent = false
            }
        )
    }

    /**
     * Checks the current Bluetooth connection status when the app is brought back to the foreground.
     * If the connection is lost, it will attempt to reconnect.
     */
    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun checkConnection() {
        Log.d("MainViewModel", "Checking Bluetooth connection status")
        
        // If we're already showing as connected, verify that the connection is still active
        if (_uiState.value.isConnected && !bluetoothService.isConnectionActive()) {
            Log.d("MainViewModel", "Connection lost while app was in background, reconnecting...")
            _uiState.update { 
                it.copy(
                    isConnected = false,
                    isConnecting = true
                ) 
            }
            
            // Try to reconnect
            viewModelScope.launch {
                tryConnectBluetooth()
            }
        } 
        // If we're showing as disconnected but the connection is actually active, update UI
        else if (!_uiState.value.isConnected && bluetoothService.isConnectionActive()) {
            Log.d("MainViewModel", "Connection is active but UI shows disconnected, updating UI")
            _uiState.update { 
                it.copy(
                    isConnected = true,
                    isConnecting = false
                ) 
            }
        }
        // If we're in a connecting state but not actively trying to connect, reset the state
        else if (_uiState.value.isConnecting && !_uiState.value.isConnected) {
            Log.d("MainViewModel", "App returned to foreground while in connecting state, retrying connection")
            viewModelScope.launch {
                tryConnectBluetooth()
            }
        }
    }

    fun onTapScreen() {
        if (!_uiState.value.isConnected || _uiState.value.isScanning) return

        if (!scanRequestSent) {
            viewModelScope.launch {
                _uiState.update { it.copy(isScanning = true) }
                hapticService.vibrateTap()

                bluetoothService.sendMessage("SCAN_REQUEST")
                scanRequestSent = true
                
                Log.d("MainViewModel", "Scan request sent")
            }
        } else {
            Log.d("MainViewModel", "Ignoring tap - scan request already sent and waiting for response")
        }
    }

    private fun handlePhoneMessage(message: String) {
        viewModelScope.launch {
            // Message format: "PATTERN|Text"
            val parts = message.split("|", limit = 2)

            if (parts.size >= 2) {
                val pattern = parts[0].toIntOrNull() ?: 2
                val text = parts[1]
                
                // Update UI
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        detectedText = text
                    ) 
                }
                
                // Vibrate according to pattern
                hapticService.vibratePattern(pattern)
                
                Log.d("MainViewModel", "Processed message: Pattern=$pattern, Text=$text")
            } else {
                // If message doesn't match expected format, just stop scanning
                _uiState.update { it.copy(isScanning = false) }
                Log.d("MainViewModel", "Received malformed message: $message")
            }
        }
    }
    
    // Function to manually reconnect if needed
    fun reconnect() {
        viewModelScope.launch {
            tryConnectBluetooth()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService.disconnect()
    }
}

data class UiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isScanning: Boolean = false,
    val detectedText: String = ""
)