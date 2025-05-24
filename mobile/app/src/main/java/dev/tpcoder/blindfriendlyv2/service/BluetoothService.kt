package dev.tpcoder.blindfriendlyv2.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothService(private val context: Context) {
    companion object {
        private const val NAME = "BlindFriendlyPhone"
        private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startServer(onMessageReceived: (String) -> Unit) {
        thread {
            try {
                // Make device discoverable
                bluetoothAdapter?.name = NAME

                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    NAME, UUID_SPP
                )

                // Accept connection
                clientSocket = serverSocket?.accept()

                // Read messages
                val inputStream = clientSocket?.inputStream
                val buffer = ByteArray(1024)

                while (true) {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        if (message == "SCAN_REQUEST") {
                            onMessageReceived(message)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendToWatch(pattern: Int, message: String) {
        thread {
            try {
                // Check if socket is connected before attempting to send
                if (clientSocket == null || !clientSocket!!.isConnected) {
                    Log.e(TAG, "Cannot send to watch: Socket is not connected")
                    // Attempt to reconnect - you may want to implement a reconnection strategy here
                    return@thread
                }
                
                val formattedMessage = "$pattern|$message"
                try {
                    clientSocket?.outputStream?.write(formattedMessage.toByteArray())
                    clientSocket?.outputStream?.flush() // Ensure data is sent immediately
                    Log.d(TAG, "Message sent to watch: $formattedMessage")
                } catch (e: java.io.IOException) {
                    Log.e(TAG, "Error sending message to watch", e)
                    // Handle broken pipe or other IO exceptions
                    if (e.message?.contains("Broken pipe") == true) {
                        // The connection was lost, close the socket to clean up resources
                        closeConnection()
                    }
                    throw e // Re-throw to be caught by outer catch block
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message to watch", e)
                e.printStackTrace()
            }
        }
    }
    
    // Add method to close the connection properly
    fun closeConnection() {
        try {
            clientSocket?.close()
            clientSocket = null
            serverSocket?.close()
            serverSocket = null
            Log.d(TAG, "Bluetooth connection closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Bluetooth connection", e)
            e.printStackTrace()
        }
    }
}