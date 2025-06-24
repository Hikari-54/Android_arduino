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

    /// 🔥 НОВЫЕ переменные для отслеживания пройденных порогов
    private var lastLoggedBatteryLevel = 101
    private var lastUpperTemp: Int? = null
    private var lastLowerTemp: Int? = null

    // 🔥 ПОРОГИ которые УЖЕ были пройдены (для предотвращения дублирования)
    private val upperTempThresholdsReached = mutableSetOf<Int>()
    private val lowerTempThresholdsReached = mutableSetOf<Int>()

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
                    },
                    // 🔥 ДОБАВЛЕННЫЙ параметр для скрытия Bluetooth в режиме симуляции
                    bluetoothHelper = bluetoothHelper
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

    // 🔥 ПОЛНОСТЬЮ ИСПРАВЛЕННАЯ функция логирования батареи
    private fun logBatteryThresholds(batteryValue: Int) {
        Log.d(
            TAG,
            "🔋 Проверка батареи: текущий=$batteryValue%, последний зафиксированный=$lastLoggedBatteryLevel%"
        )

        // Проверяем понижение уровня батареи
        val downwardThresholds = listOf(50, 30, 15, 5)
        for (threshold in downwardThresholds) {
            if (batteryValue <= threshold && lastLoggedBatteryLevel > threshold) {
                lastLoggedBatteryLevel = threshold
                val message = when (threshold) {
                    5 -> "🚨 КРИТИЧЕСКИ низкий уровень заряда (≤5%)"
                    15 -> "⚠️ Очень низкий уровень заряда (≤15%)"
                    30 -> "⚡ Низкий уровень заряда (≤30%)"
                    50 -> "🔋 Уровень заряда менее половины (≤50%)"
                    else -> continue
                }

                Log.d(TAG, "🔋 Логируем пороговое событие батареи: $message")
                LogModule.logSystemEvent(
                    this, bluetoothHelper, enhancedLocationManager,
                    message, "БАТАРЕЯ"
                )
                break // Логируем только один порог за раз
            }
        }
    }

    // 🔥 ИСПРАВЛЕННАЯ функция логирования температуры
    private fun logTemperatureThresholds(upperTemp: Float?, lowerTemp: Float?) {
        // 🔥 ВЕРХНИЙ ОТСЕК (ГОРЯЧИЙ)
        upperTemp?.let { temp ->
            val tempInt = temp.toInt()
            val previousTemp = lastUpperTemp
            lastUpperTemp = tempInt

            Log.d(TAG, "🌡️ Верхний: было=${previousTemp}°C → стало=${tempInt}°C")

            if (previousTemp != null) {
                // 🔥 ПОВЫШЕНИЕ температуры - проверяем пороги
                if (tempInt > previousTemp) {
                    when {
                        tempInt >= 40 && !upperTempThresholdsReached.contains(40) -> {
                            upperTempThresholdsReached.add(40)
                            logCriticalTemperatureEvent("🚨 ВЕРХНИЙ ОТСЕК: Достиг 40°C! (было ${previousTemp}°C)")
                        }

                        tempInt >= 50 && !upperTempThresholdsReached.contains(50) -> {
                            upperTempThresholdsReached.add(50)
                            logCriticalTemperatureEvent("🔥 ВЕРХНИЙ ОТСЕК: Достиг 50°C! (было ${previousTemp}°C)")
                        }

                        tempInt >= 60 && !upperTempThresholdsReached.contains(60) -> {
                            upperTempThresholdsReached.add(60)
                            logCriticalTemperatureEvent("🚨 ВЕРХНИЙ ОТСЕК: Достиг 60°C! (было ${previousTemp}°C)")
                        }

                        tempInt >= 70 && !upperTempThresholdsReached.contains(70) -> {
                            upperTempThresholdsReached.add(70)
                            logCriticalTemperatureEvent("🔥 ВЕРХНИЙ ОТСЕК: КРИТИЧНО! Достиг 70°C! (было ${previousTemp}°C)")
                        }
                    }
                }

                // 🔥 ПОНИЖЕНИЕ температуры - проверяем пороги
                if (tempInt < previousTemp) {
                    when {
                        tempInt <= 50 && upperTempThresholdsReached.contains(60) && !upperTempThresholdsReached.contains(
                            -50
                        ) -> {
                            upperTempThresholdsReached.add(-50) // Отрицательное значение = "остыл до 50"
                            logCriticalTemperatureEvent("❄️ ВЕРХНИЙ ОТСЕК: Остыл до 50°C (было ${previousTemp}°C)")
                        }

                        tempInt <= 40 && upperTempThresholdsReached.contains(50) && !upperTempThresholdsReached.contains(
                            -40
                        ) -> {
                            upperTempThresholdsReached.add(-40)
                            logCriticalTemperatureEvent("❄️ ВЕРХНИЙ ОТСЕК: Остыл до 40°C (было ${previousTemp}°C)")
                        }

                        tempInt <= 30 && upperTempThresholdsReached.contains(40) && !upperTempThresholdsReached.contains(
                            -30
                        ) -> {
                            upperTempThresholdsReached.add(-30)
                            logCriticalTemperatureEvent("🟢 ВЕРХНИЙ ОТСЕК: Нормализовался до 30°C (было ${previousTemp}°C)")
                        }

                        tempInt <= 25 && upperTempThresholdsReached.contains(40) && !upperTempThresholdsReached.contains(
                            -25
                        ) -> {
                            upperTempThresholdsReached.add(-25)
                            logCriticalTemperatureEvent("✅ ВЕРХНИЙ ОТСЕК: Вернулся к норме 25°C (было ${previousTemp}°C)")
                        }
                    }
                }
            }
        }

        // 🔥 НИЖНИЙ ОТСЕК (ХОЛОДНЫЙ)
        lowerTemp?.let { temp ->
            val tempInt = temp.toInt()
            val previousTemp = lastLowerTemp
            lastLowerTemp = tempInt

            Log.d(TAG, "🌡️ Нижний: было=${previousTemp}°C → стало=${tempInt}°C")

            if (previousTemp != null) {
                // 🔥 ПОНИЖЕНИЕ температуры (хорошо для холодного отсека)
                if (tempInt < previousTemp) {
                    when {
                        tempInt <= 15 && !lowerTempThresholdsReached.contains(15) -> {
                            lowerTempThresholdsReached.add(15)
                            logCriticalTemperatureEvent("❄️ НИЖНИЙ ОТСЕК: Достиг 15°C - холодовая цепь (было ${previousTemp}°C)")
                        }

                        tempInt <= 10 && !lowerTempThresholdsReached.contains(10) -> {
                            lowerTempThresholdsReached.add(10)
                            logCriticalTemperatureEvent("🧊 НИЖНИЙ ОТСЕК: Достиг 10°C - глубокое охлаждение (было ${previousTemp}°C)")
                        }

                        tempInt <= 5 && !lowerTempThresholdsReached.contains(5) -> {
                            lowerTempThresholdsReached.add(5)
                            logCriticalTemperatureEvent("🌨️ НИЖНИЙ ОТСЕК: Достиг 5°C - заморозка (было ${previousTemp}°C)")
                        }

                        tempInt <= 0 && !lowerTempThresholdsReached.contains(0) -> {
                            lowerTempThresholdsReached.add(0)
                            logCriticalTemperatureEvent("🧊 НИЖНИЙ ОТСЕК: Достиг 0°C - глубокая заморозка (было ${previousTemp}°C)")
                        }

                        tempInt <= -5 && !lowerTempThresholdsReached.contains(-5) -> {
                            lowerTempThresholdsReached.add(-5)
                            logCriticalTemperatureEvent("❄️ НИЖНИЙ ОТСЕК: Достиг -5°C - экстремальная заморозка (было ${previousTemp}°C)")
                        }
                    }
                }

                // 🔥 ПОВЫШЕНИЕ температуры (НАРУШЕНИЕ холодовой цепи!)
                if (tempInt > previousTemp) {
                    when {
                        tempInt >= 5 && lowerTempThresholdsReached.contains(0) && !lowerTempThresholdsReached.contains(
                            -105
                        ) -> {
                            lowerTempThresholdsReached.add(-105) // Отрицательное = "нагрелся до 5"
                            logCriticalTemperatureEvent("🚨 НАРУШЕНИЕ ХОЛОДОВОЙ ЦЕПИ: Нижний отсек нагрелся до 5°C! (было ${previousTemp}°C)")
                        }

                        tempInt >= 10 && lowerTempThresholdsReached.contains(5) && !lowerTempThresholdsReached.contains(
                            -110
                        ) -> {
                            lowerTempThresholdsReached.add(-110)
                            logCriticalTemperatureEvent("🔥 КРИТИЧЕСКОЕ НАРУШЕНИЕ: Нижний отсек нагрелся до 10°C! (было ${previousTemp}°C)")
                        }

                        tempInt >= 15 && lowerTempThresholdsReached.contains(10) && !lowerTempThresholdsReached.contains(
                            -115
                        ) -> {
                            lowerTempThresholdsReached.add(-115)
                            logCriticalTemperatureEvent("⚠️ ПОТЕРЯ ОХЛАЖДЕНИЯ: Нижний отсек нагрелся до 15°C! (было ${previousTemp}°C)")
                        }

                        tempInt >= 20 && lowerTempThresholdsReached.contains(15) && !lowerTempThresholdsReached.contains(
                            -120
                        ) -> {
                            lowerTempThresholdsReached.add(-120)
                            logCriticalTemperatureEvent("🌡️ ПОЛНАЯ ПОТЕРЯ ХОЛОДА: Нижний отсек нагрелся до 20°C! (было ${previousTemp}°C)")
                        }

                        // 🔥 ДОПОЛНИТЕЛЬНО: Любое повышение с холодных температур
                        tempInt > 0 && previousTemp <= 0 && !lowerTempThresholdsReached.contains(-200) -> {
                            lowerTempThresholdsReached.add(-200)
                            logCriticalTemperatureEvent("🚨 РАЗМОРАЖИВАНИЕ: Нижний отсек вышел из заморозки! ${previousTemp}°C → ${tempInt}°C")
                        }
                    }
                }
            }
        }
    }

    // 🔥 ФУНКЦИЯ для сброса порогов (если нужно перезапустить логирование)
    fun resetTemperatureThresholds() {
        upperTempThresholdsReached.clear()
        lowerTempThresholdsReached.clear()
        lastUpperTemp = null
        lastLowerTemp = null
        Log.d(TAG, "🔄 Пороги температуры сброшены")
    }

    // 🔥 КРИТИЧЕСКИЕ температурные события (БЕЗ ограничений!)
    private fun logCriticalTemperatureEvent(message: String) {
        Log.d(TAG, "🌡️ КРИТИЧЕСКОЕ СОБЫТИЕ: $message")

        try {
            // 🔥 ПРЯМАЯ ЗАПИСЬ В ЛОГ-ФАЙЛ (минуя все ограничения!)
            val logDir = File(this.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "events_log.txt")
            val timestamp =
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())

            // Получаем координаты
            val locationInfo = enhancedLocationManager.getLocationInfo()
            val coordinates = if (locationInfo.coordinates != "Неизвестно") {
                "${locationInfo.coordinates} (${locationInfo.source}, ±${locationInfo.accuracy.toInt()}м)"
            } else {
                "Координаты недоступны"
            }

            val logEntry = "$timestamp - ТЕМПЕРАТУРА: $message @ $coordinates\n"
            logFile.appendText(logEntry)

            Log.d(TAG, "✅ Температурное событие записано НАПРЯМУЮ в файл: $logEntry")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка прямой записи температурного лога: ${e.message}")

            // Fallback - через LogModule без ограничений
            LogModule.logEvent(this, "ТЕМПЕРАТУРА: $message")
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