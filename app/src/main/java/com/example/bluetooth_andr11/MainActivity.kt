package com.example.bluetooth_andr11

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.permissions.PermissionHelper
import com.example.bluetooth_andr11.ui.control.AppTopBar
import com.example.bluetooth_andr11.ui.MainScreen
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import java.io.File
import kotlinx.coroutines.*

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

        // Настройка кэша для карт
        setupCachePath()

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

        // Эмулируем данные от Arduino
//        simulateArduinoData()
        simulateDebugLogs(this, locationManager)


        LogModule.logEventWithLocation(this, locationManager, "Сумка закрыта")

        setContent {
            Bluetooth_andr11Theme {
                Scaffold(topBar = {
                    AppTopBar(
                        batteryLevel = batteryPercent.value,
                        isBluetoothConnected = isBluetoothConnected.value,
                        allPermissionsGranted = allPermissionsGranted.value,
                        onPermissionsClick = ::handlePermissionsIconClick,
                        onBluetoothClick = ::handleConnectToDevice
                    )
                }, content = { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onCommandSend = ::sendCommandToDevice,
                        temp1 = temp1.value,
                        temp2 = temp2.value,
                        hallState = hallState.value,
//                            functionState = functionState.value,
                        coordinates = coordinates.value,
                        acc = accelerometerData.value
                    )
                })
            }
        }
    }

    private fun initializeAppFeatures() {
        // Запуск обновления местоположения
        locationManager.startLocationUpdates { newCoordinates ->
            coordinates.value = newCoordinates
        }
    }

    // Настройка кэша карт
    private fun setupCachePath() {
        val context = applicationContext

        // Проверяем версию Android и настраиваем путь к кэшу карт
        val cacheDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.filesDir, "osmdroid")
        } else {
            File(context.getExternalFilesDir(null), "osmdroid")
        }

        // Настраиваем кэш и пользовательский агент
        val config = Configuration.getInstance()
        config.osmdroidBasePath = cacheDir
        config.osmdroidTileCache = File(cacheDir, "cache")
        config.userAgentValue = packageName
    }

    private fun handlePermissionsDenial(permissions: Map<String, Boolean>) {
        val permanentlyDeniedPermissions = permissions.filter { permission ->
            !permission.value && !ActivityCompat.shouldShowRequestPermissionRationale(
                this, permission.key
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
                // Парсим и сохраняем данные
                batteryPercent.value = parts[0].trim().toIntOrNull() ?: 0
                temp1.value = parts[1].trim()
                temp2.value = parts[2].trim()
                hallState.value = when (parts[3].trim()) {
                    "1" -> {
                        LogModule.logEventWithLocation(this, locationManager, "Сумка закрыта")
                        "Закрыт"
                    }

                    "0" -> {
                        LogModule.logEventWithLocation(this, locationManager, "Сумка открыта")
                        "Открыт"
                    }

                    else -> "Неизвестно"
                }
                functionState.value = parts[4].trim()

                // Обрабатываем данные акселерометра
                val accelerometerValue = parts[5].trim().toFloatOrNull() ?: 0.0f

                // Классифицируем тряску с добавлением значения
                val shakeCategory = when {
                    accelerometerValue > 2.5 || accelerometerValue < -2.5 -> {
                        LogModule.logEventWithLocation(
                            this,
                            locationManager,
                            "Экстремальная тряска (${accelerometerValue})"
                        )
                        "Экстремальная тряска (${accelerometerValue})"
                    }

                    accelerometerValue > 1.0 || accelerometerValue < -1.0 -> {
                        LogModule.logEventWithLocation(
                            this,
                            locationManager,
                            "Сильная тряска (${accelerometerValue})"
                        )
                        "Сильная тряска (${accelerometerValue})"
                    }

                    accelerometerValue > 0.5 || accelerometerValue < -0.5 -> "Слабая тряска (${accelerometerValue})"
                    else -> "В покое (${accelerometerValue})"
                }

                // Сохраняем данные в LiveData
                accelerometerData.value = shakeCategory
            } else {
                Toast.makeText(this, "Некорректный формат данных: $data", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка парсинга данных: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendCommandToDevice(command: String) {
        bluetoothHelper.sendCommand(command)
    }


    fun simulateDebugLogs(context: Context, locationManager: LocationManager) {
        CoroutineScope(Dispatchers.IO).launch {
            val events = listOf(
                "Сумка открыта",
                "Сумка закрыта",
                "Низкий заряд батареи",
                "Экстремальная тряска",
                "Сильная тряска"
            )

            for (event in events) {
                delay(1000) // Задержка 1 секунда между логами
                val coordinates = locationManager.getCurrentCoordinates()
                if (coordinates.isNotEmpty()) {
                    LogModule.logEventWithLocation(context, locationManager, event)
                } else {
                    Log.d("SimulateLogs", "Координаты недоступны. Пропуск события: $event")
                }
            }
        }
    }
}
