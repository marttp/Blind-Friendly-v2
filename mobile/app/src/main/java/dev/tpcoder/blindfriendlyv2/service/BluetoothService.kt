package dev.tpcoder.blindfriendlyv2.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.annotation.RequiresPermission
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothService(private val context: Context) {
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var isListening = false

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startListening(onMessageReceived: (String) -> Unit) {
        if (isListening) return
        isListening = true
        
        thread {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    "BlindFriendlyV2",
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                clientSocket = serverSocket?.accept()
                val inputStream = clientSocket?.inputStream
                val buffer = ByteArray(1024)

                while (true) {
                    val bytes = inputStream?.read(buffer) ?: break
                    val message = String(buffer, 0, bytes)
                    
                    // Handle different tap patterns from the smartwatch
                    when (message) {
                        "TAP" -> {
                            // Single tap from smartwatch - as shown in mockup "Smart watch 1 tap"
                            onMessageReceived("TAP")
                        }
                        "DOUBLE_TAP" -> {
                            // Double tap from smartwatch
                            onMessageReceived("DOUBLE_TAP")
                        }
                        else -> {
                            // Other messages
                            onMessageReceived(message)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isListening = false
            }
        }
    }

    fun sendMessage(message: String) {
        thread {
            try {
                clientSocket?.outputStream?.write(message.toByteArray())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Simulate a tap from the smartwatch (for testing without actual hardware)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun simulateSmartWatchTap() {
        thread {
            // Simulate receiving a TAP message
            Thread.sleep(500) // Small delay to make it feel more realistic
            startListening { message ->
                // This will be called with the simulated message
            }
        }
    }
}