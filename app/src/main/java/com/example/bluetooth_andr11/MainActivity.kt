package com.example.bluetooth_andr11

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.log.LogFilterScreen
import com.example.bluetooth_andr11.permissions.PermissionHelper
import com.example.bluetooth_andr11.ui.control.ControlPanel
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var locationManager: LocationManager

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            Toast.makeText(
                this,
                if (allGranted) "Все разрешения предоставлены" else "Разрешения отклонены",
                Toast.LENGTH_SHORT
            ).show()
        }

    private val receivedData = mutableStateOf("")
    private val batteryPercent = mutableStateOf("0")
    private val temp1 = mutableStateOf("0")
    private val temp2 = mutableStateOf("0")
    private val hallState = mutableStateOf("Unknown")
    private val functionState = mutableStateOf("Inactive")
    private val coordinates = mutableStateOf("Неизвестно")
    private val accelerometerData = mutableStateOf("0, 0, 0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothHelper = BluetoothHelper(this)
        permissionHelper = PermissionHelper(this, requestPermissionsLauncher)
        locationManager = LocationManager(
            context = this,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        )

        locationManager.startLocationUpdates()
        coordinates.value = locationManager.getLocationCoordinates()

        setContent {
            Bluetooth_andr11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermissions = ::handleRequestPermissions,
                        onCheckBluetooth = ::handleCheckBluetooth,
                        onConnectToDevice = ::handleConnectToDevice,
                        onStartLocationUpdates = { locationManager.startLocationUpdates() },
                        onStopLocationUpdates = { locationManager.stopLocationUpdates() },
                        receivedData = receivedData.value,
                        onCommandSend = ::sendCommandToDevice,
                        batteryPercent = batteryPercent.value,
                        temp1 = temp1.value,
                        temp2 = temp2.value,
                        hallState = hallState.value,
                        functionState = functionState.value,
                        coordinates = locationManager.getLocationCoordinates(),
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
            Toast.makeText(this, "Все разрешения уже предоставлены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCheckBluetooth() {
        if (!bluetoothHelper.isBluetoothEnabled()) {
            Toast.makeText(this, "Bluetooth выключен", Toast.LENGTH_SHORT).show()
            return
        }

        // Проверка разрешения BLUETOOTH_CONNECT
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!permissionHelper.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissionHelper.requestPermissions()
            }
            Toast.makeText(this, "Разрешение BLUETOOTH_CONNECT отсутствует", Toast.LENGTH_SHORT)
                .show()
            return
        }

        bluetoothHelper.showDeviceSelectionDialog(this) { device ->
            Toast.makeText(
                this,
                "Выбрано устройство: ${device.name ?: "Unknown"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun handleConnectToDevice() {
        bluetoothHelper.showDeviceSelectionDialog(this) { device ->
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
    onStartLocationUpdates: () -> Unit,
    onStopLocationUpdates: () -> Unit,
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
            Text("Запросить разрешения")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onCheckBluetooth) {
            Text("Проверить Bluetooth")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onConnectToDevice) {
            Text("Выбрать устройство")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStartLocationUpdates) {
            Text("Запустить обновление местоположения")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStopLocationUpdates) {
            Text("Остановить обновление местоположения")
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

        Spacer(modifier = Modifier.height(16.dp))
        val context = LocalContext.current
        LogFilterScreen { startDate, endDate ->
            Toast.makeText(context, "Фильтр: с $startDate по $endDate", Toast.LENGTH_SHORT).show()
        }
    }
}
