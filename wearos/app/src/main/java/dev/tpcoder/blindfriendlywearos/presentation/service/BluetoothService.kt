package dev.tpcoder.blindfriendlywearos.presentation.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val context: Context) {
    companion object {
        private const val TAG = "BluetoothService"
        private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEVICE_NAME_PREFIX = "BlindFriendly"
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readingJob: Job? = null
    private var lastConnectedDevice: BluetoothDevice? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun startConnection(
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onMessageReceived: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            readingJob?.cancel()

            val pairedDevice = findPairedDevice()
            if (pairedDevice == null) {
                Log.e(TAG, "No paired BlindFriendly device found")
                onDisconnected()
                return@withContext
            }

            lastConnectedDevice = pairedDevice

            connectToDevice(pairedDevice)

            withContext(Dispatchers.Main) {
                onConnected()
            }

            readingJob = serviceScope.launch {
                readMessages(
                    onMessageReceived = onMessageReceived,
                    onDisconnected = {
                        onDisconnected()
                    }
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            withContext(Dispatchers.Main) {
                onDisconnected()
            }
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun findPairedDevice(): BluetoothDevice? {
        val pairedDevices = bluetoothAdapter?.bondedDevices ?: return null

        pairedDevices.firstOrNull { device ->
            device.name?.startsWith(DEVICE_NAME_PREFIX) == true
        }?.let { return it }

        return pairedDevices.firstOrNull { device ->
            device.name?.contains("Phone", ignoreCase = true) == true ||
                    device.name?.contains("Galaxy", ignoreCase = true) == true ||
                    device.name?.contains("Pixel", ignoreCase = true) == true
        }
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun connectToDevice(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        try {
            closeConnection()

            socket = device.createRfcommSocketToServiceRecord(UUID_SPP)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            Log.d(TAG, "Connected to ${device.name}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to connect to ${device.name}", e)
            throw e
        }
    }

    private suspend fun readMessages(
        onMessageReceived: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
        val buffer = ByteArray(1024)

        try {
            while (isActive) {
                val bytes = inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val message = String(buffer, 0, bytes)
                    withContext(Dispatchers.Main) {
                        onMessageReceived(message)
                    }
                } else if (bytes == -1) {
                    Log.d(TAG, "End of stream reached, connection lost")
                    break
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading message", e)
        } finally {
            closeConnection()
            onDisconnected()
        }
    }

    suspend fun sendMessage(message: String) = withContext(Dispatchers.IO) {
        try {
            if (outputStream == null) {
                Log.e(TAG, "Cannot send message, output stream is null")
                return@withContext
            }

            outputStream?.write(message.toByteArray())
            outputStream?.flush()
            Log.d(TAG, "Sent: $message")
        } catch (e: IOException) {
            Log.e(TAG, "Error sending message", e)
        }
    }

    private fun closeConnection() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        } finally {
            socket = null
            inputStream = null
            outputStream = null
        }
    }

    fun disconnect() {
        readingJob?.cancel()
        closeConnection()
    }

    fun isConnectionActive(): Boolean {
        val isSocketConnected = socket?.isConnected ?: false
        val isStreamAvailable = inputStream != null && outputStream != null

        Log.d(TAG, "Connection status: Socket connected = $isSocketConnected, Streams available = $isStreamAvailable")

        return isSocketConnected && isStreamAvailable
    }

    private val isActive: Boolean
        get() = !serviceScope.isActive.not()
}