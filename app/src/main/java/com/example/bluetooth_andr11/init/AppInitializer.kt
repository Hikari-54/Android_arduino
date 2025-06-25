package com.example.bluetooth_andr11.init

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.MutableState
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.data.DataManager
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.location.LocationMode
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.monitoring.TemperatureMonitor
import com.example.bluetooth_andr11.permissions.PermissionHelper
import com.google.android.gms.location.LocationServices

/**
 * Централизованный инициализатор всех компонентов приложения.
 *
 * Управляет полным жизненным циклом инициализации:
 * - Создание и настройка всех core компонентов в правильном порядке
 * - Настройка мониторинга Bluetooth и GPS с reactive обновлениями
 * - Проверка и запрос разрешений Android
 * - Инициализация основных функций после получения разрешений
 * - Автозапуск симуляции в DEBUG режиме
 * - Graceful error handling и fallback механизмы
 *
 * Архитектурные принципы:
 * - Dependency injection pattern для всех компонентов
 * - Четкое разделение между инициализацией и бизнес-логикой
 * - Thread-safe операции с reactive состояниями
 * - Автоматическое управление зависимостями между компонентами
 * - Comprehensive logging всех этапов инициализации
 *
 * Последовательность инициализации (критически важна):
 * 1. PermissionHelper - базовые разрешения Android
 * 2. EnhancedLocationManager - GPS и местоположение
 * 3. BluetoothHelper - подключение к Arduino
 * 4. TemperatureMonitor - анализ температурных данных
 * 5. DataManager - централизованная обработка данных
 * 6. Monitoring setup - настройка всех мониторингов
 * 7. Features initialization - активация основных функций
 */
class AppInitializer(
    private val activity: ComponentActivity,
    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
) {
    companion object {
        private const val TAG = "AppInitializer"

        /** Задержка автозапуска симуляции в DEBUG режиме */
        private const val SIMULATION_AUTO_START_DELAY_MS = 3000L

        /** Время ожидания инициализации компонентов */
        private const val COMPONENT_INIT_TIMEOUT_MS = 10000L
    }

    // === ИНИЦИАЛИЗИРОВАННЫЕ КОМПОНЕНТЫ ===

    /** Управление разрешениями Android */
    lateinit var permissionHelper: PermissionHelper
        private set

    /** Расширенное управление GPS и местоположением */
    lateinit var enhancedLocationManager: EnhancedLocationManager
        private set

    /** Управление Bluetooth подключением к Arduino */
    lateinit var bluetoothHelper: BluetoothHelper
        private set

    /** Интеллектуальный мониторинг температуры */
    lateinit var temperatureMonitor: TemperatureMonitor
        private set

    /** Централизованная обработка данных Arduino */
    lateinit var dataManager: DataManager
        private set

    // === СТАТУС ИНИЦИАЛИЗАЦИИ ===

    /** Флаг завершения инициализации всех компонентов */
    @Volatile
    private var isFullyInitialized = false

    /** Время начала инициализации для метрик производительности */
    private var initStartTime = 0L

    /** Счётчик успешно инициализированных компонентов */
    private var initializedComponentsCount = 0

    /** Общее количество компонентов для инициализации */
    private val totalComponentsCount = 5

    // === ОСНОВНЫЕ МЕТОДЫ ИНИЦИАЛИЗАЦИИ ===

    /**
     * Выполняет полную инициализацию всех компонентов приложения.
     *
     * Процесс инициализации:
     * 1. Создание core компонентов в правильном порядке зависимостей
     * 2. Валидация успешности инициализации каждого компонента
     * 3. Настройка мониторинга систем (Bluetooth, GPS)
     * 4. Логирование метрик производительности инициализации
     * 5. Установка флага готовности системы
     *
     * @return true если все компоненты инициализированы успешно
     */
    fun initialize(): Boolean {
        initStartTime = System.currentTimeMillis()
        Log.d(TAG, "🚀 Начинаем полную инициализацию приложения...")

        return try {
            // Инициализируем компоненты в строгом порядке зависимостей
            val success = initializeAllComponents()

            if (success) {
                // Настройка мониторинга после успешной инициализации
                setupAllMonitoring()

                isFullyInitialized = true
                logInitializationSuccess()
            } else {
                handleInitializationFailure()
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "❌ Критическая ошибка инициализации: ${e.message}")
            handleInitializationFailure()
            false
        }
    }

    /**
     * Инициализирует все core компоненты в правильном порядке зависимостей.
     *
     * @return true если все компоненты созданы успешно
     */
    private fun initializeAllComponents(): Boolean {
        Log.d(TAG, "🔧 Инициализация core компонентов...")

        // 1. PermissionHelper - базовые разрешения (без зависимостей)
        if (!initializePermissionHelper()) return false

        // 2. EnhancedLocationManager - GPS функциональность
        if (!initializeLocationManager()) return false

        // 3. BluetoothHelper - Arduino подключение
        if (!initializeBluetoothHelper()) return false

        // 4. TemperatureMonitor - анализ температуры (зависит от Bluetooth + Location)
        if (!initializeTemperatureMonitor()) return false

        // 5. DataManager - обработка данных (зависит от всех предыдущих)
        if (!initializeDataManager()) return false

        Log.d(TAG, "✅ Все $totalComponentsCount компонентов инициализированы успешно")
        return true
    }

    // === ИНИЦИАЛИЗАЦИЯ ОТДЕЛЬНЫХ КОМПОНЕНТОВ ===

    /**
     * Инициализирует PermissionHelper для управления разрешениями Android.
     */
    private fun initializePermissionHelper(): Boolean {
        return try {
            permissionHelper = PermissionHelper(activity, requestPermissionsLauncher)
            initializedComponentsCount++
            Log.d(
                TAG,
                "✅ PermissionHelper инициализирован ($initializedComponentsCount/$totalComponentsCount)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации PermissionHelper: ${e.message}")
            false
        }
    }

    /**
     * Инициализирует EnhancedLocationManager для GPS и местоположения.
     */
    private fun initializeLocationManager(): Boolean {
        return try {
            enhancedLocationManager = EnhancedLocationManager(
                context = activity,
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
            )
            initializedComponentsCount++
            Log.d(
                TAG,
                "✅ EnhancedLocationManager инициализирован ($initializedComponentsCount/$totalComponentsCount)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации LocationManager: ${e.message}")
            false
        }
    }

    /**
     * Инициализирует BluetoothHelper для подключения к Arduino.
     */
    private fun initializeBluetoothHelper(): Boolean {
        return try {
            bluetoothHelper = BluetoothHelper(activity)
            initializedComponentsCount++
            Log.d(
                TAG,
                "✅ BluetoothHelper инициализирован ($initializedComponentsCount/$totalComponentsCount)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации BluetoothHelper: ${e.message}")
            false
        }
    }

    /**
     * Инициализирует TemperatureMonitor для анализа температурных данных.
     */
    private fun initializeTemperatureMonitor(): Boolean {
        return try {
            temperatureMonitor =
                TemperatureMonitor(activity, bluetoothHelper, enhancedLocationManager)
            initializedComponentsCount++
            Log.d(
                TAG,
                "✅ TemperatureMonitor инициализирован ($initializedComponentsCount/$totalComponentsCount)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации TemperatureMonitor: ${e.message}")
            false
        }
    }

    /**
     * Инициализирует DataManager для централизованной обработки данных.
     */
    private fun initializeDataManager(): Boolean {
        return try {
            dataManager =
                DataManager(activity, bluetoothHelper, enhancedLocationManager, temperatureMonitor)
            initializedComponentsCount++
            Log.d(
                TAG,
                "✅ DataManager инициализирован ($initializedComponentsCount/$totalComponentsCount)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации DataManager: ${e.message}")
            false
        }
    }

    // === НАСТРОЙКА МОНИТОРИНГА ===

    /**
     * Настраивает мониторинг всех критически важных систем.
     */
    private fun setupAllMonitoring() {
        Log.d(TAG, "📡 Настройка системы мониторинга...")
        // Мониторинг будет настроен в MainActivity через dedicated методы
        // чтобы не создавать дополнительные зависимости в AppInitializer
        Log.d(TAG, "✅ Мониторинг подготовлен к активации")
    }

    // === ПРОВЕРКА РАЗРЕШЕНИЙ ===

    /**
     * Выполняет начальную проверку разрешений и запускает процесс получения недостающих.
     *
     * @param onPermissionsResult callback с результатом проверки разрешений
     */
    fun checkInitialPermissions(onPermissionsResult: (Boolean) -> Unit) {
        if (!::permissionHelper.isInitialized) {
            Log.e(TAG, "❌ PermissionHelper не инициализирован")
            onPermissionsResult(false)
            return
        }

        val hasAllPermissions = permissionHelper.hasAllPermissions()

        if (hasAllPermissions) {
            Log.d(TAG, "✅ Все разрешения уже предоставлены")
            onPermissionsResult(true)
        } else {
            Log.d(TAG, "⚠️ Отсутствуют некоторые разрешения, запрашиваем...")
            permissionHelper.requestPermissions()
            // Результат будет обработан через ActivityResultLauncher в MainActivity
        }
    }

    // === ИНИЦИАЛИЗАЦИЯ ОСНОВНЫХ ФУНКЦИЙ ===

    /**
     * Активирует основные функции приложения после получения всех разрешений.
     *
     * Включает:
     * - GPS обновления с callback для координат
     * - Принудительное обновление местоположения
     * - Установка оптимального режима GPS
     * - Логирование активации функций
     *
     * @param coordinatesState reactive состояние для координат
     */
    fun initializeAppFeatures(coordinatesState: MutableState<String>) {
        if (!isFullyInitialized) {
            Log.w(TAG, "⚠️ Попытка активации функций до завершения инициализации")
            return
        }

        try {
            Log.d(TAG, "🚀 Активация основных функций приложения...")

            // Запускаем GPS обновления с reactive обновлением координат
            enhancedLocationManager.startLocationUpdates { newCoordinates ->
                coordinatesState.value = newCoordinates
                Log.d(TAG, "📍 Координаты обновлены: $newCoordinates")
            }

            // Принудительно обновляем местоположение для быстрого получения данных
            enhancedLocationManager.forceLocationUpdate(LocationMode.BALANCED)

            // Устанавливаем рекомендуемый режим GPS
            val recommendedMode = enhancedLocationManager.getRecommendedMode()
            enhancedLocationManager.setLocationMode(recommendedMode)

            Log.d(TAG, "🎯 Установлен оптимальный режим GPS: $recommendedMode")
            Log.d(TAG, "✅ Основные функции успешно активированы")

            // Логируем активацию функций
            LogModule.logSystemEvent(
                activity, bluetoothHelper, enhancedLocationManager,
                "Основные функции приложения активированы", "СИСТЕМА"
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка активации основных функций: ${e.message}")
        }
    }

    // === НАСТРОЙКА МОНИТОРИНГА BLUETOOTH ===

    /**
     * Настраивает мониторинг Bluetooth с reactive обновлениями состояний.
     *
     * @param bluetoothEnabledState reactive состояние Bluetooth адаптера
     * @param deviceConnectedState reactive состояние подключения устройства
     * @param onDataReceived callback для обработки данных от Arduino
     */
    fun setupBluetoothMonitoring(
        bluetoothEnabledState: MutableState<Boolean>,
        deviceConnectedState: MutableState<Boolean>,
        onDataReceived: (String) -> Unit
    ) {
        if (!::bluetoothHelper.isInitialized) {
            Log.e(TAG, "❌ BluetoothHelper не инициализирован для мониторинга")
            return
        }

        Log.d(TAG, "🔵 Настройка Bluetooth мониторинга...")

        bluetoothHelper.monitorBluetoothStatus(
            activity,
            enhancedLocationManager
        ) { isEnabled, isConnected ->
            bluetoothEnabledState.value = isEnabled
            deviceConnectedState.value = isConnected

            when {
                isConnected -> {
                    Log.d(TAG, "🟢 Bluetooth подключен, начинаем прослушивание данных")
                    bluetoothHelper.listenForData { data ->
                        onDataReceived(data)
                    }
                }

                isEnabled && !isConnected -> {
                    Log.d(TAG, "🟡 Bluetooth включен, но устройство не подключено")
                    bluetoothHelper.showDeviceSelectionDialog(activity) { device ->
                        bluetoothHelper.connectToDevice(device) { success, message ->
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                            deviceConnectedState.value = success
                        }
                    }
                }

                else -> {
                    Log.d(TAG, "🔴 Bluetooth отключен или недоступен")
                }
            }
        }

        Log.d(TAG, "✅ Bluetooth мониторинг настроен")
    }

    // === НАСТРОЙКА МОНИТОРИНГА GPS ===

    /**
     * Настраивает мониторинг GPS с reactive обновлениями и обработкой изменений состояния.
     *
     * @param locationEnabledState reactive состояние GPS
     * @param onLocationEnabledChanged callback для обработки изменений GPS
     */
    fun setupGpsMonitoring(
        locationEnabledState: MutableState<Boolean>,
        onLocationEnabledChanged: (Boolean) -> Unit
    ) {
        if (!::enhancedLocationManager.isInitialized) {
            Log.e(TAG, "❌ LocationManager не инициализирован для мониторинга")
            return
        }

        Log.d(TAG, "🛰️ Настройка GPS мониторинга...")

        enhancedLocationManager.setLocationStatusChangeListener { isEnabled ->
            Log.d(TAG, "📍 GPS состояние изменилось: $isEnabled")

            activity.runOnUiThread {
                locationEnabledState.value = isEnabled
                onLocationEnabledChanged(isEnabled)

                // Показываем пользователю уведомления об изменениях GPS
                if (!isEnabled) {
                    Toast.makeText(
                        activity,
                        "⚠️ GPS отключен! Функции местоположения недоступны.",
                        Toast.LENGTH_LONG
                    ).show()

                    LogModule.logGpsStateChange(
                        activity,
                        false,
                        "GPS отключен пользователем во время работы"
                    )
                } else {
                    Toast.makeText(
                        activity,
                        "✅ GPS включен! Функции местоположения восстановлены.",
                        Toast.LENGTH_SHORT
                    ).show()

                    LogModule.logGpsStateChange(
                        activity,
                        true,
                        "GPS включен пользователем"
                    )
                }
            }
        }

        // Проверяем начальное состояние GPS
        val initialState = enhancedLocationManager.forceLocationStatusCheck()
        locationEnabledState.value = initialState
        LogModule.logGpsStateChange(activity, initialState, "Проверка при инициализации")

        Log.d(TAG, "✅ GPS мониторинг настроен, начальное состояние: $initialState")
    }

    // === АВТОЗАПУСК СИМУЛЯЦИИ (DEBUG) ===

    /**
     * Автоматически запускает симуляцию Arduino в DEBUG режиме если устройство не подключено.
     * Обеспечивает удобную разработку без необходимости реального Arduino.
     */
    fun autoStartSimulationIfNeeded() {
        // Блокируем симуляцию в RELEASE режиме для безопасности
        if (!BuildConfig.DEBUG) {
            Log.d(TAG, "🚫 RELEASE режим: автозапуск симуляции заблокирован")
            return
        }

        if (!::bluetoothHelper.isInitialized) {
            Log.w(TAG, "⚠️ BluetoothHelper не инициализирован для автозапуска симуляции")
            return
        }

        if (!bluetoothHelper.isDeviceConnected) {
            Handler(Looper.getMainLooper()).postDelayed({
                // Двойная проверка DEBUG режима и состояния подключения
                if (BuildConfig.DEBUG && !bluetoothHelper.isDeviceConnected) {
                    bluetoothHelper.enableSimulationMode(true)

                    Toast.makeText(
                        activity,
                        "🔧 Запущена симуляция Arduino (DEBUG)",
                        Toast.LENGTH_LONG
                    ).show()

                    LogModule.logSystemEvent(
                        activity, bluetoothHelper, enhancedLocationManager,
                        "Автозапуск симуляции Arduino (DEBUG режим)", "ОТЛАДКА"
                    )

                    Log.d(TAG, "🤖 Автозапуск симуляции выполнен успешно")
                }
            }, SIMULATION_AUTO_START_DELAY_MS)
        } else {
            Log.d(TAG, "🔗 Устройство уже подключено, симуляция не требуется")
        }
    }

    // === ДИАГНОСТИКА И СТАТИСТИКА ===

    /**
     * Возвращает статус инициализации всех компонентов.
     *
     * @return объект InitializationStatus с детальной информацией
     */
    fun getInitializationStatus(): InitializationStatus {
        val initDuration = if (initStartTime > 0) {
            System.currentTimeMillis() - initStartTime
        } else 0L

        return InitializationStatus(
            isFullyInitialized = isFullyInitialized,
            initializedComponentsCount = initializedComponentsCount,
            totalComponentsCount = totalComponentsCount,
            initializationDurationMs = initDuration,
            hasPermissionHelper = ::permissionHelper.isInitialized,
            hasLocationManager = ::enhancedLocationManager.isInitialized,
            hasBluetoothHelper = ::bluetoothHelper.isInitialized,
            hasTemperatureMonitor = ::temperatureMonitor.isInitialized,
            hasDataManager = ::dataManager.isInitialized
        )
    }

    /**
     * Проверяет готовность системы к работе.
     */
    fun isSystemReady(): Boolean {
        return isFullyInitialized &&
                ::permissionHelper.isInitialized &&
                ::enhancedLocationManager.isInitialized &&
                ::bluetoothHelper.isInitialized &&
                ::temperatureMonitor.isInitialized &&
                ::dataManager.isInitialized
    }

    /**
     * Возвращает краткий отчёт о состоянии инициализации.
     */
    fun getStatusReport(): String {
        val status = getInitializationStatus()
        return "AppInitializer: ${if (status.isFullyInitialized) "✅" else "⏳"} | " +
                "Компоненты: ${status.initializedComponentsCount}/${status.totalComponentsCount} | " +
                "Время: ${status.initializationDurationMs}мс | " +
                "Готовность: ${if (isSystemReady()) "🟢" else "🔴"}"
    }

    // === ОБРАБОТКА ОШИБОК ===

    /**
     * Логирует успешную инициализацию с метриками производительности.
     */
    private fun logInitializationSuccess() {
        val duration = System.currentTimeMillis() - initStartTime

        Log.i(TAG, "🎉 Инициализация завершена успешно за ${duration}мс")
        Log.i(TAG, "✅ Все $totalComponentsCount компонентов готовы к работе")

        // Логируем успешную инициализацию в систему событий
        LogModule.logSystemEvent(
            activity, bluetoothHelper, enhancedLocationManager,
            "Инициализация приложения завершена успешно за ${duration}мс", "СИСТЕМА"
        )
    }

    /**
     * Обрабатывает ошибки инициализации с логированием и fallback механизмами.
     */
    private fun handleInitializationFailure() {
        val duration = System.currentTimeMillis() - initStartTime

        Log.e(TAG, "❌ Инициализация завершилась с ошибками за ${duration}мс")
        Log.e(
            TAG,
            "⚠️ Инициализировано компонентов: $initializedComponentsCount/$totalComponentsCount"
        )

        // Показываем пользователю критическое уведомление
        Toast.makeText(
            activity,
            "Ошибка инициализации приложения. Некоторые функции могут быть недоступны.",
            Toast.LENGTH_LONG
        ).show()

        // Логируем ошибку для диагностики
        try {
            LogModule.logEvent(
                activity,
                "КРИТИЧЕСКАЯ ОШИБКА: Инициализация завершилась неудачно. " +
                        "Компонентов: $initializedComponentsCount/$totalComponentsCount"
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Не удалось записать лог ошибки инициализации: ${e.message}")
        }
    }

    /**
     * Выполняет частичную очистку ресурсов при ошибках инициализации.
     */
    fun cleanup() {
        try {
            if (::enhancedLocationManager.isInitialized) {
                enhancedLocationManager.cleanup()
            }
            if (::bluetoothHelper.isInitialized) {
                bluetoothHelper.cleanup()
            }
            if (::temperatureMonitor.isInitialized) {
                temperatureMonitor.reset()
            }
            if (::dataManager.isInitialized) {
                dataManager.resetStatistics()
            }

            isFullyInitialized = false
            Log.d(TAG, "🧹 AppInitializer очищен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки AppInitializer: ${e.message}")
        }
    }

    // === DATA CLASSES ===

    /**
     * Подробная информация о статусе инициализации для диагностики.
     *
     * @param isFullyInitialized завершена ли инициализация полностью
     * @param initializedComponentsCount количество инициализированных компонентов
     * @param totalComponentsCount общее количество компонентов
     * @param initializationDurationMs время инициализации в миллисекундах
     * @param hasPermissionHelper инициализирован ли PermissionHelper
     * @param hasLocationManager инициализирован ли LocationManager
     * @param hasBluetoothHelper инициализирован ли BluetoothHelper
     * @param hasTemperatureMonitor инициализирован ли TemperatureMonitor
     * @param hasDataManager инициализирован ли DataManager
     */
    data class InitializationStatus(
        val isFullyInitialized: Boolean,
        val initializedComponentsCount: Int,
        val totalComponentsCount: Int,
        val initializationDurationMs: Long,
        val hasPermissionHelper: Boolean,
        val hasLocationManager: Boolean,
        val hasBluetoothHelper: Boolean,
        val hasTemperatureMonitor: Boolean,
        val hasDataManager: Boolean
    ) {
        /**
         * Возвращает процент завершённости инициализации.
         */
        fun getCompletionPercentage(): Int {
            return if (totalComponentsCount > 0) {
                (initializedComponentsCount * 100) / totalComponentsCount
            } else 0
        }

        /**
         * Проверяет наличие критических проблем инициализации.
         */
        fun hasCriticalIssues(): Boolean {
            return !hasPermissionHelper || !hasLocationManager || !hasBluetoothHelper
        }

        /**
         * Возвращает список отсутствующих компонентов.
         */
        fun getMissingComponents(): List<String> {
            val missing = mutableListOf<String>()
            if (!hasPermissionHelper) missing.add("PermissionHelper")
            if (!hasLocationManager) missing.add("LocationManager")
            if (!hasBluetoothHelper) missing.add("BluetoothHelper")
            if (!hasTemperatureMonitor) missing.add("TemperatureMonitor")
            if (!hasDataManager) missing.add("DataManager")
            return missing
        }

        /**
         * Возвращает детальный отчёт о состоянии инициализации.
         */
        fun getDetailedReport(): String {
            return buildString {
                appendLine("🔧 ОТЧЁТ ИНИЦИАЛИЗАЦИИ:")
                appendLine("═══════════════════════════")
                appendLine("• Статус: ${if (isFullyInitialized) "✅ Завершено" else "⏳ В процессе"}")
                appendLine("• Прогресс: $initializedComponentsCount/$totalComponentsCount (${getCompletionPercentage()}%)")
                appendLine("• Время: ${initializationDurationMs}мс")
                appendLine("• PermissionHelper: ${if (hasPermissionHelper) "✅" else "❌"}")
                appendLine("• LocationManager: ${if (hasLocationManager) "✅" else "❌"}")
                appendLine("• BluetoothHelper: ${if (hasBluetoothHelper) "✅" else "❌"}")
                appendLine("• TemperatureMonitor: ${if (hasTemperatureMonitor) "✅" else "❌"}")
                appendLine("• DataManager: ${if (hasDataManager) "✅" else "❌"}")

                if (hasCriticalIssues()) {
                    appendLine("⚠️ Обнаружены критические проблемы!")
                    appendLine("• Отсутствуют: ${getMissingComponents().joinToString(", ")}")
                }
                appendLine("═══════════════════════════")
            }
        }
    }
}