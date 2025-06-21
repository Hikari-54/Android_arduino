package com.example.bluetooth_andr11

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.permissions.PermissionHelper
import com.example.bluetooth_andr11.ui.LogScreen
import com.example.bluetooth_andr11.ui.MainScreen
import com.example.bluetooth_andr11.ui.control.AppTopBar
import com.example.bluetooth_andr11.ui.debug.DebugControlPanel
import com.example.bluetooth_andr11.ui.location.LocationRequiredScreen
import com.example.bluetooth_andr11.ui.location.isLocationEnabled
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var enhancedLocationManager: EnhancedLocationManager

    // Состояния UI
    private val isBluetoothEnabled = mutableStateOf(false)
    private val isDeviceConnected = mutableStateOf(false)
    private val allPermissionsGranted = mutableStateOf(false)
    private val batteryPercent = mutableStateOf(0)
    private val coordinates = mutableStateOf("Неизвестно")
    private val temp1 = mutableStateOf("--")
    private val temp2 = mutableStateOf("--")
    private val hallState = mutableStateOf("--")
    private val functionState = mutableStateOf("--")
    private val accelerometerData = mutableStateOf("--")

    // Реактивное состояние GPS
    private val isLocationServiceEnabled = mutableStateOf(false)
    private val showDebugPanel = mutableStateOf(false)

    // Кеш для предотвращения дублирования логов
    private var lastLoggedBatteryLevel = -1
    private var lastUpperLoggedTemp: Float? = null
    private var lastLowerLoggedTemp: Float? = null
    private var lastLoggedBagState: String? = null

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            allPermissionsGranted.value = allGranted

            if (allGranted) {
                Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
                if (isLocationServiceEnabled.value) {
                    initializeAppFeatures()
                }
            } else {
                handlePermissionsDenial(permissions)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Начальная проверка GPS
        isLocationServiceEnabled.value = isLocationEnabled(this)

        setupCachePath()
        initializeComponents()
        setupMonitoring() // Настройка всех мониторингов
        checkInitialPermissions()
        if (BuildConfig.DEBUG) {
            autoStartSimulationIfNeeded()
        }

        // 🔥 ИЗМЕНЕНО: Используем умное логирование для запуска
        LogModule.logSystemEvent(
            this, bluetoothHelper, enhancedLocationManager,
            "Приложение запущено", "СИСТЕМА"
        )

        setContent {
            Bluetooth_andr11Theme {
                // Реактивная проверка GPS
                if (!isLocationServiceEnabled.value) {
                    LocationRequiredScreen(
                        onLocationEnabled = {
                            // Принудительная проверка через EnhancedLocationManager
                            val actualState = enhancedLocationManager.forceLocationStatusCheck()
                            isLocationServiceEnabled.value = actualState

                            if (actualState && allPermissionsGranted.value) {
                                initializeAppFeatures()
                            }

                            Log.d(TAG, "✅ GPS проверен после включения: $actualState")
                        }
                    )
                } else {
                    MainAppContent()
                }
            }
        }
    }

    // Функция инициализации компонентов
    private fun initializeComponents() {
        permissionHelper = PermissionHelper(this, requestPermissionsLauncher)
        enhancedLocationManager = EnhancedLocationManager(
            context = this,
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        )
        bluetoothHelper = BluetoothHelper(this)
    }

    // Функция настройки всех мониторингов
    private fun setupMonitoring() {
        setupBluetoothMonitoring()
        setupGpsMonitoring()
    }

    // 🔥 ОБНОВЛЕННАЯ функция для настройки GPS мониторинга
    private fun setupGpsMonitoring() {
        Log.d(TAG, "🔄 Настройка GPS мониторинга...")

        enhancedLocationManager.setLocationStatusChangeListener { isEnabled ->
            Log.d(TAG, "📍 GPS состояние изменилось: $isEnabled")

            runOnUiThread {
                isLocationServiceEnabled.value = isEnabled

                if (!isEnabled) {
                    Toast.makeText(
                        this,
                        "⚠️ Внимание: GPS отключен! Функции местоположения недоступны.",
                        Toast.LENGTH_LONG
                    ).show()

                    // 🔥 ИЗМЕНЕНО: Используем умное логирование GPS
                    LogModule.logGpsStateChange(
                        this,
                        false,
                        "Пользователь отключил GPS во время работы приложения"
                    )
                } else {
                    Toast.makeText(
                        this,
                        "✅ GPS включен! Функции местоположения восстановлены.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // 🔥 ИЗМЕНЕНО: Используем умное логирование GPS
                    LogModule.logGpsStateChange(
                        this,
                        true,
                        "GPS включен пользователем"
                    )

                    if (allPermissionsGranted.value) {
                        initializeAppFeatures()
                    }
                }
            }
        }

        val initialState = enhancedLocationManager.forceLocationStatusCheck()
        isLocationServiceEnabled.value = initialState

        // 🔥 НОВОЕ: Логируем начальное состояние через умное логирование
        LogModule.logGpsStateChange(this, initialState, "Проверка при запуске приложения")

        Log.d(TAG, "🚀 Начальное состояние GPS: $initialState")
    }

    @Composable
    private fun MainAppContent() {
        val navController = rememberNavController()

        Scaffold(
            topBar = {
                AppTopBar(
                    batteryLevel = batteryPercent.value,
                    isBluetoothEnabled = isBluetoothEnabled.value,
                    isDeviceConnected = isDeviceConnected.value,
                    allPermissionsGranted = allPermissionsGranted.value,
                    onPermissionsClick = ::handlePermissionsIconClick,
                    onBluetoothClick = ::handleConnectToDevice,
                    onDebugClick = {
                        showDebugPanel.value = !showDebugPanel.value
                        Log.d(TAG, "Debug panel toggled: ${showDebugPanel.value}")
                    },
                    showDebugButton = BuildConfig.DEBUG,
                    onTitleClick = {
                        navController.navigate("main_screen") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                        showDebugPanel.value = false
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "main_screen",
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
                        locationManager = enhancedLocationManager,
                    )
                }

                composable("log_screen") {
                    LogScreen(navController = navController)
                }
            }

            if (showDebugPanel.value && BuildConfig.DEBUG) {
                DebugControlPanel(
                    bluetoothHelper = bluetoothHelper,
                    locationManager = enhancedLocationManager
                )
            }
        }
    }

    private fun setupBluetoothMonitoring() {
        bluetoothHelper.monitorBluetoothStatus(
            this,
            enhancedLocationManager
        ) { isEnabled, isConnected ->
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
    }

    private fun checkInitialPermissions() {
        allPermissionsGranted.value = permissionHelper.hasAllPermissions()
        if (allPermissionsGranted.value && isLocationServiceEnabled.value) {
            initializeAppFeatures()
        } else if (!allPermissionsGranted.value) {
            permissionHelper.requestPermissions()
        }
    }

    private fun initializeAppFeatures() {
        enhancedLocationManager.startLocationUpdates { newCoordinates ->
            coordinates.value = newCoordinates
            Log.d(TAG, "📍 Координаты обновлены: $newCoordinates")
        }

        if (isLocationServiceEnabled.value) {
            enhancedLocationManager.forceLocationUpdate(EnhancedLocationManager.LocationMode.BALANCED)
        }

        // Получаем рекомендуемый режим GPS
        val recommendedMode = enhancedLocationManager.getRecommendedMode()
        enhancedLocationManager.setLocationMode(recommendedMode)
        Log.d(TAG, "🎯 Установлен рекомендуемый режим GPS: $recommendedMode")
    }

    private fun autoStartSimulationIfNeeded() {
        // 🔥 ИСПРАВЛЕНИЕ: Двойная проверка DEBUG режима
        if (!BuildConfig.DEBUG) {
            Log.i(TAG, "RELEASE режим: автозапуск симуляции отключен")
            return
        }

        if (!bluetoothHelper.isDeviceConnected) {
            Handler(Looper.getMainLooper()).postDelayed({
                // 🔥 ДОПОЛНИТЕЛЬНАЯ проверка перед запуском
                if (BuildConfig.DEBUG && !bluetoothHelper.isDeviceConnected) {
                    bluetoothHelper.enableSimulationMode(true)
                    Toast.makeText(this, "🔧 Запущена симуляция Arduino (DEBUG)", Toast.LENGTH_LONG)
                        .show()

                    LogModule.logSystemEvent(
                        this, bluetoothHelper, enhancedLocationManager,
                        "Автозапуск симуляции Arduino (DEBUG режим)", "ОТЛАДКА"
                    )
                }
            }, 3000)
        }
    }

    fun handleReceivedData(data: String) {
        Log.d(TAG, "🔴 Получены RAW данные: '$data'")
        parseArduinoData(data)
    }

    private fun parseArduinoData(data: String) {
        try {
            val cleanData = data.trim()
            val parts = cleanData.split(",")

            if (parts.size >= 6) {
                Log.d(TAG, "✅ Парсинг ${parts.size} параметров")

                val batteryValue = parts[0].trim().toIntOrNull() ?: -1
                val upperTempString = parts[1].trim()
                val lowerTempString = parts[2].trim()
                val closedState = parts[3].trim()
                val arduinoState = parts[4].trim().toIntOrNull() ?: 0
                val accelerometerValue = parts[5].trim().toFloatOrNull() ?: 0.0f

                // Обновляем UI
                updateBatteryLevel(batteryValue)
                updateTemperatures(upperTempString, lowerTempString)
                updateBagState(closedState)
                updateAccelerometer(accelerometerValue)

                // Логируем дополнительные параметры для отладки
                if (parts.size > 6) {
                    val extraParams = parts.subList(6, parts.size).joinToString(",")
                    Log.w(TAG, "⚠️ Лишние параметры проигнорированы: $extraParams")
                }

            } else {
                Log.w(TAG, "❌ Недостаточно параметров: получено ${parts.size}, ожидается 6")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка парсинга данных: ${e.message}")
        }
    }

    private fun updateBatteryLevel(batteryValue: Int) {
        if (batteryValue in 0..100) {
            batteryPercent.value = batteryValue
            logBatteryThresholds(batteryValue)
        } else {
            Log.w(TAG, "⚠️ Некорректное значение батареи: $batteryValue")
        }
    }

    private fun updateTemperatures(upperTempString: String, lowerTempString: String) {
        val upperTemp = if (upperTempString == "er") {
            Log.w(TAG, "⚠️ Ошибка датчика верхнего отсека")
            null
        } else {
            upperTempString.toFloatOrNull()
        }

        val lowerTemp = if (lowerTempString == "er") {
            Log.w(TAG, "⚠️ Ошибка датчика нижнего отсека")
            null
        } else {
            lowerTempString.toFloatOrNull()
        }

        temp1.value = when {
            upperTempString == "er" -> "Ошибка"
            upperTemp != null -> upperTemp.toString()
            else -> temp1.value // Сохраняем старое значение
        }

        temp2.value = when {
            lowerTempString == "er" -> "Ошибка"
            lowerTemp != null -> lowerTemp.toString()
            else -> temp2.value // Сохраняем старое значение
        }

        logTemperatureThresholds(upperTemp, lowerTemp)
    }

    private fun updateBagState(closedState: String) {
        val newState = when (closedState) {
            "1" -> {
                Log.d(TAG, "🔒 Сумка закрыта")
                "Закрыт"
            }

            "0" -> {
                Log.d(TAG, "🔓 Сумка открыта")
                "Открыт"
            }

            else -> {
                Log.w(TAG, "❓ Неизвестное состояние сумки: $closedState")
                "Неизвестно"
            }
        }

        hallState.value = newState
        logBagStateChange(newState)
    }

    private var lastAccelerometerLogTime = 0L

    // 🔥 ОБНОВЛЕННАЯ функция логирования акселерометра
    private fun updateAccelerometer(accelerometerValue: Float) {
        val shakeCategory = when {
            accelerometerValue > 2.5 -> {
                // 🔥 ДОБАВЛЕНО: Ограничение по времени для акселерометра
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAccelerometerLogTime > 2000) { // 2 секунд
                    LogModule.logSystemEvent(
                        this, bluetoothHelper, enhancedLocationManager,
                        "Экстремальная тряска (${String.format("%.2f", accelerometerValue)})",
                        "АКСЕЛЕРОМЕТР"
                    )
                    lastAccelerometerLogTime = currentTime
                }
                "Экстремальная тряска"
            }

            accelerometerValue > 1.0 -> {
                // 🔥 ДОБАВЛЕНО: Ограничение по времени для акселерометра
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAccelerometerLogTime > 2000) { // 2 секунд
                    LogModule.logSystemEvent(
                        this, bluetoothHelper, enhancedLocationManager,
                        "Сильная тряска (${String.format("%.2f", accelerometerValue)})",
                        "АКСЕЛЕРОМЕТР"
                    )
                    lastAccelerometerLogTime = currentTime
                }
                "Сильная тряска"
            }

            accelerometerValue > 0.5 -> "Слабая тряска"
            else -> "В покое"
        }

        accelerometerData.value = "$shakeCategory (${String.format("%.2f", accelerometerValue)})"
    }

    // 🔥 ОБНОВЛЕННАЯ функция логирования батареи
    private fun logBatteryThresholds(batteryValue: Int) {
        val thresholds = listOf(5, 10, 25, 50)
        val threshold = thresholds.find { batteryValue < it && lastLoggedBatteryLevel >= it }

        threshold?.let {
            lastLoggedBatteryLevel = it
            val message = when (it) {
                5 -> "Критически низкий уровень заряда (<5%)"
                10 -> "Очень низкий уровень заряда (<10%)"
                25 -> "Низкий уровень заряда (<25%)"
                50 -> "Уровень заряда менее половины (<50%)"
                else -> return
            }

            // 🔥 ИЗМЕНЕНО: Используем системное событие для батареи
            LogModule.logSystemEvent(
                this, bluetoothHelper, enhancedLocationManager,
                message, "БАТАРЕЯ"
            )
        }
    }

    // 🔥 ОБНОВЛЕННАЯ функция логирования температуры
    private fun logTemperatureThresholds(upperTemp: Float?, lowerTemp: Float?) {
        // Логирование температуры верхнего отсека
        upperTemp?.let { temp ->
            val thresholds = listOf(40, 50, 60)
            val threshold = thresholds.find {
                temp.toInt() >= it && (lastUpperLoggedTemp == null || lastUpperLoggedTemp!! < it)
            }

            threshold?.let {
                lastUpperLoggedTemp = it.toFloat()
                // 🔥 ИЗМЕНЕНО: Используем системное событие
                LogModule.logSystemEvent(
                    this, bluetoothHelper, enhancedLocationManager,
                    "Температура верхнего отсека достигла ${it}°C", "ТЕМПЕРАТУРА"
                )
            }
        }

        // Логирование температуры нижнего отсека
        lowerTemp?.let { temp ->
            val thresholds = listOf(5, 10, 15)
            val threshold = thresholds.find {
                temp.toInt() <= it && (lastLowerLoggedTemp == null || lastLowerLoggedTemp!! > it)
            }

            threshold?.let {
                lastLowerLoggedTemp = it.toFloat()
                // 🔥 ИЗМЕНЕНО: Используем системное событие
                LogModule.logSystemEvent(
                    this, bluetoothHelper, enhancedLocationManager,
                    "Температура нижнего отсека упала до ${it}°C", "ТЕМПЕРАТУРА"
                )
            }
        }
    }

    // 🔥 ОБНОВЛЕННАЯ функция логирования состояния сумки
    private fun logBagStateChange(newState: String) {
        if (lastLoggedBagState != newState) {
            lastLoggedBagState = newState
            val message = "Сумка ${if (newState == "Закрыт") "закрыта" else "открыта"}"

            // 🔥 ИЗМЕНЕНО: Используем системное событие с ограничением
            LogModule.logSystemEvent(
                this, bluetoothHelper, enhancedLocationManager,
                message, "ДАТЧИК_ХОЛЛА"
            )
        }
    }

    private fun sendCommandToDevice(command: String) {
        Log.d(TAG, "📤 Отправляем команду: $command")
        bluetoothHelper.sendCommand(command)
    }

    private fun handlePermissionsIconClick() {
        if (!allPermissionsGranted.value) {
            permissionHelper.requestPermissions()
        } else {
            Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePermissionsDenial(permissions: Map<String, Boolean>) {
        val permanentlyDenied = permissions.filter { permission ->
            !permission.value && !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                permission.key
            )
        }

        if (permanentlyDenied.isNotEmpty()) {
            Toast.makeText(
                this,
                "Предоставьте разрешения в настройках приложения",
                Toast.LENGTH_LONG
            ).show()
            redirectToAppSettings()
        } else {
            permissionHelper.requestPermissions()
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
                isDeviceConnected.value = success
                if (success) {
                    bluetoothHelper.listenForData { data ->
                        handleReceivedData(data)
                    }
                }
            }
        }
    }

    private fun setupCachePath() {
        val context = applicationContext
        val cacheDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.filesDir, "osmdroid")
        } else {
            File(context.getExternalFilesDir(null), "osmdroid")
        }

        val config = Configuration.getInstance()
        config.osmdroidBasePath = cacheDir
        config.osmdroidTileCache = File(cacheDir, "cache")
        config.userAgentValue = packageName
    }

    // Функция для очистки ресурсов
    override fun onDestroy() {
        super.onDestroy()

        try {
            // 🔥 НОВОЕ: Логируем закрытие приложения
            LogModule.logSystemEvent(
                this, bluetoothHelper, enhancedLocationManager,
                "Приложение закрыто", "СИСТЕМА"
            )

            enhancedLocationManager.cleanup()
            Log.d(TAG, "🧹 MainActivity уничтожена, GPS мониторинг остановлен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки ресурсов: ${e.message}")
        }
    }

    // 🔥 ОБНОВЛЕННАЯ функция для тестирования GPS
    fun testGpsMonitoring() {
        Log.d(TAG, "🧪 Тестирование GPS мониторинга...")

        val currentState = enhancedLocationManager.forceLocationStatusCheck()

        // 🔥 ИЗМЕНЕНО: Используем системное событие для тестов
        LogModule.logSystemEvent(
            this, bluetoothHelper, enhancedLocationManager,
            "Тест GPS мониторинга. Состояние: ${if (currentState) "включен" else "выключен"}",
            "ТЕСТ"
        )

        Toast.makeText(
            this,
            "🧪 GPS тест выполнен. Состояние: ${if (currentState) "✅" else "❌"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}