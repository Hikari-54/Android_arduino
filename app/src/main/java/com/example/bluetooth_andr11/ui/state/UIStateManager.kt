package com.example.bluetooth_andr11.ui.state

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.bluetooth_andr11.data.DataManager

/**
 * Централизованный менеджер всех reactive UI состояний приложения.
 *
 * Основные функции:
 * - Создание и управление всеми mutableStateOf объектами
 * - Предоставление типобезопасного доступа к состояниям
 * - Валидация изменений состояний перед обновлением
 * - Автоматическое логирование критических изменений состояния
 * - Создание контейнеров состояний для передачи между компонентами
 * - Сброс состояний в начальные значения при необходимости
 *
 * Архитектурные преимущества:
 * - Единая точка управления всеми UI состояниями
 * - Предотвращение дублирования состояний в разных компонентах
 * - Централизованная валидация и логирование изменений
 * - Типобезопасность через dedicated методы доступа
 * - Легкое тестирование UI логики через моки состояний
 * - Consistent naming и структурирование состояний
 *
 * Поддерживаемые категории состояний:
 * - System States: Bluetooth, GPS, разрешения, инициализация
 * - Device States: батарея, подключение, режимы работы
 * - Sensor States: температуры, акселерометр, датчик Холла
 * - Location States: координаты, точность, источник данных
 * - UI States: отладочная панель, навигация, уведомления
 */
class UIStateManager {

    companion object {
        private const val TAG = "UIStateManager"

        // === КОНСТАНТЫ ВАЛИДАЦИИ ===

        /** Минимальный допустимый уровень батареи */
        private const val MIN_BATTERY_LEVEL = 0

        /** Максимальный допустимый уровень батареи */
        private const val MAX_BATTERY_LEVEL = 100

        /** Значения состояний по умолчанию */
        private const val DEFAULT_BATTERY = 0
        private const val DEFAULT_COORDINATES = "Неизвестно"
        private const val DEFAULT_SENSOR_VALUE = "--"

        /** Список валидных состояний датчика Холла */
        private val VALID_HALL_STATES = setOf("Открыт", "Закрыт", "Неизвестно", "--")

        /** Паттерн для валидации координат */
        private val COORDINATE_PATTERN = Regex("""^-?\d+\.\d+,\s*-?\d+\.\d+$""")
    }

    // === SYSTEM STATES (Системные состояния) ===

    /** Включен ли Bluetooth адаптер на устройстве */
    private val _isBluetoothEnabled = mutableStateOf(false)
    val isBluetoothEnabled: MutableState<Boolean> get() = _isBluetoothEnabled

    /** Подключено ли Arduino устройство через Bluetooth */
    private val _isDeviceConnected = mutableStateOf(false)
    val isDeviceConnected: MutableState<Boolean> get() = _isDeviceConnected

    /** Предоставлены ли все необходимые разрешения Android */
    private val _allPermissionsGranted = mutableStateOf(false)
    val allPermissionsGranted: MutableState<Boolean> get() = _allPermissionsGranted

    /** Включены ли службы местоположения в системе */
    private val _isLocationServiceEnabled = mutableStateOf(false)
    val isLocationServiceEnabled: MutableState<Boolean> get() = _isLocationServiceEnabled

    // === DEVICE STATES (Состояния устройства Arduino) ===

    /** Уровень заряда батареи Arduino (0-100%) */
    private val _batteryPercent = mutableStateOf(DEFAULT_BATTERY)
    val batteryPercent: MutableState<Int> get() = _batteryPercent

    /** Состояние функций Arduino (количество активных функций) */
    private val _functionState = mutableStateOf(DEFAULT_SENSOR_VALUE)
    val functionState: MutableState<String> get() = _functionState

    // === SENSOR STATES (Состояния датчиков) ===

    /** Температура горячего отсека или статус ошибки датчика */
    private val _temp1 = mutableStateOf(DEFAULT_SENSOR_VALUE)
    val temp1: MutableState<String> get() = _temp1

    /** Температура холодного отсека или статус ошибки датчика */
    private val _temp2 = mutableStateOf(DEFAULT_SENSOR_VALUE)
    val temp2: MutableState<String> get() = _temp2

    /** Состояние датчика Холла (Открыт/Закрыт/Неизвестно) */
    private val _hallState = mutableStateOf(DEFAULT_SENSOR_VALUE)
    val hallState: MutableState<String> get() = _hallState

    /** Данные акселерометра с категоризацией уровня тряски */
    private val _accelerometerData = mutableStateOf(DEFAULT_SENSOR_VALUE)
    val accelerometerData: MutableState<String> get() = _accelerometerData

    // === LOCATION STATES (Состояния местоположения) ===

    /** Текущие координаты в формате "latitude, longitude" */
    private val _coordinates = mutableStateOf(DEFAULT_COORDINATES)
    val coordinates: MutableState<String> get() = _coordinates

    // === UI STATES (Состояния пользовательского интерфейса) ===

    /** Показывать ли панель отладки (только в DEBUG режиме) */
    private val _showDebugPanel = mutableStateOf(false)
    val showDebugPanel: MutableState<Boolean> get() = _showDebugPanel

    // === CONTROL STATES (Состояния управления) ===

    /** Включен ли режим нагрева */
    private val _isHeatOn = mutableStateOf(false)
    val isHeatOn: MutableState<Boolean> get() = _isHeatOn

    /** Включен ли режим охлаждения */
    private val _isCoolOn = mutableStateOf(false)
    val isCoolOn: MutableState<Boolean> get() = _isCoolOn

    /** Включен ли режим подсветки */
    private val _isLightOn = mutableStateOf(false)
    val isLightOn: MutableState<Boolean> get() = _isLightOn

    // === СТАТИСТИКА И МОНИТОРИНГ ===

    /** Счётчик обновлений состояний для диагностики */
    @Volatile
    private var stateUpdateCount = 0

    /** Время последнего обновления состояний */
    @Volatile
    private var lastUpdateTime = 0L

    /** Счётчик ошибок валидации */
    @Volatile
    private var validationErrorCount = 0

    // === МЕТОДЫ ТИПОБЕЗОПАСНОГО ОБНОВЛЕНИЯ ===

    /**
     * Безопасно обновляет уровень заряда батареи с валидацией диапазона.
     *
     * @param level новый уровень заряда (0-100)
     * @return true если значение обновлено успешно
     */
    fun updateBatteryLevel(level: Int): Boolean {
        return if (level in MIN_BATTERY_LEVEL..MAX_BATTERY_LEVEL) {
            val oldLevel = _batteryPercent.value
            _batteryPercent.value = level

            // Логируем значимые изменения уровня батареи
            if (kotlin.math.abs(level - oldLevel) >= 5) {
                Log.d(TAG, "🔋 Батарея изменилась: ${oldLevel}% → ${level}%")
            }

            updateStatistics()
            true
        } else {
            Log.w(
                TAG,
                "⚠️ Некорректный уровень батареи: $level (допустимо: $MIN_BATTERY_LEVEL-$MAX_BATTERY_LEVEL)"
            )
            validationErrorCount++
            false
        }
    }

    /**
     * Безопасно обновляет температуру горячего отсека с валидацией формата.
     *
     * @param temperature новое значение температуры или статус ошибки
     * @return true если значение обновлено успешно
     */
    fun updateHotTemperature(temperature: String): Boolean {
        return if (isValidTemperatureValue(temperature)) {
            val oldTemp = _temp1.value
            _temp1.value = temperature

            // Логируем изменения температуры (исключая переходы из "--")
            if (oldTemp != DEFAULT_SENSOR_VALUE && oldTemp != temperature) {
                Log.d(TAG, "🔥 Температура горячего отсека: $oldTemp → $temperature")
            }

            updateStatistics()
            true
        } else {
            Log.w(TAG, "⚠️ Некорректное значение температуры горячего отсека: '$temperature'")
            validationErrorCount++
            false
        }
    }

    /**
     * Безопасно обновляет температуру холодного отсека с валидацией формата.
     *
     * @param temperature новое значение температуры или статус ошибки
     * @return true если значение обновлено успешно
     */
    fun updateColdTemperature(temperature: String): Boolean {
        return if (isValidTemperatureValue(temperature)) {
            val oldTemp = _temp2.value
            _temp2.value = temperature

            // Логируем изменения температуры (исключая переходы из "--")
            if (oldTemp != DEFAULT_SENSOR_VALUE && oldTemp != temperature) {
                Log.d(TAG, "❄️ Температура холодного отсека: $oldTemp → $temperature")
            }

            updateStatistics()
            true
        } else {
            Log.w(TAG, "⚠️ Некорректное значение температуры холодного отсека: '$temperature'")
            validationErrorCount++
            false
        }
    }

    /**
     * Безопасно обновляет состояние датчика Холла с валидацией допустимых значений.
     *
     * @param state новое состояние датчика
     * @return true если значение обновлено успешно
     */
    fun updateHallState(state: String): Boolean {
        return if (state in VALID_HALL_STATES) {
            val oldState = _hallState.value
            _hallState.value = state

            // Логируем изменения состояния сумки
            if (oldState != DEFAULT_SENSOR_VALUE && oldState != state) {
                Log.d(TAG, "📦 Состояние сумки: $oldState → $state")
            }

            updateStatistics()
            true
        } else {
            Log.w(
                TAG,
                "⚠️ Некорректное состояние датчика Холла: '$state' (допустимо: $VALID_HALL_STATES)"
            )
            validationErrorCount++
            false
        }
    }

    /**
     * Безопасно обновляет состояние функций Arduino.
     *
     * @param state описание активных функций
     * @return true если значение обновлено успешно
     */
    fun updateFunctionState(state: String): Boolean {
        val oldState = _functionState.value
        _functionState.value = state

        // Логируем изменения состояния функций
        if (oldState != DEFAULT_SENSOR_VALUE && oldState != state) {
            Log.d(TAG, "⚙️ Функции Arduino: $oldState → $state")
        }

        updateStatistics()
        return true
    }

    /**
     * Безопасно обновляет данные акселерометра.
     *
     * @param data форматированные данные акселерометра с категорией
     * @return true если значение обновлено успешно
     */
    fun updateAccelerometerData(data: String): Boolean {
        val oldData = _accelerometerData.value
        _accelerometerData.value = data

        // Логируем только значимые изменения акселерометра
        if (oldData != DEFAULT_SENSOR_VALUE &&
            !oldData.startsWith("В покое") &&
            data.startsWith("Экстремальная")
        ) {
            Log.d(TAG, "📳 Акселерометр: $oldData → $data")
        }

        updateStatistics()
        return true
    }

    /**
     * Безопасно обновляет координаты местоположения с валидацией формата.
     *
     * @param newCoordinates координаты в формате "lat, lon"
     * @return true если значение обновлено успешно
     */
    fun updateCoordinates(newCoordinates: String): Boolean {
        return if (isValidCoordinates(newCoordinates)) {
            val oldCoordinates = _coordinates.value
            _coordinates.value = newCoordinates

            // Логируем обновления координат (исключая начальное состояние)
            if (oldCoordinates != DEFAULT_COORDINATES) {
                Log.d(TAG, "📍 Координаты обновлены: $newCoordinates")
            }

            updateStatistics()
            true
        } else {
            Log.w(TAG, "⚠️ Некорректный формат координат: '$newCoordinates'")
            validationErrorCount++
            false
        }
    }

    // === СИСТЕМНЫЕ СОСТОЯНИЯ (без валидации) ===

    /**
     * Обновляет состояние Bluetooth адаптера.
     */
    fun updateBluetoothEnabled(enabled: Boolean) {
        val wasEnabled = _isBluetoothEnabled.value
        _isBluetoothEnabled.value = enabled

        if (wasEnabled != enabled) {
            Log.d(TAG, "🔵 Bluetooth: ${if (enabled) "включен" else "выключен"}")
            updateStatistics()
        }
    }

    /**
     * Обновляет состояние подключения устройства.
     */
    fun updateDeviceConnected(connected: Boolean) {
        val wasConnected = _isDeviceConnected.value
        _isDeviceConnected.value = connected

        if (wasConnected != connected) {
            Log.d(TAG, "🔗 Устройство: ${if (connected) "подключено" else "отключено"}")
            updateStatistics()
        }
    }

    /**
     * Обновляет состояние разрешений приложения.
     */
    fun updateAllPermissionsGranted(granted: Boolean) {
        val wereGranted = _allPermissionsGranted.value
        _allPermissionsGranted.value = granted

        if (wereGranted != granted) {
            Log.d(TAG, "🔐 Разрешения: ${if (granted) "предоставлены" else "отсутствуют"}")
            updateStatistics()
        }
    }

    /**
     * Обновляет состояние служб местоположения.
     */
    fun updateLocationServiceEnabled(enabled: Boolean) {
        val wasEnabled = _isLocationServiceEnabled.value
        _isLocationServiceEnabled.value = enabled

        if (wasEnabled != enabled) {
            Log.d(TAG, "🛰️ GPS: ${if (enabled) "включен" else "выключен"}")
            updateStatistics()
        }
    }

    /**
     * Переключает отображение панели отладки.
     */
    fun toggleDebugPanel() {
        _showDebugPanel.value = !_showDebugPanel.value
        Log.d(TAG, "🔧 Панель отладки: ${if (_showDebugPanel.value) "показана" else "скрыта"}")
        updateStatistics()
    }

    /**
     * Обновляет состояние режима нагрева.
     */
    fun updateHeatState(enabled: Boolean) {
        val wasEnabled = _isHeatOn.value
        _isHeatOn.value = enabled

        if (wasEnabled != enabled) {
            Log.d(TAG, "🔥 Нагрев: ${if (enabled) "включен" else "выключен"}")
            updateStatistics()
        }
    }

    /**
     * Обновляет состояние режима охлаждения.
     */
    fun updateCoolState(enabled: Boolean) {
        val wasEnabled = _isCoolOn.value
        _isCoolOn.value = enabled

        if (wasEnabled != enabled) {
            Log.d(TAG, "❄️ Охлаждение: ${if (enabled) "включено" else "выключено"}")
            updateStatistics()
        }
    }

    /**
     * Обновляет состояние режима подсветки.
     */
    fun updateLightState(enabled: Boolean) {
        val wasEnabled = _isLightOn.value
        _isLightOn.value = enabled

        if (wasEnabled != enabled) {
            Log.d(TAG, "💡 Подсветка: ${if (enabled) "включена" else "выключена"}")
            updateStatistics()
        }
    }

    // === СОЗДАНИЕ КОНТЕЙНЕРОВ СОСТОЯНИЙ ===

    /**
     * Создаёт контейнер UI состояний для передачи в DataManager.
     * Обеспечивает типобезопасную передачу reactive состояний между компонентами.
     *
     * @return объект UIStates со всеми необходимыми состояниями
     */
    fun createDataManagerUIStates(): DataManager.UIStates {
        return DataManager.UIStates(
            batteryPercent = _batteryPercent,
            temp1 = _temp1,
            temp2 = _temp2,
            hallState = _hallState,
            functionState = _functionState,
            accelerometerData = _accelerometerData,
            isHeatOn = _isHeatOn,
            isCoolOn = _isCoolOn,
            isLightOn = _isLightOn
        )
    }

    // === ВАЛИДАЦИЯ ДАННЫХ ===

    /**
     * Валидирует значение температуры (число, "Ошибка" или "--").
     */
    private fun isValidTemperatureValue(value: String): Boolean {
        return when {
            value == "Ошибка" -> true
            value == DEFAULT_SENSOR_VALUE -> true
            else -> {
                try {
                    val temp = value.toFloat()
                    temp in -50f..100f // Реалистичный диапазон температур
                } catch (e: NumberFormatException) {
                    false
                }
            }
        }
    }

    /**
     * Валидирует формат координат (должен соответствовать паттерну "lat, lon").
     */
    private fun isValidCoordinates(coordinates: String): Boolean {
        return coordinates == DEFAULT_COORDINATES ||
                coordinates.matches(COORDINATE_PATTERN)
    }

    // === УТИЛИТАРНЫЕ МЕТОДЫ ===

    /**
     * Обновляет статистику изменений состояний.
     */
    private fun updateStatistics() {
        stateUpdateCount++
        lastUpdateTime = System.currentTimeMillis()
    }

    /**
     * Сбрасывает все состояния в значения по умолчанию.
     * Используется при перезапуске приложения или отключении устройства.
     */
    fun resetAllStates() {
        Log.d(TAG, "🔄 Сброс всех UI состояний в значения по умолчанию")

        // Сбрасываем системные состояния
        _isBluetoothEnabled.value = false
        _isDeviceConnected.value = false
        _allPermissionsGranted.value = false
        _isLocationServiceEnabled.value = false

        // Сбрасываем состояния устройства
        _batteryPercent.value = DEFAULT_BATTERY
        _functionState.value = DEFAULT_SENSOR_VALUE

        // Сбрасываем состояния датчиков
        _temp1.value = DEFAULT_SENSOR_VALUE
        _temp2.value = DEFAULT_SENSOR_VALUE
        _hallState.value = DEFAULT_SENSOR_VALUE
        _accelerometerData.value = DEFAULT_SENSOR_VALUE

        // Сбрасываем состояния управления
        _isHeatOn.value = false
        _isCoolOn.value = false
        _isLightOn.value = false

        // Сбрасываем местоположение
        _coordinates.value = DEFAULT_COORDINATES

        // UI состояния не сбрасываем (отладочная панель может оставаться открытой)

        // Сбрасываем статистику
        stateUpdateCount = 0
        validationErrorCount = 0
        lastUpdateTime = System.currentTimeMillis()
    }

    /**
     * Сбрасывает только состояния датчиков (при потере подключения к Arduino).
     */
    fun resetSensorStates() {
        Log.d(TAG, "🔄 Сброс состояний датчиков")

        _batteryPercent.value = DEFAULT_BATTERY
        _temp1.value = DEFAULT_SENSOR_VALUE
        _temp2.value = DEFAULT_SENSOR_VALUE
        _hallState.value = DEFAULT_SENSOR_VALUE
        _functionState.value = DEFAULT_SENSOR_VALUE
        _accelerometerData.value = DEFAULT_SENSOR_VALUE

        updateStatistics()
    }

    // === ДИАГНОСТИКА И МОНИТОРИНГ ===

    /**
     * Возвращает статистику работы UIStateManager для диагностики.
     *
     * @return объект UIStateStatistics с метриками производительности
     */
    fun getStatistics(): UIStateStatistics {
        return UIStateStatistics(
            totalUpdates = stateUpdateCount,
            validationErrors = validationErrorCount,
            lastUpdateTime = lastUpdateTime,
            isSystemReady = isSystemReady(),
            hasActiveData = hasActiveData(),
            successRate = if (stateUpdateCount > 0) {
                ((stateUpdateCount - validationErrorCount) * 100 / stateUpdateCount)
            } else 100
        )
    }

    /**
     * Проверяет готовность всех системных состояний.
     */
    fun isSystemReady(): Boolean {
        return _isBluetoothEnabled.value &&
                _isDeviceConnected.value &&
                _allPermissionsGranted.value &&
                _isLocationServiceEnabled.value
    }

    /**
     * Проверяет наличие активных данных от датчиков.
     */
    fun hasActiveData(): Boolean {
        return _batteryPercent.value > 0 ||
                _temp1.value != DEFAULT_SENSOR_VALUE ||
                _temp2.value != DEFAULT_SENSOR_VALUE ||
                _hallState.value != DEFAULT_SENSOR_VALUE ||
                _coordinates.value != DEFAULT_COORDINATES
    }

    /**
     * Возвращает краткий отчёт о состоянии всех UI компонентов.
     */
    fun getStatusReport(): String {
        val stats = getStatistics()
        return "UIStateManager: ${stats.successRate}% успешных | " +
                "Обновлений: ${stats.totalUpdates} | " +
                "Ошибок: ${stats.validationErrors} | " +
                "Система: ${if (stats.isSystemReady) "✅" else "❌"} | " +
                "Данные: ${if (stats.hasActiveData) "✅" else "❌"}"
    }

    /**
     * Возвращает детальную информацию о всех состояниях для отладки.
     */
    fun getDetailedStateInfo(): String {
        return buildString {
            appendLine("🎛️ ДЕТАЛЬНАЯ ИНФОРМАЦИЯ UI СОСТОЯНИЙ:")
            appendLine("═══════════════════════════════════════")
            appendLine("📊 Статистика:")
            appendLine("  • Обновлений: $stateUpdateCount")
            appendLine("  • Ошибок валидации: $validationErrorCount")
            appendLine(
                "  • Последнее обновление: ${
                    if (lastUpdateTime > 0) java.text.SimpleDateFormat(
                        "HH:mm:ss"
                    ).format(java.util.Date(lastUpdateTime)) else "Нет"
                }"
            )
            appendLine()
            appendLine("🔧 Системные состояния:")
            appendLine("  • Bluetooth: ${if (_isBluetoothEnabled.value) "✅" else "❌"}")
            appendLine("  • Устройство: ${if (_isDeviceConnected.value) "✅" else "❌"}")
            appendLine("  • Разрешения: ${if (_allPermissionsGranted.value) "✅" else "❌"}")
            appendLine("  • GPS: ${if (_isLocationServiceEnabled.value) "✅" else "❌"}")
            appendLine()
            appendLine("📱 Данные устройства:")
            appendLine("  • Батарея: ${_batteryPercent.value}%")
            appendLine("  • Функции: ${_functionState.value}")
            appendLine()
            appendLine("🌡️ Датчики:")
            appendLine("  • Температура 1: ${_temp1.value}")
            appendLine("  • Температура 2: ${_temp2.value}")
            appendLine("  • Датчик Холла: ${_hallState.value}")
            appendLine("  • Акселерометр: ${_accelerometerData.value}")
            appendLine()
            appendLine("📍 Местоположение:")
            appendLine("  • Координаты: ${_coordinates.value}")
            appendLine()
            appendLine("🎮 UI состояния:")
            appendLine("  • Панель отладки: ${if (_showDebugPanel.value) "✅" else "❌"}")
            appendLine()
            appendLine("🎛️ Управление:")
            appendLine("  • Нагрев: ${if (_isHeatOn.value) "✅" else "❌"}")
            appendLine("  • Охлаждение: ${if (_isCoolOn.value) "✅" else "❌"}")
            appendLine("  • Подсветка: ${if (_isLightOn.value) "✅" else "❌"}")
            appendLine("═══════════════════════════════════════")
        }
    }

    // === DATA CLASSES ===

    /**
     * Статистика работы UIStateManager для мониторинга и диагностики.
     *
     * @param totalUpdates общее количество обновлений состояний
     * @param validationErrors количество ошибок валидации
     * @param lastUpdateTime время последнего обновления состояния
     * @param isSystemReady готовы ли все системные состояния
     * @param hasActiveData есть ли активные данные от датчиков
     * @param successRate процент успешных обновлений состояний
     */
    data class UIStateStatistics(
        val totalUpdates: Int,
        val validationErrors: Int,
        val lastUpdateTime: Long,
        val isSystemReady: Boolean,
        val hasActiveData: Boolean,
        val successRate: Int
    ) {
        /**
         * Проверяет наличие проблем с обновлением состояний.
         */
        fun hasIssues(): Boolean {
            return successRate < 95 || validationErrors > 10
        }

        /**
         * Возвращает возраст последнего обновления в секундах.
         */
        fun getLastUpdateAgeSeconds(): Long {
            return if (lastUpdateTime > 0) {
                (System.currentTimeMillis() - lastUpdateTime) / 1000
            } else -1
        }

        /**
         * Возвращает краткую сводку статистики.
         */
        fun getSummary(): String {
            return "Обновлений: $totalUpdates | Успешно: $successRate% | " +
                    "Система: ${if (isSystemReady) "готова" else "не готова"} | " +
                    "Данные: ${if (hasActiveData) "есть" else "нет"}"
        }
    }
}