package com.example.bluetooth_andr11

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var permissionHelper: PermissionHelper

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            Toast.makeText(
                this,
                if (allGranted) "All permissions granted" else "Permissions denied",
                Toast.LENGTH_SHORT
            ).show()
        }

    private val receivedData = mutableStateOf("")
    private val batteryPercent = mutableStateOf("0")
    private val temp1 = mutableStateOf("0")
    private val temp2 = mutableStateOf("0")
    private val hallState = mutableStateOf("Unknown")
    private val functionState = mutableStateOf("Inactive")
    private val coordinates = mutableStateOf("0, 0")
    private val accelerometerData = mutableStateOf("0, 0, 0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothHelper = BluetoothHelper(this)
        permissionHelper = PermissionHelper(this, requestPermissionsLauncher)

        setContent {
            Bluetooth_andr11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermissions = ::handleRequestPermissions,
                        onCheckBluetooth = ::handleCheckBluetooth,
                        onConnectToDevice = ::handleConnectToDevice,
                        receivedData = receivedData.value,
                        onCommandSend = ::sendCommandToDevice,
                        batteryPercent = batteryPercent.value,
                        temp1 = temp1.value,
                        temp2 = temp2.value,
                        hallState = hallState.value,
                        functionState = functionState.value,
                        coordinates = coordinates.value,
                        acc = accelerometerData.value
                    )
                }
            }
        }
    }

    private fun handleRequestPermissions() {
        if (!permissionHelper.hasAllPermissions()) {
            permissionHelper.requestPermissions()
        } else {
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCheckBluetooth() {
        // Проверяем разрешение BLUETOOTH_CONNECT для Android 12+ (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !permissionHelper.hasPermission("android.permission.BLUETOOTH_CONNECT")
        ) {
            Toast.makeText(this, "Bluetooth CONNECT permission is missing", Toast.LENGTH_SHORT)
                .show()
            return
        }

        if (!bluetoothHelper.isBluetoothEnabled()) {
            Toast.makeText(this, "Bluetooth is disabled", Toast.LENGTH_SHORT).show()
        } else {
            val pairedDevices = bluetoothHelper.getPairedDevices()
            val deviceNames = try {
                pairedDevices?.joinToString(", ") { device ->
                    // Безопасно получаем имя устройства
                    device.name ?: "Unknown"
                }
            } catch (e: SecurityException) {
                Toast.makeText(
                    this,
                    "Permission denied for accessing device names",
                    Toast.LENGTH_SHORT
                ).show()
                null
            }

            Toast.makeText(this, deviceNames ?: "No paired devices found", Toast.LENGTH_SHORT)
                .show()
        }
    }


    private fun handleConnectToDevice() {
        val pairedDevices = bluetoothHelper.getPairedDevices()
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show()
            return
        }

        val device = pairedDevices.first()
        bluetoothHelper.connectToDevice(device) { success, message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            if (success) {
                bluetoothHelper.listenForData { data ->
                    receivedData.value = data
                    parseReceivedData(data)
                }
            }
        }
    }

    private fun sendCommandToDevice(command: String) {
        bluetoothHelper.sendCommand(command)
    }

    private fun parseReceivedData(data: String) {
        val parts = data.split(",")
        if (parts.size >= 6) {
            batteryPercent.value = parts[0].trim()
            temp1.value = parts[1].trim()
            temp2.value = parts[2].trim()
            hallState.value = parts[3].trim()
            functionState.value = parts[4].trim()
            accelerometerData.value = parts[5].trim()
        }
    }
}

@Composable
fun BluetoothScreen(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit,
    onCheckBluetooth: () -> Unit,
    onConnectToDevice: () -> Unit,
    receivedData: String,
    onCommandSend: (String) -> Unit,
    batteryPercent: String,
    temp1: String,
    temp2: String,
    hallState: String,
    functionState: String,
    coordinates: String,
    acc: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onRequestPermissions) {
            Text("Request Permissions")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCheckBluetooth) {
            Text("Check Bluetooth")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onConnectToDevice) {
            Text("Connect to Device")
        }
        Spacer(modifier = Modifier.height(16.dp))

        ControlPanel(
            onCommandSend = onCommandSend,
            batteryPercent = batteryPercent,
            temp1 = temp1,
            temp2 = temp2,
            hallState = hallState,
            functionState = functionState,
            coordinates = coordinates,
            acc = acc,
            responseMessage = receivedData
        )
    }
}


@Composable
fun ControlPanel(
    onCommandSend: (String) -> Unit,
    batteryPercent: String,
    temp1: String,
    temp2: String,
    hallState: String,
    functionState: String,
    coordinates: String,
    acc: String,
    responseMessage: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(text = "Battery: $batteryPercent%", modifier = Modifier.padding(4.dp))
        Text(text = "Temperature 1: $temp1°C", modifier = Modifier.padding(4.dp))
        Text(text = "Temperature 2: $temp2°C", modifier = Modifier.padding(4.dp))
        Text(text = "Hall Sensor: $hallState", modifier = Modifier.padding(4.dp))
        Text(text = "Function State: $functionState", modifier = Modifier.padding(4.dp))
        Text(text = "Coordinates: $coordinates", modifier = Modifier.padding(4.dp))
        Text(text = "Accelerometer: $acc", modifier = Modifier.padding(4.dp))
        Text(text = "Last Message: $responseMessage", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = { onCommandSend("H") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Heat On") }

            Button(
                onClick = { onCommandSend("h") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) { Text("Heat Off") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = { onCommandSend("C") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
            ) { Text("Cool On") }

            Button(
                onClick = { onCommandSend("c") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) { Text("Cool Off") }
        }
    }
}
