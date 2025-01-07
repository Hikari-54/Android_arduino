package com.example.bluetooth_andr11.bluetooth

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothHelper(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var isListening = false // Prevent duplicate coroutine starts

    // Check if Bluetooth is enabled
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    // Get paired devices
    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (!hasBluetoothPermission()) {
            Log.e("BluetoothHelper", "Bluetooth permissions are missing")
            return null
        }
        return try {
            bluetoothAdapter?.bondedDevices
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Permission error when accessing paired devices: ${e.message}")
            null
        }
    }

    // Display a device selection dialog
    fun showDeviceSelectionDialog(context: Context, onDeviceSelected: (BluetoothDevice) -> Unit) {
        val pairedDevices = getPairedDevices()
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "No paired devices found", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = pairedDevices.map {
            try {
                it.name ?: "Unknown"
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothHelper", "Permission error when accessing device name: ${e.message}"
                )
                "Unknown"
            }
        }

        val deviceList = pairedDevices.toList()

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select a Bluetooth Device")
        builder.setItems(deviceNames.toTypedArray()) { _, which ->
            onDeviceSelected(deviceList[which])
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    // Connect to a Bluetooth device
    fun connectToDevice(device: BluetoothDevice, onConnectionResult: (Boolean, String) -> Unit) {
        if (!hasBluetoothPermission()) {
            onConnectionResult(false, "Bluetooth permissions are missing")
            return
        }

        val uuid = try {
            device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Permission error when accessing UUID: ${e.message}")
            onConnectionResult(false, "Permission error when accessing UUID")
            return
        }

        try {
            bluetoothSocket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: SecurityException) {
                Log.e("BluetoothHelper", "Permission error when creating socket: ${e.message}")
                onConnectionResult(false, "Permission error when creating socket")
                return
            }

            bluetoothSocket?.connect()

            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            isConnected = true

            val deviceName = try {
                device.name ?: "Unknown"
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothHelper", "Permission error when accessing device name: ${e.message}"
                )
                "Unknown"
            }

            onConnectionResult(true, "Connected to $deviceName")
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Permission error when connecting: ${e.message}")
            onConnectionResult(false, "Permission error when connecting")
            closeConnection()
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Connection error: ${e.message}")
            onConnectionResult(false, "Connection error: ${e.message}")
            closeConnection()
        }
    }

    // Send command to connected device
    fun sendCommand(command: String) {
        if (!isConnected || outputStream == null) {
            Log.e("BluetoothHelper", "Not connected or output stream unavailable")
            return
        }

        try {
            outputStream?.write(command.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Error sending command: ${e.message}")
        }
    }

    // Listen for incoming data from the connected device
    fun listenForData(onDataReceived: (String) -> Unit) {
        if (!isConnected || inputStream == null || isListening) {
            Log.e("BluetoothHelper", "Not connected or input stream unavailable")
            return
        }

        isListening = true
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (isConnected) {
                try {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        withContext(Dispatchers.Main) {
                            onDataReceived(data)
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("BluetoothHelper", "Permission error when reading data: ${e.message}")
                    break
                } catch (e: IOException) {
                    Log.e("BluetoothHelper", "Error reading data: ${e.message}")
                    break
                }
            }
            isListening = false
        }
    }

    // Close the Bluetooth connection
    fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
            isListening = false
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Error closing connection: ${e.message}")
        }
    }

    // Check Bluetooth permissions
    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = listOf(
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
            )
            return permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        return true // Permissions are not required for Bluetooth before Android 12
    }

    fun monitorConnectionStatus(onStatusChange: (Boolean) -> Unit) {
        bluetoothAdapter?.let { adapter ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                            val state =
                                intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                            onStatusChange(state == BluetoothAdapter.STATE_CONNECTED)
                        }
                    }
                }
            }

            val filter = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            context.registerReceiver(receiver, filter)
        }
    }
}
