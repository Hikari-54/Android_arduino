package com.example.bluetooth_andr11

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.permissions.PermissionHelper
import com.example.bluetooth_andr11.ui.AppTopBar
import com.example.bluetooth_andr11.ui.control.ControlPanel
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var locationManager: LocationManager


    private val allPermissionsGranted = mutableStateOf(false)
    private val batteryPercent = mutableStateOf(0) // Информация о батарее
    private val isBluetoothConnected = mutableStateOf(false) // Состояние подключения Bluetooth
    private val coordinates = mutableStateOf("Неизвестно") // Координаты
    private val temp1 = mutableStateOf("--") // Температура 1
    private val temp2 = mutableStateOf("--") // Температура 2
    private val hallState = mutableStateOf("--") // Состояние датчика Холла
    private val functionState = mutableStateOf("--") // Функциональное состояние
    private val accelerometerData = mutableStateOf("--") // Данные акселерометра


    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            allPermissionsGranted.value = allGranted

            if (allGranted) {
                Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
                initializeAppFeatures()
            } else {
                handlePermissionsDenial(permissions)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothHelper = BluetoothHelper(this)
        permissionHelper = PermissionHelper(this, requestPermissionsLauncher)
        locationManager = LocationManager(
            context = this,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        )

        // Проверяем наличие всех необходимых разрешений
        allPermissionsGranted.value = permissionHelper.hasAllPermissions()
        if (allPermissionsGranted.value) {
            initializeAppFeatures()
        } else {
            permissionHelper.requestPermissions()
        }

        setContent {
            Bluetooth_andr11Theme {
                Scaffold(
                    topBar = {
                        AppTopBar(
                            batteryLevel = batteryPercent.value,
                            isBluetoothConnected = isBluetoothConnected.value,
                            allPermissionsGranted = allPermissionsGranted.value,
                            onPermissionsClick = ::handlePermissionsIconClick,
                            onBluetoothClick = ::handleConnectToDevice
                        )
                    },
                    content = { innerPadding ->
                        BluetoothScreen(
                            modifier = Modifier.padding(innerPadding),
                            onCommandSend = ::sendCommandToDevice,
                            temp1 = temp1.value,
                            temp2 = temp2.value,
                            hallState = hallState.value,
//                            functionState = functionState.value,
                            coordinates = coordinates.value,
//                            acc = accelerometerData.value
                        )
                    }
                )
            }
        }
    }

    private fun initializeAppFeatures() {
        // Запуск обновления местоположения
        locationManager.startLocationUpdates { newCoordinates ->
            coordinates.value = newCoordinates
        }
    }

    private fun handlePermissionsDenial(permissions: Map<String, Boolean>) {
        val permanentlyDeniedPermissions = permissions.filter { permission ->
            !permission.value && !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                permission.key
            )
        }

        if (permanentlyDeniedPermissions.isNotEmpty()) {
            Toast.makeText(
                this,
                "Некоторые разрешения отклонены навсегда. Пожалуйста, предоставьте их в настройках приложения.",
                Toast.LENGTH_LONG
            ).show()
            redirectToAppSettings()
        } else {
            showPermissionsRationale()
        }
    }

    private fun handlePermissionsIconClick() {
        if (!allPermissionsGranted.value) {
            permissionHelper.requestPermissions()
        } else {
            Toast.makeText(this, "Все необходимые разрешения уже предоставлены", Toast.LENGTH_SHORT)
                .show()
        }
    }


    private fun showPermissionsRationale() {
        val missingPermissions = permissionHelper.getMissingPermissions()
        val shouldShowRationale = missingPermissions.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        if (shouldShowRationale) {
            // Показываем объяснение и повторно запрашиваем разрешения
            Toast.makeText(
                this,
                "Для работы приложения необходимо предоставить все разрешения.",
                Toast.LENGTH_LONG
            ).show()
            permissionHelper.requestPermissions()
        } else {
            // Если пользователь выбрал "Не спрашивать снова", показываем инструкцию
            Toast.makeText(
                this,
                "Перейдите в настройки приложения и предоставьте разрешения вручную.",
                Toast.LENGTH_LONG
            ).show()
            // Опционально: перенаправить в настройки приложения
            redirectToAppSettings()
        }
    }

    private fun redirectToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun handleConnectToDevice() {
        bluetoothHelper.showDeviceSelectionDialog(this) { device ->
            bluetoothHelper.connectToDevice(device) { success, message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                isBluetoothConnected.value = success
                if (success) {
                    bluetoothHelper.listenForData { data ->
                        handleReceivedData(data)
                    }
                }
            }
        }
    }

    private fun handleReceivedData(data: String) {
        parseArduinoData(data)
    }

    private fun parseArduinoData(data: String) {
        try {
            val parts = data.split(",")
            if (parts.size == 6) {
                batteryPercent.value = parts[0].trim().toIntOrNull() ?: 0
                temp1.value = parts[1].trim()
                temp2.value = parts[2].trim()
                hallState.value = when (parts[3].trim()) {
                    "1" -> "Открыт"
                    "0" -> "Закрыт"
                    else -> "Неизвестно"
                }
                functionState.value = parts[4].trim()
                accelerometerData.value = parts[5].trim()
            } else {
                Toast.makeText(
                    this,
                    "Некорректный формат данных: $data",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Ошибка парсинга данных: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun sendCommandToDevice(command: String) {
        bluetoothHelper.sendCommand(command)
    }

//    private fun handleRequestPermissions() {
//        if (!allPermissionsGranted.value) {
//            permissionHelper.requestPermissions()
//        } else {
//            Toast.makeText(this, "Все разрешения уже предоставлены", Toast.LENGTH_SHORT).show()
//        }
//    }

//    private fun handleCheckBluetooth() {
//        if (!bluetoothHelper.isBluetoothEnabled()) {
//            Toast.makeText(this, "Bluetooth выключен", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            if (!permissionHelper.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
//                Toast.makeText(this, "Разрешение BLUETOOTH_CONNECT отсутствует", Toast.LENGTH_SHORT)
//                    .show()
//                permissionHelper.requestSpecificPermission(Manifest.permission.BLUETOOTH_CONNECT)
//                return
//            }
//        } else {
//            // Для версий ниже API 31 ничего делать не нужно, так как это разрешение отсутствует
//            Toast.makeText(
//                this,
//                "BLUETOOTH_CONNECT не требуется для этой версии Android",
//                Toast.LENGTH_SHORT
//            )
//                .show()
//        }
//
//
//        bluetoothHelper.showDeviceSelectionDialog(this) { device ->
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
//                ActivityCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.BLUETOOTH_CONNECT
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                // Проверяем разрешение BLUETOOTH_CONNECT
//                if (!permissionHelper.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
//                    permissionHelper.requestSpecificPermission(Manifest.permission.BLUETOOTH_CONNECT)
//                }
//                Toast.makeText(this, "Разрешение BLUETOOTH_CONNECT отсутствует", Toast.LENGTH_SHORT)
//                    .show()
//                return@showDeviceSelectionDialog // Выходим из блока, если разрешение отсутствует
//            }
//
//            // Устройство выбрано, выводим его имя
//            Toast.makeText(
//                this,
//                "Выбрано устройство: ${device.name ?: "Unknown"}",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//    }
}

@Composable
fun BluetoothScreen(
    modifier: Modifier = Modifier,
    onCommandSend: (String) -> Unit,
    temp1: String,
    temp2: String,
    hallState: String,
//    functionState: String,
    coordinates: String,
//    acc: String
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Панель управления
        ControlPanel(
            onCommandSend = onCommandSend,
            temp1 = temp1,
            temp2 = temp2,
            hallState = hallState,
//            functionState = functionState,
            coordinates = coordinates,
//            acc = acc,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ControlPanelPreview() {
    Bluetooth_andr11Theme {
        Scaffold(
            topBar = {
                AppTopBar(
                    batteryLevel = 75, // Пример уровня заряда батареи
                    isBluetoothConnected = true, // Пример состояния подключения Bluetooth
                    allPermissionsGranted = true, // Пример состояния разрешений
                    onPermissionsClick = { /* Нажатие на иконку разрешений */ },
                    onBluetoothClick = { /* Нажатие на иконку Bluetooth */ }
                )
            },
            content = { innerPadding ->
                ControlPanel(
                    onCommandSend = { /* Никакой команды, просто для просмотра */ },
                    temp1 = "22",
                    temp2 = "19",
                    hallState = "Закрыто",
                    coordinates = "55.751244, 37.618423",
                    modifier = Modifier.padding(innerPadding) // Учитываем отступы от TopBar
                )
            }
        )
    }
}
