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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bluetooth_andr11.init.AppInitializer
import com.example.bluetooth_andr11.ui.LogScreen
import com.example.bluetooth_andr11.ui.MainScreen
import com.example.bluetooth_andr11.ui.control.AppTopBar
import com.example.bluetooth_andr11.ui.debug.DebugControlPanel
import com.example.bluetooth_andr11.ui.location.LocationRequiredScreen
import com.example.bluetooth_andr11.ui.location.isLocationEnabled
import com.example.bluetooth_andr11.ui.state.UIStateManager
import com.example.bluetooth_andr11.ui.theme.Bluetooth_andr11Theme
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Главная активность приложения для мониторинга доставочной сумки.
 *
 * ФИНАЛЬНАЯ АРХИТЕКТУРА:
 * - AppInitializer - полная инициализация всех компонентов
 * - UIStateManager - централизованное управление UI состояниями
 * - DataManager - обработка данных Arduino с валидацией
 * - Минималистичная MainActivity - только координация и lifecycle
 *
 * Основные обязанности MainActivity:
 * - Координация между компонентами приложения
 * - Обработка lifecycle событий и cleanup ресурсов
 * - Управление navigation и общей структурой UI
 * - Обработка разрешений через PermissionHelper
 * - Отображение ошибок инициализации с диагностикой
 *
 * Архитектурные улучшения (финальные):
 * - Полное разделение ответственности между специализированными компонентами
 * - Централизованное управление состояниями через UIStateManager
 * - Типобезопасная работа с reactive состояниями
 * - Comprehensive error handling на всех уровнях
 * - Modular architecture с возможностью независимого тестирования
 *
 * Упрощённый жизненный цикл:
 * 1. onCreate() → AppInitializer.initialize() → всё готово
 * 2. UI updates → UIStateManager → автоматическое обновление Compose
 * 3. Data flow → DataManager → UIStateManager → UI recomposition
 * 4. onDestroy() → AppInitializer.cleanup() → все ресурсы освобождены
 */
class MainActivity : ComponentActivity() {

    // === CORE КОМПОНЕНТЫ ===

    /** Централизованный инициализатор всех компонентов приложения */
    private lateinit var appInitializer: AppInitializer

    /** Централизованный менеджер всех UI состояний */
    private lateinit var uiStateManager: UIStateManager

    // === ОБРАБОТЧИК РАЗРЕШЕНИЙ ===

    /**
     * Упрощённый launcher для разрешений с делегированием логики в AppInitializer.
     */
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            uiStateManager.updateAllPermissionsGranted(allGranted)

            if (allGranted) {
                Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
                if (uiStateManager.isLocationServiceEnabled.value) {
                    appInitializer.initializeAppFeatures(uiStateManager.coordinates)
                }
            } else {
                handlePermissionsDenial(permissions)
            }
        }

    // === LIFECYCLE МЕТОДЫ ===

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализируем компоненты в правильном порядке
        initializeComponents()

        // Настраиваем UI с reactive состояниями
        setContent {
            Bluetooth_andr11Theme {
                if (!uiStateManager.isLocationServiceEnabled.value) {
                    LocationRequiredScreen(
                        onLocationEnabled = ::handleLocationEnabled
                    )
                } else {
                    MainAppContent()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    // === ИНИЦИАЛИЗАЦИЯ КОМПОНЕНТОВ ===

    /**
     * Быстрая инициализация всех компонентов через специализированные менеджеры.
     *
     * Новый подход:
     * 1. UIStateManager - создание всех reactive состояний
     * 2. Проверка GPS при запуске
     * 3. Настройка OSMDroid для карт
     * 4. AppInitializer - полная инициализация и настройка мониторинга
     */
    private fun initializeComponents() {
        try {
            Log.d(TAG, "🚀 Быстрый старт приложения...")

            // 1. Создаём UIStateManager для всех reactive состояний
            uiStateManager = UIStateManager()

            // 2. Проверяем GPS и обновляем состояние
            val gpsEnabled = isLocationEnabled(this)
            uiStateManager.updateLocationServiceEnabled(gpsEnabled)

            // 3. Настраиваем OSMDroid кэш для карт
            setupCachePath()

            // 4. Инициализируем все компоненты через AppInitializer
            appInitializer = AppInitializer(this, requestPermissionsLauncher)
            val success = appInitializer.initialize()

            if (success) {
                // Настраиваем мониторинг с reactive обновлениями
                setupMonitoring()

                // Проверяем разрешения и активируем функции
                checkPermissionsAndActivateFeatures()

                // Автозапуск симуляции в DEBUG режиме
                if (BuildConfig.DEBUG) {
                    appInitializer.autoStartSimulationIfNeeded()
                }

                Log.d(TAG, "✅ Приложение полностью готово к работе")
            } else {
                showInitializationError()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Критическая ошибка инициализации: ${e.message}")
            showInitializationError()
        }
    }

    /**
     * Настраивает мониторинг с автоматическим обновлением UI состояний.
     */
    private fun setupMonitoring() {
        // Мониторинг Bluetooth с reactive обновлениями
        appInitializer.setupBluetoothMonitoring(
            bluetoothEnabledState = uiStateManager.isBluetoothEnabled,
            deviceConnectedState = uiStateManager.isDeviceConnected,
            onDataReceived = ::handleReceivedData
        )

        // Мониторинг GPS с reactive обновлениями
        appInitializer.setupGpsMonitoring(
            locationEnabledState = uiStateManager.isLocationServiceEnabled,
            onLocationEnabledChanged = ::handleGpsStateChange
        )

        Log.d(TAG, "📡 Reactive мониторинг настроен")
    }

    /**
     * Проверяет разрешения и активирует функции через AppInitializer.
     */
    private fun checkPermissionsAndActivateFeatures() {
        appInitializer.checkInitialPermissions { hasAllPermissions ->
            uiStateManager.updateAllPermissionsGranted(hasAllPermissions)

            if (hasAllPermissions && uiStateManager.isLocationServiceEnabled.value) {
                appInitializer.initializeAppFeatures(uiStateManager.coordinates)
            }
        }
    }

    // === UI КОМПОНЕНТЫ ===

    /**
     * Основной контент с чистой навигацией и reactive состояниями.
     */
    @Composable
    private fun MainAppContent() {
        val navController = rememberNavController()

        Scaffold(
            topBar = {
                AppTopBar(
                    batteryLevel = uiStateManager.batteryPercent.value,
                    isBluetoothEnabled = uiStateManager.isBluetoothEnabled.value,
                    isDeviceConnected = uiStateManager.isDeviceConnected.value,
                    allPermissionsGranted = uiStateManager.allPermissionsGranted.value,
                    onPermissionsClick = ::handlePermissionsIconClick,
                    onBluetoothClick = ::handleConnectToDevice,
                    onDebugClick = {
                        uiStateManager.toggleDebugPanel()
                        Log.d(TAG, "Debug panel toggled: ${uiStateManager.showDebugPanel.value}")
                    },
                    showDebugButton = BuildConfig.DEBUG,
                    onTitleClick = {
                        navController.navigate("main_screen") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                        if (uiStateManager.showDebugPanel.value) {
                            uiStateManager.toggleDebugPanel()
                        }
                    },
                    bluetoothHelper = appInitializer.bluetoothHelper
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
                        temp1 = uiStateManager.temp1.value,
                        temp2 = uiStateManager.temp2.value,
                        hallState = uiStateManager.hallState.value,
                        acc = uiStateManager.accelerometerData.value,
                        onNavigateToLogs = { navController.navigate("log_screen") },
                        bluetoothHelper = appInitializer.bluetoothHelper,
                        locationManager = appInitializer.enhancedLocationManager,
                    )
                }

                composable("log_screen") {
                    LogScreen(navController = navController)
                }
            }

            // Панель отладки с reactive состоянием
            if (uiStateManager.showDebugPanel.value && BuildConfig.DEBUG) {
                DebugControlPanel(
                    bluetoothHelper = appInitializer.bluetoothHelper,
                    locationManager = appInitializer.enhancedLocationManager
                )
            }
        }
    }

    // === ОБРАБОТКА ДАННЫХ ARDUINO ===

    /**
     * Максимально упрощённая обработка данных через DataManager и UIStateManager.
     *
     * Вся сложная логика инкапсулирована в соответствующих компонентах!
     */
    fun handleReceivedData(data: String) {
        Log.d(TAG, "🔴 Получены данные: '$data'")

        // Создаём контейнер состояний и передаём в DataManager
        val uiStates = uiStateManager.createDataManagerUIStates()
        appInitializer.dataManager.processArduinoData(data, uiStates)
    }

    // === ОБРАБОТКА КОМАНД И СОБЫТИЙ ===

    /**
     * Отправляет команду на Arduino через BluetoothHelper.
     */
    private fun sendCommandToDevice(command: String) {
        Log.d(TAG, "📤 Отправляем команду: $command")
        appInitializer.bluetoothHelper.sendCommand(command)
    }

    /**
     * Обрабатывает клик по иконке разрешений.
     */
    private fun handlePermissionsIconClick() {
        if (!uiStateManager.allPermissionsGranted.value) {
            appInitializer.permissionHelper.requestPermissions()
        } else {
            Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Обрабатывает клик по Bluetooth иконке.
     */
    private fun handleConnectToDevice() {
        appInitializer.bluetoothHelper.showDeviceSelectionDialog(this) { device ->
            appInitializer.bluetoothHelper.connectToDevice(device) { success, message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                uiStateManager.updateDeviceConnected(success)
                if (success) {
                    appInitializer.bluetoothHelper.listenForData { data ->
                        handleReceivedData(data)
                    }
                }
            }
        }
    }

    // === ОБРАБОТКА ИЗМЕНЕНИЙ СОСТОЯНИЯ ===

    /**
     * Обрабатывает изменения GPS состояния.
     */
    private fun handleGpsStateChange(isEnabled: Boolean) {
        if (isEnabled && uiStateManager.allPermissionsGranted.value) {
            appInitializer.initializeAppFeatures(uiStateManager.coordinates)
        }
    }

    /**
     * Обрабатывает включение GPS пользователем.
     */
    private fun handleLocationEnabled() {
        val actualState = appInitializer.enhancedLocationManager.forceLocationStatusCheck()
        uiStateManager.updateLocationServiceEnabled(actualState)

        if (actualState && uiStateManager.allPermissionsGranted.value) {
            appInitializer.initializeAppFeatures(uiStateManager.coordinates)
        }

        Log.d(TAG, "✅ GPS проверен: $actualState")
    }

    // === ОБРАБОТКА РАЗРЕШЕНИЙ ===

    /**
     * Упрощённая обработка отказа в разрешениях.
     */
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
            appInitializer.permissionHelper.requestPermissions()
        }
    }

    /**
     * Перенаправляет в настройки приложения.
     */
    private fun redirectToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    // === ОБРАБОТКА ОШИБОК ===

    /**
     * Показывает ошибку инициализации с диагностикой.
     */
    private fun showInitializationError() {
        Toast.makeText(
            this,
            "Ошибка инициализации. Некоторые функции могут быть недоступны.",
            Toast.LENGTH_LONG
        ).show()

        // В DEBUG режиме показываем детальную диагностику
        if (BuildConfig.DEBUG && ::appInitializer.isInitialized) {
            Log.e(TAG, "=== ДИАГНОСТИКА ОШИБКИ ИНИЦИАЛИЗАЦИИ ===")
            Log.e(TAG, appInitializer.getInitializationStatus().getDetailedReport())
            Log.e(TAG, "=========================================")
        }
    }

    // === НАСТРОЙКА OSMDROID ===

    /**
     * Настраивает кэш OpenStreetMap для корректной работы карт.
     */
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

        Log.d(TAG, "🗺️ OSMDroid кэш настроен: ${cacheDir.absolutePath}")
    }

    // === ОЧИСТКА РЕСУРСОВ ===

    /**
     * Максимально упрощённая очистка через специализированные компоненты.
     * Каждый компонент отвечает за освобождение своих ресурсов.
     */
    private fun cleanup() {
        try {
            Log.d(TAG, "🧹 Начинаем очистку MainActivity...")

            // Очищаем AppInitializer (включает все подкомпоненты)
            if (::appInitializer.isInitialized) {
                appInitializer.cleanup()
            }

            // Сбрасываем UI состояния
            if (::uiStateManager.isInitialized) {
                uiStateManager.resetAllStates()
            }

            Log.d(TAG, "✅ MainActivity успешно очищена")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки ресурсов: ${e.message}")
        }
    }

    // === ДИАГНОСТИКА И МОНИТОРИНГ ===

    /**
     * Возвращает краткий статус системы для отладки и мониторинга.
     */
    fun getSystemStatus(): String {
        if (!::appInitializer.isInitialized || !::uiStateManager.isInitialized) {
            return "Компоненты не инициализированы"
        }

        return buildString {
            appendLine("=== SYSTEM STATUS ===")
            appendLine("UIStateManager: ${uiStateManager.getStatusReport()}")
            appendLine("AppInitializer: ${appInitializer.getStatusReport()}")
            appendLine("Система готова: ${uiStateManager.isSystemReady()}")
            appendLine("Данные активны: ${uiStateManager.hasActiveData()}")
            appendLine("===================")
        }
    }

    /**
     * Возвращает детальную диагностику всех компонентов для troubleshooting.
     */
    fun getDetailedDiagnostics(): String {
        if (!::appInitializer.isInitialized || !::uiStateManager.isInitialized) {
            return "Диагностика недоступна: компоненты не инициализированы"
        }

        return buildString {
            appendLine(getSystemStatus())
            appendLine()
            appendLine("=== DETAILED DIAGNOSTICS ===")
            appendLine()
            appendLine("UI STATES:")
            appendLine(uiStateManager.getDetailedStateInfo())
            appendLine()
            appendLine("INITIALIZATION:")
            appendLine(appInitializer.getInitializationStatus().getDetailedReport())
            appendLine()
            appendLine("DATA MANAGER:")
            appendLine(appInitializer.dataManager.getStatusReport())
            appendLine()
            appendLine("LOCATION MANAGER:")
            appendLine(appInitializer.enhancedLocationManager.getStatusSummary())
            appendLine()
            appendLine("BLUETOOTH HELPER:")
            appendLine(appInitializer.bluetoothHelper.getConnectionStatistics())
            appendLine()
            appendLine("TEMPERATURE MONITOR:")
            appendLine(appInitializer.temperatureMonitor.getStatusReport())
            appendLine("=============================")
        }
    }

    /**
     * Проверяет общую готовность системы к полноценной работе.
     */
    fun isSystemReady(): Boolean {
        return ::appInitializer.isInitialized &&
                ::uiStateManager.isInitialized &&
                appInitializer.isSystemReady() &&
                uiStateManager.isSystemReady()
    }

    /**
     * Возвращает краткие рекомендации по решению проблем системы.
     */
    fun getSystemRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        if (!::appInitializer.isInitialized) {
            recommendations.add("Перезапустите приложение")
            return recommendations
        }

        if (!uiStateManager.allPermissionsGranted.value) {
            recommendations.add("Предоставьте все необходимые разрешения")
        }

        if (!uiStateManager.isLocationServiceEnabled.value) {
            recommendations.add("Включите GPS в настройках устройства")
        }

        if (!uiStateManager.isBluetoothEnabled.value) {
            recommendations.add("Включите Bluetooth")
        }

        if (!uiStateManager.isDeviceConnected.value) {
            recommendations.add("Подключите Arduino устройство")
        }

        if (!uiStateManager.hasActiveData()) {
            recommendations.add("Проверьте передачу данных от Arduino")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Система работает нормально")
        }

        return recommendations
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}