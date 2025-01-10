package com.example.bluetooth_andr11

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.permissions.PermissionHelper
import com.example.bluetooth_andr11.ui.LogScreen
import com.example.bluetooth_andr11.ui.MainScreen
import com.example.bluetooth_andr11.ui.control.AppTopBar
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var locationManager: LocationManager

    private val isBluetoothEnabled = mutableStateOf(false) // Состояние Bluetooth адаптера
    private val isDeviceConnected = mutableStateOf(false) // Состояние подключения устройства

    private val allPermissionsGranted = mutableStateOf(false)
    private val batteryPercent = mutableStateOf(0) // Информация о батарее
    private val isBluetoothConnected = mutableStateOf(false) // Состояние подключения Bluetooth
    private val coordinates = mutableStateOf("Неизвестно") // Координаты
    private val temp1 = mutableStateOf("--") // Температура 1
    private val temp2 = mutableStateOf("--") // Температура 2
    private val hallState = mutableStateOf("--") // Состояние датчика Холла
    private val functionState = mutableStateOf("--") // Функциональное состояние
    private val accelerometerData = mutableStateOf("--") // Данные акселерометра

    private var lastLoggedBatteryLevel = -1

    private var lastUpperLoggedTemp: Float? = null
    private var lastLowerLoggedTemp: Float? = null
//    private var upperTrendUp = true
//    private var lowerTrendDown = true

    private var lastLoggedBagState: String? = null

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

        permissionHelper = PermissionHelper(this, requestPermissionsLauncher)
        locationManager = LocationManager(
            context = this,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        )

        bluetoothHelper = BluetoothHelper(this)
        // Отслеживаем изменения состояния Bluetooth и подключения устройства
        bluetoothHelper.monitorBluetoothStatus(this, locationManager) { isEnabled, isConnected ->
            isBluetoothEnabled.value = isEnabled
            isDeviceConnected.value = isConnected

            if (isConnected) {
                bluetoothHelper.listenForData { data ->
                    handleReceivedData(data)
                }
            } else if (isEnabled && !isConnected) {
                bluetoothHelper.showDeviceSelectionDialog(this) { device ->
                    bluetoothHelper.connectToDevice(device) { success, message ->
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        isDeviceConnected.value = success
                    }
                }
            }
        }

        // Проверяем наличие всех необходимых разрешений
        allPermissionsGranted.value = permissionHelper.hasAllPermissions()
        if (allPermissionsGranted.value) {
            initializeAppFeatures()
        } else {
            permissionHelper.requestPermissions()
        }

        // Эмулируем данные от Arduino
//        simulateTemperatureChanges(this, locationManager, bluetoothHelper)
//        LogModule.logEventWithLocation(this, bluetoothHelper, locationManager, "Сумка закрыта")

        LogModule.logEvent(this, "Приложение запущено")


        setContent {
            Bluetooth_andr11Theme {
                val navController = rememberNavController()

                Scaffold(topBar = {
                    AppTopBar(
                        batteryLevel = batteryPercent.value,
                        isBluetoothEnabled = isBluetoothEnabled.value,
                        isDeviceConnected = isDeviceConnected.value,
//                        bluetoothHelper = bluetoothHelper,
                        allPermissionsGranted = allPermissionsGranted.value,
                        onPermissionsClick = ::handlePermissionsIconClick,
                        onBluetoothClick = ::handleConnectToDevice
                    )
                }) { innerPadding ->
                    // Навигация между экранами
                    NavHost(
                        navController = navController,
                        startDestination = "main_screen",
//                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("main_screen") {
                            MainScreen(
                                modifier = Modifier.padding(innerPadding),
                                onCommandSend = ::sendCommandToDevice,
                                temp1 = temp1.value,
                                temp2 = temp2.value,
                                hallState = hallState.value,
                                acc = accelerometerData.value,
                                onNavigateToLogs = { navController.navigate("log_screen") },
                                bluetoothHelper = bluetoothHelper,
                                locationManager = locationManager,
                            )
                        }

                        composable("log_screen") {
                            LogScreen(navController = navController)
                        }
                    }
                }
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

    fun handleReceivedData(data: String) {
        parseArduinoData(data)
    }

    private fun parseArduinoData(data: String) {
        try {
            val parts = data.split(",")
            if (parts.size == 6) {
                // Парсим заряд батареи
                val batteryValue = parts[0].trim().toIntOrNull() ?: -1

                // Проверяем, что значение заряда находится в допустимом диапазоне
                if (batteryValue in 0..100) {
                    batteryPercent.value = batteryValue
                } else {
                    Log.e("MainActivity", "Некорректное значение заряда батареи: $batteryValue")
                }

                // Логирование по порогам заряда батареи
                logBatteryThresholds(batteryValue)

                // Логируем чистые данные от Arduino
                Log.e("ArduinoData", "Данные от ардуино: $data")

                // Парсим температуры
                val upperTemp = parts[1].trim().toFloatOrNull()
                val lowerTemp = parts[2].trim().toFloatOrNull()

                logTemperatureWithBoundaries(upperTemp, lowerTemp)

                // Обновляем интерфейс с текущими значениями
                temp1.value = upperTemp?.toString() ?: temp1.value
                temp2.value = lowerTemp?.toString() ?: temp2.value

                hallState.value = when (parts[3].trim()) {
                    "1" -> {
                        logBagState("Сумка закрыта")
//                        LogModule.logEventWithLocationAndLimit(
//                            this, bluetoothHelper, locationManager, "Сумка закрыта", noRepeat = true
//                        )
                        "Закрыт"
                    }

                    "0" -> {
                        logBagState("Сумка открыта")

//                        LogModule.logEventWithLocationAndLimit(
//                            this, bluetoothHelper, locationManager, "Сумка открыта", noRepeat = true
//                        )
                        "Открыт"
                    }

                    else -> "Неизвестно"
                }
                functionState.value = parts[4].trim()

                val accelerometerValue = parts[5].trim().toFloatOrNull() ?: 0.0f
                val shakeCategory = when {
                    accelerometerValue > 2.5 || accelerometerValue < -2.5 -> {
                        LogModule.logEventWithLocationAndLimit(
                            this,
                            bluetoothHelper,
                            locationManager,
                            "Экстремальная тряска (${accelerometerValue})"
                        )
                        "Экстремальная тряска (${accelerometerValue})"
                    }

                    accelerometerValue > 1.0 || accelerometerValue < -1.0 -> {
                        LogModule.logEventWithLocationAndLimit(
                            this,
                            bluetoothHelper,
                            locationManager,
                            "Сильная тряска (${accelerometerValue})"
                        )
                        "Сильная тряска (${accelerometerValue})"
                    }

                    accelerometerValue > 0.5 || accelerometerValue < -0.5 -> "Слабая тряска (${accelerometerValue})"
                    else -> "В покое (${accelerometerValue})"
                }

                accelerometerData.value = shakeCategory

                // Логируем каждые 10% изменения заряда
//                if (lastLoggedBatteryLevel == -1 || batteryPercent.value <= lastLoggedBatteryLevel - 10) {
//                    lastLoggedBatteryLevel = batteryPercent.value
//                    LogModule.logEventWithLocation(
//                        this,
//                        bluetoothHelper,
//                        locationManager,
//                        "Уровень заряда сумки: ${batteryPercent.value}%"
//                    )
//                }
            } else {
                Log.e("MainActivity", "Некорректный формат данных: $data")
                Toast.makeText(this, "Некорректный формат данных: $data", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка парсинга данных: ${e.message}")
            Toast.makeText(this, "Ошибка парсинга данных: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logBagState(newState: String) {
        if (lastLoggedBagState != newState) {
            lastLoggedBagState = newState
            LogModule.logEventWithLocationAndLimit(
                this, bluetoothHelper, locationManager, newState, noRepeat = true
            )
            Log.d("BagStateLog", "Состояние сумки изменилось: $newState")
        } else {
            Log.d("BagStateLog", "Состояние сумки не изменилось, логирование пропущено.")
        }
    }

    // Проверка допустимого изменения температуры
//    private fun isValidTempChange(lastTemp: Float?, newTemp: Float, maxChange: Float): Boolean {
//        return lastTemp == null || kotlin.math.abs(newTemp - lastTemp) <= maxChange
//    }

    private fun logBatteryThresholds(batteryValue: Int) {
        when {
            batteryValue < 5 && lastLoggedBatteryLevel != 5 -> {
                lastLoggedBatteryLevel = 5
                logBatteryLevel("Критически низкий уровень заряда (<5%)")
            }

            batteryValue < 10 && lastLoggedBatteryLevel > 10 -> {
                lastLoggedBatteryLevel = 10
                logBatteryLevel("Очень низкий уровень заряда (<10%)")
            }

            batteryValue < 25 && lastLoggedBatteryLevel > 25 -> {
                lastLoggedBatteryLevel = 25
                logBatteryLevel("Низкий уровень заряда (<25%)")
            }

            batteryValue < 50 && lastLoggedBatteryLevel > 50 -> {
                lastLoggedBatteryLevel = 50
                logBatteryLevel("Уровень заряда менее половины (<50%)")
            }
        }
    }

    private fun logTemperatureWithBoundaries(temp1Value: Float?, temp2Value: Float?) {
        // Пороговые значения для верхнего и нижнего отсеков
        val upperThresholds = listOf(40, 50, 60)
        val lowerThresholds = listOf(5, 10, 15)

        // Логирование температуры верхнего отсека по порогам
        if (temp1Value != null) {
            val temp1Int = temp1Value.toInt()
            if (lastUpperLoggedTemp == null || (temp1Int in upperThresholds && temp1Int.toFloat() != lastUpperLoggedTemp)) {
                val event = when (temp1Int) {
                    40 -> "Температура верхнего отсека достигла 40°C"
                    50 -> "Температура верхнего отсека достигла 50°C"
                    60 -> "Температура верхнего отсека достигла 60°C"
                    else -> null
                }
                event?.let {
                    LogModule.logEventWithLocationAndLimit(
                        this, bluetoothHelper, locationManager, it
                    )
                    lastUpperLoggedTemp = temp1Int.toFloat()
                }
            }
        }

        // Логирование температуры нижнего отсека по порогам
        if (temp2Value != null) {
            val temp2Int = temp2Value.toInt()
            if (lastLowerLoggedTemp == null || (temp2Int in lowerThresholds && temp2Int.toFloat() != lastLowerLoggedTemp)) {
                val event = when (temp2Int) {
                    5 -> "Температура нижнего отсека упала до 5°C"
                    10 -> "Температура нижнего отсека упала до 10°C"
                    15 -> "Температура нижнего отсека упала до 15°C"
                    else -> null
                }
                event?.let {
                    LogModule.logEventWithLocationAndLimit(
                        this, bluetoothHelper, locationManager, it
                    )
                    lastLowerLoggedTemp = temp2Int.toFloat()
                }
            }
        }
    }

    // Вспомогательный метод для логирования уровня заряда
    private fun logBatteryLevel(message: String) {
        LogModule.logEventWithLocationAndLimit(
            this, bluetoothHelper, locationManager, message
        )
        Log.d("BatteryLevel", message)
    }


    private fun sendCommandToDevice(command: String) {
        bluetoothHelper.sendCommand(command)
    }

}
