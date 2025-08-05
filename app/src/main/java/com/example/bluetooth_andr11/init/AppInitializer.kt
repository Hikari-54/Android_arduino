package com.example.bluetooth_andr11.init

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.MutableState
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.auth.AuthenticationManager
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
 * 4. AuthenticationManager - аутентификация доставочных сумок
 * 5. TemperatureMonitor - анализ температурных данных
 * 6. DataManager - централизованная обработка данных
 * 7. Monitoring setup - настройка всех мониторингов
 * 8. Features initialization - активация основных функций
 */
class AppInitializer(
    private val activity: ComponentActivity,
    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
) {
    companion object {
        private const val TAG = "AppInitializer"

        /** Задержка автозапуска симуляции в DEBUG режиме */
        private const val SIMULATION_AUTO_START_DELAY_MS = 3000L
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

    /** Менеджер аутентификации доставочных сумок */
    lateinit var authenticationManager: AuthenticationManager
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
    private val totalComponentsCount =
        6  // Обновлено: было 5, стало 6 (добавлен AuthenticationManager)

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

        // 4. AuthenticationManager - аутентификация сумок (зависит от BT + Location)
        if (!initializeAuthenticationManager()) return false

        // 5. TemperatureMonitor - анализ температуры (зависит от Bluetooth + Location)
        if (!initializeTemperatureMonitor()) return false

        // 6. DataManager - обработка данных (зависит от всех предыдущих)
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
            Log.d(TAG, "🔐 Инициализация PermissionHelper...")

            permissionHelper = PermissionHelper(
                context = activity,
                requestPermissionLauncher = requestPermissionsLauncher
            )

            initializedComponentsCount++
            Log.i(TAG, "✅ PermissionHelper инициализирован успешно")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации PermissionHelper: ${e.message}", e)
            false
        }
    }

    /**
     * Инициализирует EnhancedLocationManager для работы с GPS и местоположением.
     */
    private fun initializeLocationManager(): Boolean {
        return try {
            Log.d(TAG, "📍 Инициализация EnhancedLocationManager...")

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
            enhancedLocationManager = EnhancedLocationManager(
                context = activity,
                fusedLocationClient = fusedLocationClient
            )

            initializedComponentsCount++
            Log.i(TAG, "✅ EnhancedLocationManager инициализирован успешно")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации EnhancedLocationManager: ${e.message}", e)
            false
        }
    }

    /**
     * Инициализирует BluetoothHelper для подключения к Arduino устройствам.
     */
    private fun initializeBluetoothHelper(): Boolean {
        return try {
            Log.d(TAG, "📡 Инициализация BluetoothHelper...")

            bluetoothHelper = BluetoothHelper(activity)

            initializedComponentsCount++
            Log.i(TAG, "✅ BluetoothHelper инициализирован успешно")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации BluetoothHelper: ${e.message}", e)
            false
        }
    }

    /**
     * Инициализирует AuthenticationManager для аутентификации доставочных сумок.
     *
     * @return true если инициализация прошла успешно
     */
    private fun initializeAuthenticationManager(): Boolean {
        return try {
            Log.d(TAG, "🔐 Инициализация AuthenticationManager...")

            authenticationManager = AuthenticationManager(
                context = activity,
                bluetoothHelper = bluetoothHelper,
                locationManager = enhancedLocationManager
            )

            // Связываем AuthenticationManager с BluetoothHelper
            bluetoothHelper.setAuthenticationManager(authenticationManager)

            initializedComponentsCount++
            Log.i(TAG, "✅ AuthenticationManager инициализирован успешно")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации AuthenticationManager: ${e.message}", e)
            false
        }
    }

    /**
     * Инициализирует TemperatureMonitor для анализа температурных данных.
     */
    private fun initializeTemperatureMonitor(): Boolean {
        return try {
            Log.d(TAG, "🌡️ Инициализация TemperatureMonitor...")

            temperatureMonitor = TemperatureMonitor(
                context = activity,
                bluetoothHelper = bluetoothHelper,
                locationManager = enhancedLocationManager
            )

            initializedComponentsCount++
            Log.i(TAG, "✅ TemperatureMonitor инициализирован успешно")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации TemperatureMonitor: ${e.message}", e)
            false
        }
    }

    /**
     * Инициализирует DataManager для централизованной обработки данных Arduino.
     */
    private fun initializeDataManager(): Boolean {
        return try {
            Log.d(TAG, "💾 Инициализация DataManager...")

            dataManager = DataManager(
                context = activity,
                bluetoothHelper = bluetoothHelper,
                locationManager = enhancedLocationManager,
                temperatureMonitor = temperatureMonitor
            )

            initializedComponentsCount++
            Log.i(TAG, "✅ DataManager инициализирован успешно")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации DataManager: ${e.message}", e)
            false
        }
    }

    // === НАСТРОЙКА МОНИТОРИНГА ===

    /**
     * Настраивает все системы мониторинга после успешной инициализации компонентов.
     */
    private fun setupAllMonitoring() {
        try {
            Log.d(TAG, "📡 Настройка систем мониторинга...")

            // Мониторинг Bluetooth состояния
            bluetoothHelper.monitorBluetoothStatus(
                context = activity,
                locationManager = enhancedLocationManager
            ) { isEnabled, isConnected ->
                Log.d(TAG, "📡 Bluetooth статус: enabled=$isEnabled, connected=$isConnected")
            }

            // Мониторинг GPS состояния - startLocationUpdates принимает callback со строкой координат
            enhancedLocationManager.startLocationUpdates { coordinates ->
                Log.d(TAG, "📍 GPS обновление: $coordinates")
            }

            Log.i(TAG, "✅ Мониторинг систем настроен успешно")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка настройки мониторинга: ${e.message}", e)
        }
    }

    // === ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ===

    /**
     * Автозапуск симуляции в DEBUG режиме с задержкой.
     */
    fun autoStartSimulationIfNeeded() {
        if (BuildConfig.DEBUG && ::bluetoothHelper.isInitialized) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    bluetoothHelper.enableSimulationMode(true)
                    Log.i(TAG, "🤖 Автозапуск симуляции в DEBUG режиме")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка автозапуска симуляции: ${e.message}")
                }
            }, SIMULATION_AUTO_START_DELAY_MS)
        }
    }

    /**
     * Настраивает Bluetooth мониторинг с reactive обновлениями.
     */
    fun setupBluetoothMonitoring(
        bluetoothEnabledState: MutableState<Boolean>,
        deviceConnectedState: MutableState<Boolean>,
        onDataReceived: (String) -> Unit
    ) {
        bluetoothHelper.monitorBluetoothStatus(
            context = activity,
            locationManager = enhancedLocationManager
        ) { isEnabled, isConnected ->
            bluetoothEnabledState.value = isEnabled
            deviceConnectedState.value = isConnected
        }

        bluetoothHelper.listenForData(onDataReceived)
    }

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

        // Настраиваем слушатель изменений статуса GPS
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
                        activity, false,
                        "GPS отключен пользователем во время работы"
                    )
                } else {
                    Toast.makeText(
                        activity,
                        "✅ GPS включен! Функции местоположения восстановлены.",
                        Toast.LENGTH_SHORT
                    ).show()
                    LogModule.logGpsStateChange(
                        activity, true,
                        "GPS включен пользователем"
                    )
                }
            }
        }

        // Запускаем обновления местоположения
        enhancedLocationManager.startLocationUpdates { coordinates ->
            Log.d(TAG, "📍 Новое местоположение: $coordinates")
        }

        // Проверяем начальное состояние GPS
        val initialState = enhancedLocationManager.forceLocationStatusCheck()
        locationEnabledState.value = initialState
        LogModule.logGpsStateChange(activity, initialState, "Проверка при инициализации")

        Log.d(TAG, "✅ GPS мониторинг настроен, начальное состояние: $initialState")
    }

    /**
     * Проверяет начальные разрешения и вызывает callback с результатом.
     */
    fun checkInitialPermissions(onResult: (Boolean) -> Unit) {
        val hasAllPermissions = permissionHelper.hasAllPermissions()
        onResult(hasAllPermissions)

        if (!hasAllPermissions) {
            Log.w(TAG, "⚠️ Не все разрешения предоставлены, потребуется запрос")
        }
    }

    /**
     * Инициализирует основные функции приложения после получения разрешений.
     */
    fun initializeAppFeatures(coordinatesState: MutableState<String>) {
        try {
            Log.d(TAG, "🚀 Инициализация функций приложения...")

            // Запускаем получение координат - startLocationUpdates принимает callback со строкой
            enhancedLocationManager.startLocationUpdates { coordinates ->
                coordinatesState.value = coordinates
                Log.d(TAG, "📍 Координаты обновлены: $coordinates")
            }

            // Принудительное обновление местоположения
            enhancedLocationManager.forceLocationUpdate(LocationMode.BALANCED)

            Log.i(TAG, "✅ Функции приложения инициализированы")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации функций: ${e.message}", e)
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    /**
     * Возвращает текущее состояние GPS.
     * Безопасный метод для получения состояния местоположения.
     */
    fun isLocationEnabled(): Boolean {
        return if (::enhancedLocationManager.isInitialized) {
            enhancedLocationManager.isLocationEnabled.value
        } else {
            false
        }
    }

    /**
     * Возвращает статистику аутентификации для отладки.
     */
    fun getAuthenticationStatistics(): AuthenticationManager.AuthenticationStatistics? {
        return if (::authenticationManager.isInitialized) {
            authenticationManager.getAuthenticationStatistics()
        } else {
            null
        }
    }

    // === СТАТУС И ДИАГНОСТИКА ===

    /**
     * Проверяет готовность системы к полноценной работе.
     */
    fun isSystemReady(): Boolean {
        return isFullyInitialized &&
                ::permissionHelper.isInitialized &&
                ::enhancedLocationManager.isInitialized &&
                ::bluetoothHelper.isInitialized &&
                ::authenticationManager.isInitialized &&
                ::temperatureMonitor.isInitialized &&
                ::dataManager.isInitialized
    }

    /**
     * Возвращает статус инициализации всех компонентов.
     */
    fun getInitializationStatus(): InitializationStatus {
        return InitializationStatus(
            isFullyInitialized = isFullyInitialized,
            initializedComponents = initializedComponentsCount,
            totalComponents = totalComponentsCount,
            initializationDurationMs = if (initStartTime > 0) System.currentTimeMillis() - initStartTime else 0,
            hasPermissionHelper = ::permissionHelper.isInitialized,
            hasLocationManager = ::enhancedLocationManager.isInitialized,
            hasBluetoothHelper = ::bluetoothHelper.isInitialized,
            hasAuthenticationManager = ::authenticationManager.isInitialized,
            hasTemperatureMonitor = ::temperatureMonitor.isInitialized,
            hasDataManager = ::dataManager.isInitialized
        )
    }

    /**
     * Возвращает краткий отчёт о состоянии инициализации.
     */
    fun getStatusReport(): String {
        val status = getInitializationStatus()
        return "AppInitializer: ${if (status.isFullyInitialized) "✅" else "⏳"} | " +
                "Компоненты: ${status.initializedComponents}/${status.totalComponents} | " +
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
        try {
            LogModule.logEventWithLocation(
                activity, bluetoothHelper, enhancedLocationManager,
                "Инициализация приложения завершена успешно за ${duration}мс"
            )
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Не удалось записать лог успешной инициализации: ${e.message}")
        }
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
    private fun performPartialCleanup() {
        try {
            Log.d(TAG, "🧹 Частичная очистка после ошибки инициализации...")

            // Очищаем только успешно инициализированные компоненты
            if (::dataManager.isInitialized) {
                // DataManager не требует специальной очистки
            }

            if (::temperatureMonitor.isInitialized) {
                // TemperatureMonitor не требует специальной очистки
            }

            if (::authenticationManager.isInitialized) {
                authenticationManager.resetAuthentication()
            }

            if (::bluetoothHelper.isInitialized) {
                bluetoothHelper.cleanup()
            }

            if (::enhancedLocationManager.isInitialized) {
                enhancedLocationManager.cleanup()
            }

            Log.d(TAG, "✅ Частичная очистка завершена")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка частичной очистки: ${e.message}")
        }
    }

    // === ОЧИСТКА РЕСУРСОВ ===

    /**
     * Очищает все ресурсы AppInitializer при закрытии приложения.
     */
    fun cleanup() {
        try {
            Log.d(TAG, "🧹 Начинаем полную очистку AppInitializer...")

            // Очищаем компоненты в обратном порядке инициализации
            if (::dataManager.isInitialized) {
                // DataManager не требует специальной очистки
                Log.d(TAG, "✅ DataManager очищен")
            }

            if (::temperatureMonitor.isInitialized) {
                // TemperatureMonitor не требует специальной очистки
                Log.d(TAG, "✅ TemperatureMonitor очищен")
            }

            if (::authenticationManager.isInitialized) {
                authenticationManager.resetAuthentication()
                Log.d(TAG, "✅ AuthenticationManager очищен")
            }

            if (::bluetoothHelper.isInitialized) {
                bluetoothHelper.cleanup()
                Log.d(TAG, "✅ BluetoothHelper очищен")
            }

            if (::enhancedLocationManager.isInitialized) {
                enhancedLocationManager.cleanup()
                Log.d(TAG, "✅ EnhancedLocationManager очищен")
            }

            // PermissionHelper не требует специальной очистки

            // Сбрасываем флаги состояния
            isFullyInitialized = false
            initializedComponentsCount = 0

            Log.d(TAG, "🧹 AppInitializer полностью очищен")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки AppInitializer: ${e.message}")
        }
    }

    // === DATA CLASSES ===

    /**
     * Статус инициализации всех компонентов приложения.
     */
    data class InitializationStatus(
        val isFullyInitialized: Boolean,
        val initializedComponents: Int,
        val totalComponents: Int,
        val initializationDurationMs: Long,
        val hasPermissionHelper: Boolean,
        val hasLocationManager: Boolean,
        val hasBluetoothHelper: Boolean,
        val hasAuthenticationManager: Boolean,  // НОВОЕ ПОЛЕ
        val hasTemperatureMonitor: Boolean,
        val hasDataManager: Boolean
    ) {
        /**
         * Возвращает подробный отчёт о статусе инициализации с информацией об аутентификации.
         */
        fun getDetailedReport(): String {
            return buildString {
                appendLine("=== INITIALIZATION STATUS REPORT ===")
                appendLine("Статус: ${if (isFullyInitialized) "✅ Завершена" else "⏳ В процессе"}")
                appendLine("Прогресс: $initializedComponents/$totalComponents компонентов")
                appendLine("Время инициализации: ${initializationDurationMs}ms")
                appendLine()
                appendLine("Компоненты:")
                appendLine("  PermissionHelper: ${if (hasPermissionHelper) "✅" else "❌"}")
                appendLine("  LocationManager: ${if (hasLocationManager) "✅" else "❌"}")
                appendLine("  BluetoothHelper: ${if (hasBluetoothHelper) "✅" else "❌"}")
                appendLine("  AuthenticationManager: ${if (hasAuthenticationManager) "✅" else "❌"}")
                appendLine("  TemperatureMonitor: ${if (hasTemperatureMonitor) "✅" else "❌"}")
                appendLine("  DataManager: ${if (hasDataManager) "✅" else "❌"}")
                appendLine("=====================================")
            }
        }

        /**
         * Возвращает процент завершённости инициализации.
         */
        fun getCompletionPercentage(): Int {
            return if (totalComponents > 0) {
                (initializedComponents * 100) / totalComponents
            } else {
                0
            }
        }

        /**
         * Проверяет, есть ли критические проблемы с инициализацией.
         */
        fun hasCriticalIssues(): Boolean {
            return !hasPermissionHelper || !hasLocationManager || !hasBluetoothHelper
        }
    }
}