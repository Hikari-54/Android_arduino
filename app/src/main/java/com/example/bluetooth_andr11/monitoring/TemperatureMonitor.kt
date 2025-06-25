package com.example.bluetooth_andr11.monitoring

import android.content.Context
import android.util.Log
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Интеллектуальный мониторинг температурных событий для системы доставки еды.
 *
 * Основные возможности:
 * - Контекстно-зависимый анализ температуры в горячем и холодном отсеках
 * - Интеллектуальное определение критических температурных событий
 * - Автоматическое логирование с координатами местоположения
 * - Валидация данных с защитой от некорректных значений датчиков
 * - Статистика и аналитика температурных трендов
 * - Асинхронная обработка для избежания блокировки UI потока
 *
 * Алгоритм работы:
 * 1. Получение данных температуры от Arduino через BluetoothHelper
 * 2. Валидация значений в допустимых диапазонах (-20°C до +80°C)
 * 3. Отслеживание пересечения пороговых значений для каждого отсека
 * 4. Генерация контекстно-зависимых сообщений о состоянии еды
 * 5. Асинхронное логирование событий с координатами
 * 6. Накопление статистики для анализа качества доставки
 *
 * Пороговые значения:
 *
 * Горячий отсек (нагрев): 40°C (комфорт) → 50°C (оптимум) → 60°C (очень горячо)
 * Горячий отсек (остывание): 45°C (теплая) → 35°C (умеренная) → 25°C (остыла)
 *
 * Холодный отсек (охлаждение): 15°C (прохладно) → 10°C (оптимум) → 5°C (очень холодно)
 * Холодный отсек (нагрев): 10°C (потеря холода) → 15°C (теряется свежесть) → 20°C (критично)
 *
 * Архитектурные принципы:
 * - Асинхронная обработка событий для производительности
 * - Fallback механизмы при ошибках записи логов
 * - Thread-safe операции с состоянием мониторинга
 * - Оптимизированное логирование без блокировки основного потока
 * - Интеграция с LogModule для централизованного логирования
 */
class TemperatureMonitor(
    private val context: Context,
    private val bluetoothHelper: BluetoothHelper,
    private val locationManager: EnhancedLocationManager
) {
    companion object {
        private const val TAG = "TemperatureMonitor"

        // === ПОРОГОВЫЕ ЗНАЧЕНИЯ ===

        /** Пороги нагрева горячего отсека (комфорт → горячо → очень горячо) */
        private val HOT_HEATING_THRESHOLDS = listOf(40, 50, 60)

        /** Пороги остывания горячего отсека (теплая → умеренная → остыла) */
        private val HOT_COOLING_THRESHOLDS = listOf(45, 35, 25)

        /** Пороги охлаждения холодного отсека (прохладно → холодно → очень холодно) */
        private val COLD_COOLING_THRESHOLDS = listOf(15, 10, 5)

        /** Пороги нагрева холодного отсека (нежелательные события) */
        private val COLD_WARMING_THRESHOLDS = listOf(10, 15, 20)

        // === ВАЛИДАЦИЯ ДАННЫХ ===

        /** Минимальная допустимая температура для защиты от ошибок датчиков */
        private const val MIN_VALID_TEMP = -20.0f

        /** Максимальная допустимая температура для защиты от ошибок датчиков */
        private const val MAX_VALID_TEMP = 80.0f

        /** Максимальное изменение температуры за одно измерение (защита от скачков) */
        private const val MAX_TEMP_CHANGE_PER_STEP = 10.0f

        /** Количество последовательных ошибок для логирования проблемы с датчиком */
        private const val ERROR_THRESHOLD = 3

        // === СПЕЦИАЛЬНЫЕ КЛЮЧИ ===

        /** Базовое смещение для ключей нагрева холодного отсека */
        private const val COLD_WARMING_KEY_OFFSET = -100
    }

    // === СОСТОЯНИЕ МОНИТОРИНГА ===

    /** Последняя температура горячего отсека */
    @Volatile
    private var lastUpperTemp: Int? = null

    /** Последняя температура холодного отсека */
    @Volatile
    private var lastLowerTemp: Int? = null

    /** Множество пройденных порогов для горячего отсека */
    private val upperThresholds = mutableSetOf<Int>()

    /** Множество пройденных порогов для холодного отсека */
    private val lowerThresholds = mutableSetOf<Int>()

    // === СТАТИСТИКА И АНАЛИТИКА ===

    /** Общее количество событий горячего отсека */
    @Volatile
    private var totalHotEvents = 0

    /** Общее количество событий холодного отсека */
    @Volatile
    private var totalColdEvents = 0

    /** Время последнего температурного события */
    @Volatile
    private var lastEventTime = 0L

    /** Счетчик последовательных ошибок валидации */
    @Volatile
    private var consecutiveErrorCount = 0

    // === ОСНОВНЫЕ МЕТОДЫ ===

    /**
     * Основная точка входа для обработки данных температуры от Arduino.
     *
     * Выполняет валидацию данных и передает их в соответствующие обработчики.
     * Обрабатывает как успешные измерения, так и ошибки датчиков.
     *
     * @param upperTemp температура горячего отсека (null при ошибке датчика)
     * @param lowerTemp температура холодного отсека (null при ошибке датчика)
     */
    fun processTemperatures(upperTemp: Float?, lowerTemp: Float?) {
        try {
            // Обрабатываем горячий отсек
            upperTemp?.let { temp ->
                if (isValidTemperature(temp)) {
                    processHotCompartment(temp)
                    consecutiveErrorCount = 0 // Сбрасываем счетчик ошибок при успешной обработке
                } else {
                    Log.w(TAG, "⚠️ Некорректная температура горячего отсека: $temp°C")
                    handleTemperatureError("горячий", temp)
                }
            }

            // Обрабатываем холодный отсек
            lowerTemp?.let { temp ->
                if (isValidTemperature(temp)) {
                    processColdCompartment(temp)
                    consecutiveErrorCount = 0 // Сбрасываем счетчик ошибок при успешной обработке
                } else {
                    Log.w(TAG, "⚠️ Некорректная температура холодного отсека: $temp°C")
                    handleTemperatureError("холодный", temp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Критическая ошибка обработки температур: ${e.message}")
            consecutiveErrorCount++

            // Логируем критические ошибки для диагностики
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LogModule.logSystemEvent(
                        context, bluetoothHelper, locationManager,
                        "Критическая ошибка TemperatureMonitor: ${e.message}",
                        "ОШИБКА_СИСТЕМЫ"
                    )
                } catch (logError: Exception) {
                    Log.e(TAG, "❌ Не удалось записать лог ошибки: ${logError.message}")
                }
            }
        }
    }

    // === ОБРАБОТКА ОТСЕКОВ ===

    /**
     * Обрабатывает изменения температуры в горячем отсеке.
     *
     * Отслеживает тренды нагрева и остывания, проверяет пороговые значения
     * и генерирует соответствующие события для логирования.
     *
     * @param temp текущая температура горячего отсека в градусах Цельсия
     */
    private fun processHotCompartment(temp: Float) {
        val tempInt = temp.toInt()
        val previous = lastUpperTemp

        // Проверяем на аномально резкие изменения температуры
        if (previous != null && kotlin.math.abs(tempInt - previous) > MAX_TEMP_CHANGE_PER_STEP) {
            Log.w(
                TAG,
                "🚨 Резкое изменение температуры горячего отсека: ${previous}°C → ${tempInt}°C"
            )

            // Логируем подозрительные скачки температуры
            CoroutineScope(Dispatchers.IO).launch {
                LogModule.logSystemEvent(
                    context, bluetoothHelper, locationManager,
                    "Аномальный скачок температуры горячего отсека: ${previous}°C → ${tempInt}°C",
                    "ДАТЧИК_ТЕМПЕРАТУРЫ"
                )
            }
        }

        // Обновляем состояние
        lastUpperTemp = tempInt
        Log.d(TAG, "🔥 Горячий отсек: ${previous ?: "N/A"}°C → ${tempInt}°C")

        // Анализируем изменения только при наличии предыдущего значения
        if (previous != null && previous != tempInt) {
            when {
                tempInt > previous -> checkHotFoodHeating(tempInt, previous)
                tempInt < previous -> checkHotFoodCooling(tempInt, previous)
            }
        }
    }

    /**
     * Обрабатывает изменения температуры в холодном отсеке.
     *
     * Отслеживает тренды охлаждения и нежелательного нагрева,
     * проверяет пороговые значения для холодных продуктов.
     *
     * @param temp текущая температура холодного отсека в градусах Цельсия
     */
    private fun processColdCompartment(temp: Float) {
        val tempInt = temp.toInt()
        val previous = lastLowerTemp

        // Проверяем на аномально резкие изменения температуры
        if (previous != null && kotlin.math.abs(tempInt - previous) > MAX_TEMP_CHANGE_PER_STEP) {
            Log.w(
                TAG,
                "🚨 Резкое изменение температуры холодного отсека: ${previous}°C → ${tempInt}°C"
            )

            // Логируем подозрительные скачки температуры
            CoroutineScope(Dispatchers.IO).launch {
                LogModule.logSystemEvent(
                    context, bluetoothHelper, locationManager,
                    "Аномальный скачок температуры холодного отсека: ${previous}°C → ${tempInt}°C",
                    "ДАТЧИК_ТЕМПЕРАТУРЫ"
                )
            }
        }

        // Обновляем состояние
        lastLowerTemp = tempInt
        Log.d(TAG, "❄️ Холодный отсек: ${previous ?: "N/A"}°C → ${tempInt}°C")

        // Анализируем изменения только при наличии предыдущего значения
        if (previous != null && previous != tempInt) {
            when {
                tempInt < previous -> checkColdFoodCooling(tempInt, previous)
                tempInt > previous -> checkColdFoodWarming(tempInt, previous)
            }
        }
    }

    // === ПРОВЕРКА ПОРОГОВЫХ ЗНАЧЕНИЙ ===

    /**
     * Проверяет пороги нагрева горячей еды и генерирует соответствующие события
     */
    private fun checkHotFoodHeating(current: Int, previous: Int) {
        HOT_HEATING_THRESHOLDS.forEach { threshold ->
            if (current >= threshold && previous < threshold && !upperThresholds.contains(threshold)) {
                upperThresholds.add(threshold)
                val event = createHotFoodEvent(threshold, previous, current, isHeating = true)
                logTemperatureEvent(event)
                totalHotEvents++

                Log.i(TAG, "🔥⬆️ Горячий отсек достиг порога нагрева: ${threshold}°C")
            }
        }
    }

    /**
     * Проверяет пороги остывания горячей еды и генерирует соответствующие события
     */
    private fun checkHotFoodCooling(current: Int, previous: Int) {
        HOT_COOLING_THRESHOLDS.forEach { threshold ->
            // Используем отрицательные ключи для порогов остывания
            val negativeKey = -threshold
            if (current <= threshold && previous > threshold && !upperThresholds.contains(
                    negativeKey
                )
            ) {
                upperThresholds.add(negativeKey)
                val event = createHotFoodEvent(threshold, previous, current, isHeating = false)
                logTemperatureEvent(event)
                totalHotEvents++

                Log.i(TAG, "🔥⬇️ Горячий отсек достиг порога остывания: ${threshold}°C")
            }
        }
    }

    /**
     * Проверяет пороги охлаждения холодной еды и генерирует соответствующие события
     */
    private fun checkColdFoodCooling(current: Int, previous: Int) {
        COLD_COOLING_THRESHOLDS.forEach { threshold ->
            if (current <= threshold && previous > threshold && !lowerThresholds.contains(threshold)) {
                lowerThresholds.add(threshold)
                val event = createColdFoodEvent(threshold, previous, current, isCooling = true)
                logTemperatureEvent(event)
                totalColdEvents++

                Log.i(TAG, "❄️⬇️ Холодный отсек достиг порога охлаждения: ${threshold}°C")
            }
        }
    }

    /**
     * Проверяет пороги нежелательного нагрева холодной еды
     */
    private fun checkColdFoodWarming(current: Int, previous: Int) {
        COLD_WARMING_THRESHOLDS.forEach { threshold ->
            // Используем специальные ключи для нагрева холодной еды
            val specialKey = COLD_WARMING_KEY_OFFSET - threshold
            if (current >= threshold && previous < threshold && !lowerThresholds.contains(specialKey)) {
                lowerThresholds.add(specialKey)
                val event = createColdFoodEvent(threshold, previous, current, isCooling = false)
                logTemperatureEvent(event)
                totalColdEvents++

                Log.w(TAG, "❄️⬆️ Холодный отсек нежелательно нагрелся до: ${threshold}°C")
            }
        }
    }

    // === СОЗДАНИЕ СОБЫТИЙ ===

    /**
     * Создает детализированное событие для горячего отсека с контекстно-зависимым сообщением
     */
    private fun createHotFoodEvent(
        threshold: Int,
        previous: Int,
        current: Int,
        isHeating: Boolean
    ): TemperatureEvent {
        val (message, severity) = if (isHeating) {
            when (threshold) {
                40 -> Pair(
                    "Еда нагрелась до ${threshold}°C - комфортная температура подачи",
                    EventSeverity.INFO
                )

                50 -> Pair(
                    "Еда горячая ${threshold}°C - оптимальная температура для горячих блюд",
                    EventSeverity.SUCCESS
                )

                60 -> Pair(
                    "Еда очень горячая ${threshold}°C - осторожно при подаче клиенту",
                    EventSeverity.WARNING
                )

                else -> Pair(
                    "Температура достигла ${current}°C",
                    EventSeverity.INFO
                )
            }
        } else {
            when (threshold) {
                45 -> Pair(
                    "Еда остыла до ${threshold}°C - еще достаточно теплая",
                    EventSeverity.INFO
                )

                35 -> Pair(
                    "Еда остыла до ${threshold}°C - умеренная температура",
                    EventSeverity.WARNING
                )

                25 -> Pair(
                    "Еда остыла до ${threshold}°C - приближается к комнатной температуре",
                    EventSeverity.CRITICAL
                )

                else -> Pair(
                    "Температура снизилась до ${current}°C",
                    EventSeverity.INFO
                )
            }
        }

        return TemperatureEvent(
            compartment = "ГОРЯЧИЙ",
            icon = "🔥",
            direction = if (isHeating) "⬆️" else "⬇️",
            message = message,
            details = "было ${previous}°C → стало ${current}°C",
            severity = severity,
            timestamp = System.currentTimeMillis(),
            threshold = threshold,
            temperatureChange = current - previous
        )
    }

    /**
     * Создает детализированное событие для холодного отсека с контекстно-зависимым сообщением
     */
    private fun createColdFoodEvent(
        threshold: Int,
        previous: Int,
        current: Int,
        isCooling: Boolean
    ): TemperatureEvent {
        val (message, severity) = if (isCooling) {
            when (threshold) {
                15 -> Pair(
                    "Напитки охладились до ${threshold}°C - приятная прохлада",
                    EventSeverity.INFO
                )

                10 -> Pair(
                    "Продукты холодные ${threshold}°C - оптимальная температура хранения",
                    EventSeverity.SUCCESS
                )

                5 -> Pair(
                    "Очень холодно ${threshold}°C - отличное охлаждение",
                    EventSeverity.INFO
                )

                else -> Pair(
                    "Температура снизилась до ${current}°C",
                    EventSeverity.INFO
                )
            }
        } else {
            when (threshold) {
                10 -> Pair(
                    "Продукты нагрелись до ${threshold}°C - уже не холодные",
                    EventSeverity.WARNING
                )

                15 -> Pair(
                    "Температура поднялась до ${threshold}°C - теряется свежесть",
                    EventSeverity.WARNING
                )

                20 -> Pair(
                    "Нагрев до ${threshold}°C - критическая потеря охлаждения",
                    EventSeverity.CRITICAL
                )

                else -> Pair(
                    "Нежелательный нагрев до ${current}°C",
                    EventSeverity.WARNING
                )
            }
        }

        return TemperatureEvent(
            compartment = "ХОЛОДНЫЙ",
            icon = "❄️",
            direction = if (isCooling) "⬇️" else "⬆️",
            message = message,
            details = "было ${previous}°C → стало ${current}°C",
            severity = severity,
            timestamp = System.currentTimeMillis(),
            threshold = threshold,
            temperatureChange = current - previous
        )
    }

    // === ЛОГИРОВАНИЕ ===

    /**
     * Асинхронно логирует температурное событие с координатами местоположения
     */
    private fun logTemperatureEvent(event: TemperatureEvent) {
        val formattedMessage = formatEventMessage(event)

        Log.d(TAG, "🌡️ СОБЫТИЕ: $formattedMessage")
        lastEventTime = System.currentTimeMillis()

        // Асинхронная запись в лог для избежания блокировки UI потока
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Пытаемся записать напрямую в файл для детального логирования
                writeToLogFile(formattedMessage)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка прямой записи в лог: ${e.message}")

                // Fallback к LogModule при ошибке записи файла
                try {
                    LogModule.logSystemEvent(
                        context, bluetoothHelper, locationManager,
                        formattedMessage, "ТЕМПЕРАТУРА"
                    )
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "❌ Критическая ошибка логирования: ${fallbackError.message}")
                }
            }
        }
    }

    /**
     * Форматирует сообщение о температурном событии с иконками важности
     */
    private fun formatEventMessage(event: TemperatureEvent): String {
        val severityIcon = when (event.severity) {
            EventSeverity.INFO -> "ℹ️"
            EventSeverity.SUCCESS -> "✅"
            EventSeverity.WARNING -> "⚠️"
            EventSeverity.CRITICAL -> "🚨"
        }

        return "$severityIcon ${event.icon} ${event.compartment} ОТСЕК ${event.direction} ${event.message} (${event.details})"
    }

    /**
     * Записывает событие непосредственно в файл лога с координатами
     */
    private fun writeToLogFile(message: String) {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val logFile = File(logDir, "events_log.txt")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        // Получаем актуальную информацию о местоположении
        val locationInfo = locationManager.getLocationInfo()
        val coordinates = if (locationInfo.coordinates != "Неизвестно") {
            "${locationInfo.coordinates} (${locationInfo.source}, ±${locationInfo.accuracy.toInt()}м)"
        } else {
            "Координаты недоступны"
        }

        val logEntry = "$timestamp - ТЕМПЕРАТУРА: $message @ $coordinates\n"

        try {
            logFile.appendText(logEntry)
            Log.d(TAG, "✅ Записано в лог: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка записи в файл: ${e.message}")
            throw e // Перебрасываем для fallback логирования
        }
    }

    // === ВАЛИДАЦИЯ И ОБРАБОТКА ОШИБОК ===

    /**
     * Валидирует температурные данные на корректность и допустимые диапазоны
     */
    private fun isValidTemperature(temp: Float): Boolean {
        return temp.isFinite() && temp in MIN_VALID_TEMP..MAX_VALID_TEMP
    }

    /**
     * Обрабатывает ошибки температурных датчиков с накоплением статистики
     */
    private fun handleTemperatureError(compartment: String, temp: Float) {
        consecutiveErrorCount++

        // Логируем только после накопления нескольких ошибок подряд
        if (consecutiveErrorCount >= ERROR_THRESHOLD) {
            val errorMessage = "Частые ошибки датчика $compartment отсека (значение: $temp°C, " +
                    "последовательных ошибок: $consecutiveErrorCount)"
            Log.e(TAG, "🚨 $errorMessage")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    writeToLogFile("🚨 ОШИБКА ДАТЧИКА: $errorMessage")
                } catch (e: Exception) {
                    // Fallback к LogModule
                    LogModule.logSystemEvent(
                        context, bluetoothHelper, locationManager,
                        errorMessage, "ОШИБКА_ДАТЧИКА"
                    )
                }
            }
        }
    }

    // === УПРАВЛЕНИЕ СОСТОЯНИЕМ ===

    /**
     * Полностью сбрасывает состояние мониторинга температуры
     * Используется при перезапуске мониторинга или для очистки данных
     */
    fun reset() {
        synchronized(this) {
            upperThresholds.clear()
            lowerThresholds.clear()
            lastUpperTemp = null
            lastLowerTemp = null
            totalHotEvents = 0
            totalColdEvents = 0
            consecutiveErrorCount = 0
            lastEventTime = 0L
        }

        Log.d(TAG, "🔄 Состояние мониторинга температуры полностью сброшено")

        // Логируем сброс для аудита
        CoroutineScope(Dispatchers.IO).launch {
            LogModule.logSystemEvent(
                context, bluetoothHelper, locationManager,
                "Состояние TemperatureMonitor сброшено", "СИСТЕМА"
            )
        }
    }

    /**
     * Частичный сброс только счетчиков ошибок (сохраняет температуры и пороги)
     */
    fun resetErrorCounters() {
        consecutiveErrorCount = 0
        Log.d(TAG, "🔄 Счетчики ошибок сброшены")
    }

    // === СТАТИСТИКА И МОНИТОРИНГ ===

    /**
     * Возвращает подробную статистику работы температурного мониторинга
     */
    fun getDetailedStatistics(): TemperatureStatistics {
        val uptime = if (lastEventTime > 0) {
            System.currentTimeMillis() - lastEventTime
        } else 0L

        return TemperatureStatistics(
            hotCompartmentTemp = lastUpperTemp,
            coldCompartmentTemp = lastLowerTemp,
            hotEventsCount = totalHotEvents,
            coldEventsCount = totalColdEvents,
            activeHotThresholds = upperThresholds.size,
            activeColdThresholds = lowerThresholds.size,
            consecutiveErrors = consecutiveErrorCount,
            lastEventTimestamp = lastEventTime,
            uptimeMs = uptime,
            isMonitoringActive = isMonitoringActive(),
            hasValidData = lastUpperTemp != null || lastLowerTemp != null
        )
    }

    /**
     * Проверяет, активен ли мониторинг (получены ли данные)
     */
    fun isMonitoringActive(): Boolean {
        return lastUpperTemp != null || lastLowerTemp != null
    }

    /**
     * Проверяет, есть ли проблемы с датчиками
     */
    fun hasSensorIssues(): Boolean {
        return consecutiveErrorCount >= ERROR_THRESHOLD
    }

    /**
     * Возвращает краткий отчет о текущем состоянии мониторинга
     */
    fun getStatusReport(): String {
        val hot = lastUpperTemp?.let { "${it}°C" } ?: "N/A"
        val cold = lastLowerTemp?.let { "${it}°C" } ?: "N/A"
        val totalEvents = totalHotEvents + totalColdEvents
        val status = when {
            consecutiveErrorCount >= ERROR_THRESHOLD -> "⚠️"
            !isMonitoringActive() -> "⏸️"
            totalEvents > 0 -> "✅"
            else -> "🟡"
        }

        return "$status 🔥 $hot | ❄️ $cold | События: $totalEvents | Ошибки: $consecutiveErrorCount"
    }

    /**
     * Возвращает рекомендации по состоянию температуры
     */
    fun getTemperatureRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        lastUpperTemp?.let { hotTemp ->
            when {
                hotTemp < 25 -> recommendations.add("🔥 Горячая еда остыла - рассмотрите включение нагрева")
                hotTemp > 65 -> recommendations.add("🔥 Очень высокая температура - осторожно при подаче")
                hotTemp in 45..55 -> recommendations.add("🔥 Оптимальная температура горячих блюд")
                else -> { /* Другие температуры не требуют рекомендаций */
                }
            }
        }

        lastLowerTemp?.let { coldTemp ->
            when {
                coldTemp > 18 -> recommendations.add("❄️ Холодные продукты нагрелись - проверьте охлаждение")
                coldTemp < 3 -> recommendations.add("❄️ Очень низкая температура - возможно замерзание")
                coldTemp in 5..12 -> recommendations.add("❄️ Идеальная температура для холодных продуктов")
                else -> { /* Другие температуры не требуют рекомендаций */
                }
            }
        }

        if (consecutiveErrorCount >= ERROR_THRESHOLD) {
            recommendations.add("⚠️ Проблемы с датчиками - требуется диагностика")
        }

        if (!isMonitoringActive()) {
            recommendations.add("📊 Мониторинг неактивен - нет данных от датчиков")
        }

        return recommendations
    }

    // === DATA CLASSES ===

    /**
     * Уровни важности температурных событий для правильной приоритизации
     */
    enum class EventSeverity {
        /** Информационное событие - обычные изменения температуры */
        INFO,

        /** Успешное достижение оптимальной температуры */
        SUCCESS,

        /** Предупреждение о потенциальной проблеме */
        WARNING,

        /** Критическая ситуация, требующая немедленного внимания */
        CRITICAL
    }

    /**
     * Полная информация о температурном событии для логирования и анализа
     *
     * @param compartment тип отсека (ГОРЯЧИЙ/ХОЛОДНЫЙ)
     * @param icon эмодзи иконка для визуального отображения
     * @param direction направление изменения температуры (⬆️/⬇️)
     * @param message понятное описание события
     * @param details технические детали изменения
     * @param severity уровень важности события
     * @param timestamp время события в миллисекундах
     * @param threshold пороговое значение, которое было пересечено
     * @param temperatureChange изменение температуры в градусах
     */
    data class TemperatureEvent(
        val compartment: String,
        val icon: String,
        val direction: String,
        val message: String,
        val details: String,
        val severity: EventSeverity,
        val timestamp: Long,
        val threshold: Int,
        val temperatureChange: Int
    ) {
        /**
         * Возвращает отформатированное время события
         */
        fun getFormattedTime(): String {
            return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        }

        /**
         * Проверяет, является ли событие критическим
         */
        fun isCritical(): Boolean = severity == EventSeverity.CRITICAL

        /**
         * Возвращает полное описание события
         */
        fun getFullDescription(): String {
            return "$icon $compartment отсек $direction $message ($details) в ${getFormattedTime()}"
        }

        /**
         * Возвращает краткое описание для уведомлений
         */
        fun getShortDescription(): String {
            return "$compartment: $message"
        }
    }

    /**
     * Подробная статистика работы температурного мониторинга
     *
     * @param hotCompartmentTemp текущая температура горячего отсека
     * @param coldCompartmentTemp текущая температура холодного отсека
     * @param hotEventsCount количество событий горячего отсека
     * @param coldEventsCount количество событий холодного отсека
     * @param activeHotThresholds количество пройденных порогов горячего отсека
     * @param activeColdThresholds количество пройденных порогов холодного отсека
     * @param consecutiveErrors количество последовательных ошибок
     * @param lastEventTimestamp время последнего события
     * @param uptimeMs время работы мониторинга
     * @param isMonitoringActive активен ли мониторинг
     * @param hasValidData есть ли валидные данные
     */
    data class TemperatureStatistics(
        val hotCompartmentTemp: Int?,
        val coldCompartmentTemp: Int?,
        val hotEventsCount: Int,
        val coldEventsCount: Int,
        val activeHotThresholds: Int,
        val activeColdThresholds: Int,
        val consecutiveErrors: Int,
        val lastEventTimestamp: Long,
        val uptimeMs: Long,
        val isMonitoringActive: Boolean,
        val hasValidData: Boolean
    ) {
        /**
         * Возвращает общее количество температурных событий
         */
        fun getTotalEvents(): Int = hotEventsCount + coldEventsCount

        /**
         * Проверяет наличие активных проблем с датчиками
         */
        fun hasActiveIssues(): Boolean = consecutiveErrors > 0

        /**
         * Возвращает время последнего события в читаемом формате
         */
        fun getFormattedLastEvent(): String {
            return if (lastEventTimestamp > 0) {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(lastEventTimestamp))
            } else {
                "Нет событий"
            }
        }

        /**
         * Возвращает время работы мониторинга в читаемом формате
         */
        fun getFormattedUptime(): String {
            return if (uptimeMs > 0) {
                val seconds = uptimeMs / 1000
                val minutes = seconds / 60
                val hours = minutes / 60

                when {
                    hours > 0 -> "${hours}ч ${minutes % 60}м"
                    minutes > 0 -> "${minutes}м ${seconds % 60}с"
                    else -> "${seconds}с"
                }
            } else {
                "Неактивен"
            }
        }

        /**
         * Возвращает статус состояния мониторинга
         */
        fun getStatusText(): String {
            return when {
                !isMonitoringActive -> "⏸️ Неактивен"
                !hasValidData -> "📵 Нет данных"
                consecutiveErrors > 0 -> "⚠️ Есть ошибки"
                getTotalEvents() == 0 -> "🟡 Ожидание событий"
                else -> "✅ Работает нормально"
            }
        }

        /**
         * Возвращает краткую сводку статистики
         */
        fun getSummary(): String {
            return "События: ${getTotalEvents()} | Последнее: ${getFormattedLastEvent()} | " +
                    "Статус: ${getStatusText()} | Время работы: ${getFormattedUptime()}"
        }

        /**
         * Возвращает детальный отчет о работе мониторинга
         */
        fun getDetailedReport(): String {
            return buildString {
                appendLine("🌡️ Отчет температурного мониторинга:")
                appendLine("• Статус: ${getStatusText()}")
                appendLine("• Горячий отсек: ${hotCompartmentTemp?.let { "${it}°C" } ?: "N/A"}")
                appendLine("• Холодный отсек: ${coldCompartmentTemp?.let { "${it}°C" } ?: "N/A"}")
                appendLine("• События горячего отсека: $hotEventsCount")
                appendLine("• События холодного отсека: $coldEventsCount")
                appendLine("• Всего событий: ${getTotalEvents()}")
                appendLine("• Пройдено порогов (горячий): $activeHotThresholds")
                appendLine("• Пройдено порогов (холодный): $activeColdThresholds")
                appendLine("• Последовательные ошибки: $consecutiveErrors")
                appendLine("• Последнее событие: ${getFormattedLastEvent()}")
                appendLine("• Время работы: ${getFormattedUptime()}")
            }
        }

        /**
         * Проверяет эффективность работы мониторинга
         */
        fun getEfficiencyRating(): String {
            return when {
                !isMonitoringActive -> "Неактивен"
                consecutiveErrors > 3 -> "Низкая"
                getTotalEvents() == 0 -> "Ожидание"
                getTotalEvents() > 10 -> "Высокая"
                else -> "Нормальная"
            }
        }

        /**
         * Возвращает рекомендации по улучшению работы
         */
        fun getImprovementSuggestions(): List<String> {
            val suggestions = mutableListOf<String>()

            if (!isMonitoringActive) {
                suggestions.add("Проверьте подключение к Arduino устройству")
            }

            if (consecutiveErrors > 0) {
                suggestions.add("Проверьте исправность температурных датчиков")
            }

            if (!hasValidData) {
                suggestions.add("Убедитесь, что данные передаются от датчиков")
            }

            if (getTotalEvents() == 0 && isMonitoringActive) {
                suggestions.add("Мониторинг активен, но событий нет - это нормально")
            }

            return suggestions
        }
    }
}