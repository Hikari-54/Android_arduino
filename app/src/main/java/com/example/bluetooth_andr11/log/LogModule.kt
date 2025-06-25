package com.example.bluetooth_andr11.log

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Централизованный модуль логирования событий доставочной сумки с умной фильтрацией спама.
 *
 * Основные возможности:
 * - Интеллектуальное логирование GPS событий с предотвращением спама
 * - Контекстно-зависимое логирование (критические события vs обычные)
 * - Автоматическое добавление координат к событиям
 * - Ограничение частоты повторяющихся событий
 * - Специальная обработка температурных событий без ограничений
 * - Статистика и аналитика логов
 *
 * Архитектурные принципы:
 * - Thread-safe операции с файловой системой
 * - Минимальное влияние на производительность
 * - Автоматическое создание директорий логов
 * - Graceful handling ошибок без прерывания работы приложения
 * - Интеграция с BluetoothHelper и EnhancedLocationManager
 *
 * Типы событий:
 * - СИСТЕМА: системные события (запуск, закрытие, изменения настроек)
 * - ТЕМПЕРАТУРА: события температурного мониторинга (без ограничений)
 * - ДЕЙСТВИЕ: действия пользователя (команды управления)
 * - БАТАРЕЯ: события мониторинга заряда
 * - ДАТЧИК_ХОЛЛА: открытие/закрытие сумки
 * - АКСЕЛЕРОМЕТР: события тряски и движения
 * - СИСТЕМА_GPS: события GPS с умной фильтрацией
 */
object LogModule {
    private const val TAG = "LogModule"

    // === КОНСТАНТЫ КОНФИГУРАЦИИ ===

    /** Директория для хранения логов */
    private const val LOG_DIR_NAME = "logs"

    /** Основной файл событий */
    private const val EVENTS_LOG_FILE = "events_log.txt"

    /** Файл логов местоположения */
    private const val LOCATION_LOG_FILE = "location_log.txt"

    /** Интервал между повторными GPS сообщениями (5 минут) */
    private const val GPS_LOG_COOLDOWN = 5 * 60 * 1000L

    /** Частота логирования при недоступности GPS (каждый 10-й раз) */
    private const val GPS_UNAVAILABLE_LOG_FREQUENCY = 10

    // === СОСТОЯНИЕ МОДУЛЯ ===

    /** Форматтер для временных меток */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /** Кэш времени последних событий для предотвращения спама */
    private val lastLoggedEventTime = mutableMapOf<String, Long>()

    /** Состояние GPS для умной фильтрации */
    private var lastGpsState: Boolean? = null
    private var lastGpsLogTime = 0L
    private var consecutiveUnavailableCount = 0

    // === ОСНОВНЫЕ МЕТОДЫ ЛОГИРОВАНИЯ ===

    /**
     * Базовый метод логирования событий в файл
     *
     * @param context контекст для доступа к файловой системе
     * @param event текст события для записи
     */
    fun logEvent(context: Context, event: String) {
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, EVENTS_LOG_FILE)
            val timestamp = getCurrentTimestamp()
            logFile.appendText("$timestamp - $event\n")

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "📝 Событие записано: $event")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка записи лога: ${e.message}")
            // Не прерываем работу приложения при ошибках логирования
        }
    }

    /**
     * Умное логирование изменений состояния GPS с предотвращением спама
     *
     * @param context контекст приложения
     * @param isAvailable доступен ли GPS в данный момент
     * @param reason дополнительная информация о причине изменения
     */
    fun logGpsStateChange(context: Context, isAvailable: Boolean, reason: String = "") {
        val currentTime = System.currentTimeMillis()

        // Если состояние GPS не изменилось
        if (lastGpsState == isAvailable) {
            if (!isAvailable) {
                consecutiveUnavailableCount++

                // Логируем только каждый N-й раз или по таймауту
                if (consecutiveUnavailableCount % GPS_UNAVAILABLE_LOG_FREQUENCY == 0 ||
                    currentTime - lastGpsLogTime > GPS_LOG_COOLDOWN
                ) {
                    val logMessage = "GPS недоступен уже $consecutiveUnavailableCount раз подряд"
                    logEvent(context, "СИСТЕМА_GPS: $logMessage")
                    lastGpsLogTime = currentTime
                }
            }
            return // Выходим, так как состояние не изменилось
        }

        // Состояние изменилось - обязательно логируем
        lastGpsState = isAvailable
        lastGpsLogTime = currentTime

        val event = when {
            isAvailable -> {
                consecutiveUnavailableCount = 0 // Сбрасываем счетчик ошибок
                "GPS ВОССТАНОВЛЕН - местоположение доступно"
            }

            else -> {
                consecutiveUnavailableCount = 1
                "GPS НЕДОСТУПЕН - потеря сигнала" +
                        if (reason.isNotEmpty()) " ($reason)" else ""
            }
        }

        logEvent(context, "СИСТЕМА_GPS: $event")
        Log.i(TAG, "🛰️ GPS состояние изменилось: $isAvailable")
    }

    /**
     * Логирование событий с автоматическим добавлением координат
     *
     * @param context контекст приложения
     * @param bluetoothHelper для проверки состояния подключения
     * @param locationManager для получения координат
     * @param event текст события
     * @param critical критическое ли событие (логируется всегда)
     */
    fun logEventWithLocation(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String,
        critical: Boolean = false
    ) {
        // Критические события логируются всегда
        // Обычные события - только при подключенном устройстве
        if (!critical && !bluetoothHelper.isDeviceConnected) {
            Log.d(TAG, "⏭️ Пропущено событие: устройство не подключено")
            return
        }

        val locationInfo = locationManager.getLocationInfo()
        val logMessage = buildString {
            if (critical) append("КРИТИЧЕСКОЕ: ")
            append(event)
            append(" @ ")

            // Добавляем информацию о местоположении
            if (locationInfo.coordinates != "Неизвестно") {
                append("${locationInfo.coordinates} ")
                append("(${locationInfo.source}, ±${locationInfo.accuracy.toInt()}м)")
            } else {
                append("Координаты недоступны")
            }

            // Для критических событий добавляем статус Bluetooth
            if (critical) {
                val connectionStatus = if (bluetoothHelper.isDeviceConnected) {
                    "ПОДКЛЮЧЕНО"
                } else {
                    "ОТКЛЮЧЕНО"
                }
                append(" [BT: $connectionStatus]")
            }
        }

        logEvent(context, logMessage)
    }

    /**
     * Логирование с ограничением по времени для предотвращения спама
     *
     * @param context контекст приложения
     * @param bluetoothHelper для проверки состояния
     * @param locationManager для координат
     * @param event текст события
     * @param timeLimitSeconds минимальный интервал между одинаковыми событиями
     * @param critical критическое ли событие
     */
    fun logEventWithLimit(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String,
        timeLimitSeconds: Int = 60,
        critical: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastLoggedEventTime[event] ?: 0

        // Проверяем, прошло ли достаточно времени с последнего такого же события
        if (!critical && currentTime - lastTime < timeLimitSeconds * 1000) {
            return // Слишком рано для повторного логирования
        }

        // Обновляем время последнего события
        lastLoggedEventTime[event] = currentTime

        // Логируем событие с координатами
        logEventWithLocation(context, bluetoothHelper, locationManager, event, critical)
    }

    // === СПЕЦИАЛИЗИРОВАННЫЕ МЕТОДЫ ===

    /**
     * Логирование критических событий с особой обработкой GPS
     *
     * @param context контекст приложения
     * @param bluetoothHelper для проверки состояния
     * @param locationManager для координат
     * @param event текст критического события
     */
    fun logCriticalEvent(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String
    ) {
        // Специальная обработка GPS событий
        if (event.contains("GPS", ignoreCase = true) ||
            event.contains("местоположение", ignoreCase = true)
        ) {

            val isGpsAvailable = event.contains("включен", ignoreCase = true) ||
                    event.contains("восстановлен", ignoreCase = true)
            logGpsStateChange(context, isGpsAvailable, event)
        } else {
            // Обычные критические события
            logEventWithLocation(context, bluetoothHelper, locationManager, event, critical = true)
        }
    }

    /**
     * Логирование действий пользователя (всегда важны)
     *
     * @param context контекст приложения
     * @param bluetoothHelper для состояния
     * @param locationManager для координат
     * @param action описание действия пользователя
     */
    fun logUserAction(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        action: String
    ) {
        // Действия пользователя всегда логируются без ограничений
        logEventWithLocation(context, bluetoothHelper, locationManager, "ДЕЙСТВИЕ: $action")
    }

    /**
     * Логирование системных событий с категоризацией
     *
     * @param context контекст приложения
     * @param bluetoothHelper для состояния
     * @param locationManager для координат
     * @param event описание события
     * @param category категория события для группировки
     */
    fun logSystemEvent(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String,
        category: String = "СИСТЕМА"
    ) {
        when (category) {
            "ТЕМПЕРАТУРА" -> {
                // Температурные события критически важны - логируем БЕЗ ограничений
                logEventWithLocation(
                    context, bluetoothHelper, locationManager,
                    "$category: $event", critical = true
                )
            }

            else -> {
                // Остальные системные события - с ограничением частоты
                logEventWithLimit(
                    context, bluetoothHelper, locationManager,
                    "$category: $event",
                    timeLimitSeconds = 60 // 1 минута между повторениями
                )
            }
        }
    }

    /**
     * Простое логирование координат в отдельный файл для трекинга
     *
     * @param context контекст для доступа к файлам
     * @param location объект Location с координатами
     */
    fun logLocation(context: Context, location: Location) {
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, LOCATION_LOG_FILE)
            val timestamp = getCurrentTimestamp()

            // Формат: timestamp, lat, lon, accuracy, bearing
            val logEntry = buildString {
                append(timestamp)
                append(", ${location.latitude}")
                append(", ${location.longitude}")
                append(", ${location.accuracy}")
                append(", ${if (location.hasBearing()) location.bearing else 0.0f}")
                append("\n")
            }

            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка записи координат: ${e.message}")
        }
    }

    // === СТАТИСТИКА И АНАЛИТИКА ===

    /**
     * Получает подробную статистику логов для мониторинга
     *
     * @param context контекст для доступа к файлам
     * @return объект LogStatistics с аналитикой
     */
    fun getLogStatistics(context: Context): LogStatistics {
        return try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
            val logFile = File(logDir, EVENTS_LOG_FILE)

            if (!logFile.exists()) {
                LogStatistics(0, 0, 0, 0, "Нет логов")
            } else {
                val lines = logFile.readLines()

                // Подсчитываем различные типы событий
                val totalEvents = lines.size
                val criticalEvents = lines.count { it.contains("КРИТИЧЕСКОЕ") }
                val gpsEvents = lines.count { line ->
                    line.contains("🛰️") || line.contains("📡") ||
                            line.contains("📶") || line.contains("GPS")
                }
                val userActions = lines.count { it.contains("ДЕЙСТВИЕ:") }
                val temperatureEvents = lines.count { it.contains("ТЕМПЕРАТУРА:") }

                // Находим время последнего события
                val lastEventTime = lines.lastOrNull()
                    ?.substringBefore(" -")
                    ?.trim()
                    ?: "Нет событий"

                LogStatistics(
                    totalEvents = totalEvents,
                    criticalEvents = criticalEvents,
                    gpsEvents = gpsEvents,
                    userActions = userActions,
                    lastEventTime = lastEventTime,
                    temperatureEvents = temperatureEvents
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения статистики: ${e.message}")
            LogStatistics(0, 0, 0, 0, "Ошибка чтения", 0)
        }
    }

    /**
     * Очищает кэш событий для освобождения памяти
     */
    fun clearEventCache() {
        lastLoggedEventTime.clear()
        Log.d(TAG, "🧹 Кэш событий очищен")
    }

    /**
     * Получает информацию о состоянии GPS логирования
     */
    fun getGpsLoggingStatus(): GpsLoggingStatus {
        return GpsLoggingStatus(
            lastKnownState = lastGpsState,
            consecutiveUnavailableCount = consecutiveUnavailableCount,
            lastLogTime = lastGpsLogTime,
            cooldownRemaining = if (lastGpsLogTime > 0) {
                maxOf(0, GPS_LOG_COOLDOWN - (System.currentTimeMillis() - lastGpsLogTime))
            } else 0
        )
    }

    // === УТИЛИТАРНЫЕ МЕТОДЫ ===

    /**
     * Получает текущую временную метку в стандартном формате
     */
    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }

    // === DATA CLASSES ===

    /**
     * Статистика логов для аналитики и мониторинга
     *
     * @param totalEvents общее количество событий
     * @param criticalEvents количество критических событий
     * @param gpsEvents количество GPS событий
     * @param userActions количество действий пользователя
     * @param lastEventTime время последнего события
     * @param temperatureEvents количество температурных событий
     */
    data class LogStatistics(
        val totalEvents: Int,
        val criticalEvents: Int,
        val gpsEvents: Int,
        val userActions: Int,
        val lastEventTime: String,
        val temperatureEvents: Int = 0
    ) {
        /**
         * Возвращает краткую сводку статистики
         */
        fun getSummary(): String {
            return "Всего: $totalEvents | Критические: $criticalEvents | " +
                    "GPS: $gpsEvents | Действия: $userActions | Температура: $temperatureEvents"
        }

        /**
         * Возвращает детальную информацию
         */
        fun getDetailedInfo(): String {
            return buildString {
                appendLine("📊 Статистика логов:")
                appendLine("• Всего событий: $totalEvents")
                appendLine("• Критические события: $criticalEvents")
                appendLine("• GPS события: $gpsEvents")
                appendLine("• Действия пользователя: $userActions")
                appendLine("• Температурные события: $temperatureEvents")
                appendLine("• Последнее событие: $lastEventTime")
            }
        }

        /**
         * Проверяет, есть ли события в логах
         */
        fun hasEvents(): Boolean = totalEvents > 0

        /**
         * Вычисляет процент критических событий
         */
        fun getCriticalEventsPercentage(): Int {
            return if (totalEvents > 0) {
                (criticalEvents * 100) / totalEvents
            } else 0
        }
    }

    /**
     * Состояние GPS логирования для отладки
     */
    data class GpsLoggingStatus(
        val lastKnownState: Boolean?,
        val consecutiveUnavailableCount: Int,
        val lastLogTime: Long,
        val cooldownRemaining: Long
    ) {
        /**
         * Возвращает описание состояния
         */
        fun getStatusDescription(): String {
            val stateText = when (lastKnownState) {
                true -> "🟢 Доступен"
                false -> "🔴 Недоступен ($consecutiveUnavailableCount раз)"
                null -> "❓ Неизвестно"
            }

            val cooldownText = if (cooldownRemaining > 0) {
                " (кулдаун: ${cooldownRemaining / 1000}с)"
            } else ""

            return "GPS: $stateText$cooldownText"
        }
    }
}