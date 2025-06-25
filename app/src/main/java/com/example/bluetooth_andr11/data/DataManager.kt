package com.example.bluetooth_andr11.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.monitoring.TemperatureMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Централизованный менеджер для обработки и валидации данных от Arduino устройства.
 *
 * Основные функции:
 * - Парсинг входящих данных Arduino в формате CSV
 * - Валидация и фильтрация некорректных данных
 * - Обновление reactive UI состояний
 * - Интеллектуальное логирование событий с предотвращением спама
 * - Передача данных в специализированные мониторы (температура, акселерометр)
 * - Кэширование последних значений для анализа изменений
 *
 * Поддерживаемый формат данных Arduino:
 * "batteryPercent,tempHot,tempCold,closedState,state,overload"
 *
 * Примеры входных данных:
 * - "85,25.50,15.20,1,2,0.15" - нормальные данные
 * - "90,er,12.30,0,1,2.50" - ошибка датчика горячего отсека
 * - "75,45.00,er,1,0,0.05" - ошибка датчика холодного отсека
 *
 * Архитектурные принципы:
 * - Thread-safe операции с reactive состояниями
 * - Graceful handling ошибок без прерывания работы
 * - Минимальное влияние на производительность UI потока
 * - Автоматическая фильтрация повторяющихся событий
 * - Интеграция с существующими системами логирования и мониторинга
 */
class DataManager(
    private val context: Context,
    private val bluetoothHelper: BluetoothHelper,
    private val locationManager: EnhancedLocationManager,
    private val temperatureMonitor: TemperatureMonitor
) {
    companion object {
        private const val TAG = "DataManager"

        // === КОНСТАНТЫ ВАЛИДАЦИИ ===

        /** Ожидаемое количество параметров в данных Arduino */
        private const val EXPECTED_PARAMETERS_COUNT = 6

        /** Минимальное корректное значение батареи */
        private const val MIN_BATTERY_LEVEL = 0

        /** Максимальное корректное значение батареи */
        private const val MAX_BATTERY_LEVEL = 100

        /** Минимальное корректное значение температуры */
        private const val MIN_TEMPERATURE = -50f

        /** Максимальное корректное значение температуры */
        private const val MAX_TEMPERATURE = 100f

        /** Максимальное корректное значение перегрузки акселерометра */
        private const val MAX_ACCELEROMETER_VALUE = 20f

        // === КОНСТАНТЫ ЛОГИРОВАНИЯ ===

        /** Интервал между логами акселерометра для предотвращения спама */
        private const val ACCELEROMETER_LOG_INTERVAL_MS = 2000L

        /** Разница в заряде батареи для логирования изменений */
        private const val BATTERY_LOG_THRESHOLD = 5
    }

    // === КЭШИРОВАНИЕ ДАННЫХ ===

    /** Последний зафиксированный уровень батареи для предотвращения спама */
    @Volatile
    private var lastLoggedBatteryLevel = 101

    /** Последнее зафиксированное состояние сумки */
    @Volatile
    private var lastLoggedBagState: String? = null

    /** Время последнего лога акселерометра */
    @Volatile
    private var lastAccelerometerLogTime = 0L

    /** Счётчик некорректных данных для диагностики */
    @Volatile
    private var invalidDataCount = 0

    /** Общий счётчик обработанных пакетов данных */
    @Volatile
    private var totalPacketsProcessed = 0

    // === ОСНОВНЫЕ МЕТОДЫ ОБРАБОТКИ ДАННЫХ ===

    /**
     * Главная точка входа для обработки данных от Arduino или симулятора.
     *
     * Выполняет полный цикл обработки:
     * 1. Логирование raw данных для отладки
     * 2. Парсинг и валидация формата
     * 3. Обновление UI состояний через reactive variables
     * 4. Передача данных в специализированные мониторы
     * 5. Логирование значимых событий
     *
     * @param rawData строка данных в формате "battery,temp1,temp2,closed,state,overload"
     * @param uiStates объект содержащий все reactive состояния UI
     */
    fun processArduinoData(rawData: String, uiStates: UIStates) {
        totalPacketsProcessed++

        Log.d(TAG, "🔴 Обработка пакета #$totalPacketsProcessed: '$rawData'")

        try {
            val cleanData = rawData.trim()
            val parts = cleanData.split(",")

            if (parts.size >= EXPECTED_PARAMETERS_COUNT) {
                Log.d(TAG, "✅ Валидация пройдена: ${parts.size} параметров")

                val parsedData = parseDataParts(parts)
                if (parsedData != null) {
                    updateAllUIStates(parsedData, uiStates)
                    forwardToMonitors(parsedData)
                    logSignificantEvents(parsedData)
                } else {
                    handleInvalidData(rawData, "Ошибка парсинга данных")
                }

                // Логируем дополнительные параметры если есть
                if (parts.size > EXPECTED_PARAMETERS_COUNT) {
                    val extraParams =
                        parts.subList(EXPECTED_PARAMETERS_COUNT, parts.size).joinToString(",")
                    Log.w(TAG, "⚠️ Дополнительные параметры проигнорированы: $extraParams")
                }

            } else {
                handleInvalidData(
                    rawData,
                    "Недостаточно параметров: получено ${parts.size}, ожидается $EXPECTED_PARAMETERS_COUNT"
                )
            }
        } catch (e: Exception) {
            handleInvalidData(rawData, "Критическая ошибка обработки: ${e.message}")
        }
    }

    // === ПАРСИНГ И ВАЛИДАЦИЯ ===

    /**
     * Парсит массив строковых частей данных в структурированный объект.
     *
     * Выполняет валидацию каждого параметра:
     * - battery: 0-100 или null если некорректно
     * - tempHot/tempCold: float значение или "er" для ошибки датчика
     * - closedState: 0 или 1 (булево значение)
     * - functionState: неотрицательное целое число
     * - accelerometer: положительное float значение
     *
     * @param parts массив строк с данными
     * @return ParsedArduinoData или null если данные некорректны
     */
    private fun parseDataParts(parts: List<String>): ParsedArduinoData? {
        return try {
            val battery =
                parts[0].trim().toIntOrNull()?.takeIf { it in MIN_BATTERY_LEVEL..MAX_BATTERY_LEVEL }
            val tempHotString = parts[1].trim()
            val tempColdString = parts[2].trim()
            val closedState = parts[3].trim().toIntOrNull()?.takeIf { it in 0..1 }
            val functionState = parts[4].trim().toIntOrNull()?.takeIf { it >= 0 }
            val accelerometer = parts[5].trim().toFloatOrNull()
                ?.takeIf { it >= 0f && it <= MAX_ACCELEROMETER_VALUE }

            // Валидация температур
            val tempHot = validateTemperature(tempHotString)
            val tempCold = validateTemperature(tempColdString)

            ParsedArduinoData(
                battery = battery,
                tempHot = tempHot,
                tempHotString = tempHotString,
                tempCold = tempCold,
                tempColdString = tempColdString,
                closedState = closedState,
                functionState = functionState,
                accelerometer = accelerometer
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка парсинга частей данных: ${e.message}")
            null
        }
    }

    /**
     * Валидирует температурное значение с поддержкой ошибок датчика.
     *
     * @param tempString строка температуры ("er" или число)
     * @return float значение температуры или null если "er" или некорректно
     */
    private fun validateTemperature(tempString: String): Float? {
        return when {
            tempString == "er" -> null // Ошибка датчика
            else -> tempString.toFloatOrNull()?.takeIf {
                it in MIN_TEMPERATURE..MAX_TEMPERATURE
            }
        }
    }

    /**
     * Обрабатывает некорректные данные с логированием и счётчиком ошибок.
     */
    private fun handleInvalidData(rawData: String, reason: String) {
        invalidDataCount++
        Log.w(TAG, "❌ Некорректные данные #$invalidDataCount: '$rawData' - $reason")

        // Логируем каждую 10-ю ошибку для предотвращения спама
        if (invalidDataCount % 10 == 0) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LogModule.logSystemEvent(
                        context, bluetoothHelper, locationManager,
                        "Накоплено $invalidDataCount некорректных пакетов данных. Последняя ошибка: $reason",
                        "ОБРАБОТКА_ДАННЫХ"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка логирования некорректных данных: ${e.message}")
                }
            }
        }
    }

    // === ОБНОВЛЕНИЕ UI СОСТОЯНИЙ ===

    /**
     * Обновляет все reactive состояния UI на основе распарсенных данных.
     *
     * @param data распарсенные данные Arduino
     * @param uiStates объект с reactive состояниями
     */
    private fun updateAllUIStates(data: ParsedArduinoData, uiStates: UIStates) {
        // Обновляем батарею
        data.battery?.let { batteryValue ->
            uiStates.batteryPercent.value = batteryValue
        }

        // Обновляем температуры с обработкой ошибок датчиков
        uiStates.temp1.value = when {
            data.tempHotString == "er" -> "Ошибка"
            data.tempHot != null -> data.tempHot.toString()
            else -> uiStates.temp1.value // Сохраняем предыдущее значение
        }

        uiStates.temp2.value = when {
            data.tempColdString == "er" -> "Ошибка"
            data.tempCold != null -> data.tempCold.toString()
            else -> uiStates.temp2.value // Сохраняем предыдущее значение
        }

        // Обновляем состояние сумки
        data.closedState?.let { closedValue ->
            val newState = when (closedValue) {
                1 -> {
                    Log.d(TAG, "🔒 Сумка закрыта")
                    "Закрыт"
                }

                0 -> {
                    Log.d(TAG, "🔓 Сумка открыта")
                    "Открыт"
                }

                else -> "Неизвестно"
            }
            uiStates.hallState.value = newState
        }

        // Обновляем состояние функций
        data.functionState?.let { functionValue ->
            uiStates.functionState.value = when (functionValue) {
                0 -> "Все выключено"
                1 -> "1 функция активна"
                else -> "$functionValue функций активно"
            }
        }

        // Обновляем акселерометр с категоризацией
        data.accelerometer?.let { accelerometerValue ->
            updateAccelerometerData(accelerometerValue, uiStates.accelerometerData)
        }
    }

    /**
     * Обновляет данные акселерометра с интеллектуальной категоризацией тряски.
     *
     * @param value значение перегрузки акселерометра
     * @param accelerometerState reactive состояние для обновления
     */
    private fun updateAccelerometerData(value: Float, accelerometerState: MutableState<String>) {
        val shakeCategory = when {
            value > 2.5 -> {
                logAccelerometerEvent("Экстремальная тряска", value)
                "Экстремальная тряска"
            }

            value > 1.0 -> {
                logAccelerometerEvent("Сильная тряска", value)
                "Сильная тряска"
            }

            value > 0.5 -> "Слабая тряска"
            else -> "В покое"
        }

        accelerometerState.value = "$shakeCategory (${String.format("%.2f", value)})"
    }

    /**
     * Логирует значимые события акселерометра с ограничением частоты.
     */
    private fun logAccelerometerEvent(category: String, value: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAccelerometerLogTime > ACCELEROMETER_LOG_INTERVAL_MS) {
            lastAccelerometerLogTime = currentTime

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LogModule.logSystemEvent(
                        context, bluetoothHelper, locationManager,
                        "$category (${String.format("%.2f", value)})",
                        "АКСЕЛЕРОМЕТР"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка логирования акселерометра: ${e.message}")
                }
            }
        }
    }

    // === ПЕРЕДАЧА ДАННЫХ В МОНИТОРЫ ===

    /**
     * Передаёт распарсенные данные в специализированные мониторы для анализа.
     *
     * @param data распарсенные данные Arduino
     */
    private fun forwardToMonitors(data: ParsedArduinoData) {
        // Передаём температурные данные в TemperatureMonitor
        // null значения обозначают ошибки датчиков
        temperatureMonitor.processTemperatures(data.tempHot, data.tempCold)
    }

    // === ЛОГИРОВАНИЕ СОБЫТИЙ ===

    /**
     * Логирует значимые изменения состояния устройства с предотвращением спама.
     *
     * @param data распарсенные данные для анализа изменений
     */
    private fun logSignificantEvents(data: ParsedArduinoData) {
        // Логируем пороговые изменения батареи
        data.battery?.let { batteryValue ->
            logBatteryThresholds(batteryValue)
        }

        // Логируем изменения состояния сумки
        data.closedState?.let { closedValue ->
            val newState = if (closedValue == 1) "Закрыт" else "Открыт"
            logBagStateChange(newState)
        }
    }

    /**
     * Логирует пороговые значения батареи для предотвращения спама.
     *
     * Логирует только при пересечении важных порогов: 50%, 30%, 15%, 5%
     *
     * @param batteryValue текущий уровень заряда батареи
     */
    private fun logBatteryThresholds(batteryValue: Int) {
        Log.d(
            TAG,
            "🔋 Проверка батареи: текущий=${batteryValue}%, последний зафиксированный=${lastLoggedBatteryLevel}%"
        )

        // Список важных порогов понижения заряда
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

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        LogModule.logSystemEvent(
                            context, bluetoothHelper, locationManager,
                            message, "БАТАРЕЯ"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Ошибка логирования батареи: ${e.message}")
                    }
                }
                break // Логируем только один порог за раз
            }
        }
    }

    /**
     * Логирует изменения состояния сумки (открыта/закрыта).
     *
     * @param newState новое состояние сумки
     */
    private fun logBagStateChange(newState: String) {
        if (lastLoggedBagState != newState) {
            lastLoggedBagState = newState
            val message = "Сумка ${if (newState == "Закрыт") "закрыта" else "открыта"}"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LogModule.logSystemEvent(
                        context, bluetoothHelper, locationManager,
                        message, "ДАТЧИК_ХОЛЛА"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Ошибка логирования состояния сумки: ${e.message}")
                }
            }
        }
    }

    // === ДИАГНОСТИКА И СТАТИСТИКА ===

    /**
     * Возвращает статистику обработки данных для мониторинга и отладки.
     *
     * @return объект DataProcessingStatistics с метриками производительности
     */
    fun getProcessingStatistics(): DataProcessingStatistics {
        val successRate = if (totalPacketsProcessed > 0) {
            ((totalPacketsProcessed - invalidDataCount).toFloat() / totalPacketsProcessed * 100).toInt()
        } else 100

        return DataProcessingStatistics(
            totalPacketsProcessed = totalPacketsProcessed,
            invalidPacketsCount = invalidDataCount,
            successRate = successRate,
            lastLoggedBatteryLevel = lastLoggedBatteryLevel,
            lastLoggedBagState = lastLoggedBagState ?: "Неизвестно"
        )
    }

    /**
     * Сбрасывает все счётчики и кэшированные данные.
     * Используется при переподключении устройства или сбросе системы.
     */
    fun resetStatistics() {
        totalPacketsProcessed = 0
        invalidDataCount = 0
        lastLoggedBatteryLevel = 101
        lastLoggedBagState = null
        lastAccelerometerLogTime = 0L

        Log.d(TAG, "🔄 Статистика DataManager сброшена")
    }

    /**
     * Возвращает краткий отчёт о состоянии обработки данных.
     */
    fun getStatusReport(): String {
        val stats = getProcessingStatistics()
        return "DataManager: ${stats.successRate}% успешных | " +
                "Обработано: ${stats.totalPacketsProcessed} | " +
                "Ошибок: ${stats.invalidPacketsCount} | " +
                "Батарея: ${stats.lastLoggedBatteryLevel}% | " +
                "Сумка: ${stats.lastLoggedBagState}"
    }

    // === DATA CLASSES ===

    /**
     * Структура для хранения распарсенных данных Arduino с валидацией.
     *
     * @param battery уровень заряда 0-100% или null если некорректно
     * @param tempHot температура горячего отсека или null если ошибка/некорректно
     * @param tempHotString исходная строка температуры горячего отсека
     * @param tempCold температура холодного отсека или null если ошибка/некорректно
     * @param tempColdString исходная строка температуры холодного отсека
     * @param closedState состояние сумки 0/1 или null если некорректно
     * @param functionState количество активных функций или null если некорректно
     * @param accelerometer значение акселерометра или null если некорректно
     */
    data class ParsedArduinoData(
        val battery: Int?,
        val tempHot: Float?,
        val tempHotString: String,
        val tempCold: Float?,
        val tempColdString: String,
        val closedState: Int?,
        val functionState: Int?,
        val accelerometer: Float?
    ) {
        /**
         * Проверяет, содержит ли объект валидные данные.
         */
        fun hasValidData(): Boolean {
            return battery != null || tempHot != null || tempCold != null ||
                    closedState != null || functionState != null || accelerometer != null
        }

        /**
         * Возвращает краткое описание данных для логирования.
         */
        fun getSummary(): String {
            return "Battery: ${battery ?: "N/A"}%, " +
                    "TempHot: ${tempHotString}, " +
                    "TempCold: ${tempColdString}, " +
                    "Closed: ${closedState ?: "N/A"}, " +
                    "Functions: ${functionState ?: "N/A"}, " +
                    "Accel: ${accelerometer ?: "N/A"}"
        }

        /**
         * Проверяет наличие ошибок датчиков температуры.
         */
        fun hasTemperatureSensorErrors(): Boolean {
            return tempHotString == "er" || tempColdString == "er"
        }
    }

    /**
     * Статистика обработки данных для мониторинга производительности.
     *
     * @param totalPacketsProcessed общее количество обработанных пакетов
     * @param invalidPacketsCount количество некорректных пакетов
     * @param successRate процент успешно обработанных пакетов
     * @param lastLoggedBatteryLevel последний залогированный уровень батареи
     * @param lastLoggedBagState последнее залогированное состояние сумки
     */
    data class DataProcessingStatistics(
        val totalPacketsProcessed: Int,
        val invalidPacketsCount: Int,
        val successRate: Int,
        val lastLoggedBatteryLevel: Int,
        val lastLoggedBagState: String
    ) {
        /**
         * Проверяет, есть ли проблемы с обработкой данных.
         */
        fun hasIssues(): Boolean {
            return successRate < 80 || invalidPacketsCount > 50
        }

        /**
         * Возвращает рекомендации по улучшению качества данных.
         */
        fun getRecommendations(): List<String> {
            val recommendations = mutableListOf<String>()

            if (successRate < 80) {
                recommendations.add("Низкий процент успешной обработки данных")
                recommendations.add("Проверьте стабильность Bluetooth соединения")
            }

            if (invalidPacketsCount > 50) {
                recommendations.add("Много некорректных пакетов данных")
                recommendations.add("Возможны проблемы с Arduino или передачей данных")
            }

            if (totalPacketsProcessed == 0) {
                recommendations.add("Нет обработанных данных - проверьте подключение")
            }

            return recommendations
        }
    }

    /**
     * Контейнер для всех reactive UI состояний.
     * Используется для передачи состояний между MainActivity и DataManager.
     */
    data class UIStates(
        val batteryPercent: MutableState<Int>,
        val temp1: MutableState<String>,
        val temp2: MutableState<String>,
        val hallState: MutableState<String>,
        val functionState: MutableState<String>,
        val accelerometerData: MutableState<String>
    )
}