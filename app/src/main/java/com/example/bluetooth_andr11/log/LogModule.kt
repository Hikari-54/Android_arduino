// 🔥 ОБНОВЛЕННЫЙ LogModule.kt с умным GPS логированием

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

object LogModule {
    private const val TAG = "LogModule"
    private val lastLoggedEventTime = mutableMapOf<String, Long>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 🔥 НОВОЕ: Состояние GPS для предотвращения спама
    private var lastGpsState: Boolean? = null
    private var lastGpsLogTime = 0L
    private var consecutiveUnavailableCount = 0
    private val GPS_LOG_COOLDOWN = 5 * 60 * 1000L // 5 минут между повторными сообщениями

    // Основной метод логирования событий
    fun logEvent(context: Context, event: String) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "events_log.txt")
            logFile.appendText("${getCurrentTimestamp()} - $event\n")

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Событие записано: $event")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи лога: ${e.message}")
        }
    }

    // 🔥 НОВЫЙ метод для умного логирования GPS изменений
    fun logGpsStateChange(context: Context, isAvailable: Boolean, reason: String = "") {
        val currentTime = System.currentTimeMillis()

        // Если состояние не изменилось, проверяем нужно ли логировать
        if (lastGpsState == isAvailable) {
            if (!isAvailable) {
                consecutiveUnavailableCount++

                // Логируем каждый 10-й раз недоступности или раз в 5 минут
                if (consecutiveUnavailableCount % 10 == 0 ||
                    currentTime - lastGpsLogTime > GPS_LOG_COOLDOWN
                ) {

                    val logMessage = "GPS недоступен уже ${consecutiveUnavailableCount} раз подряд"
                    logEvent(context, "GPS СИСТЕМА: $logMessage")
                    lastGpsLogTime = currentTime
                }
            }
            return // Состояние не изменилось, выходим
        }

        // Состояние изменилось - логируем обязательно
        lastGpsState = isAvailable
        lastGpsLogTime = currentTime

        val event = when {
            isAvailable -> {
                consecutiveUnavailableCount = 0 // Сбрасываем счетчик
                "GPS ВОССТАНОВЛЕН - местоположение доступно"
            }

            else -> {
                consecutiveUnavailableCount = 1
                "GPS НЕДОСТУПЕН - потеря сигнала" + if (reason.isNotEmpty()) " ($reason)" else ""
            }
        }

        logEvent(context, "GPS СИСТЕМА: $event")
        Log.i(TAG, "GPS состояние изменилось: $isAvailable")
    }

    // Логирование с координатами (основной метод для событий с местоположением)
    fun logEventWithLocation(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String,
        critical: Boolean = false
    ) {
        // Критические события логируются всегда, обычные - только при подключенном Bluetooth
        if (!critical && !bluetoothHelper.isDeviceConnected) {
            Log.d(TAG, "Пропущено событие: устройство не подключено")
            return
        }

        val locationInfo = locationManager.getLocationInfo()
        val logMessage = buildString {
            if (critical) append("КРИТИЧЕСКОЕ: ")
            append(event)
            append(" @ ")

            if (locationInfo.coordinates != "Неизвестно") {
                append("${locationInfo.coordinates} (${locationInfo.source}, ±${locationInfo.accuracy.toInt()}м)")
            } else {
                append("Координаты недоступны")
            }

            if (critical) {
                val connectionStatus =
                    if (bluetoothHelper.isDeviceConnected) "ПОДКЛЮЧЕНО" else "ОТКЛЮЧЕНО"
                append(" [BT: $connectionStatus]")
            }
        }

        logEvent(context, logMessage)
    }

    // Логирование с ограничением по времени (для предотвращения спама)
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

        if (!critical && currentTime - lastTime < timeLimitSeconds * 1000) {
            return // Пропускаем, если событие недавно логировалось
        }

        lastLoggedEventTime[event] = currentTime
        logEventWithLocation(context, bluetoothHelper, locationManager, event, critical)
    }

    // 🔥 УЛУЧШЕННОЕ логирование критических событий
    fun logCriticalEvent(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String
    ) {
        // 🔥 НОВОЕ: Фильтруем повторяющиеся GPS события
        if (event.contains("GPS") || event.contains("местоположение", ignoreCase = true)) {
            val isGpsEvent = event.contains("включен") || event.contains("восстановлен")
            logGpsStateChange(context, isGpsEvent, event)
        } else {
            // Обычные критические события логируем как раньше
            logEventWithLocation(context, bluetoothHelper, locationManager, event, critical = true)
        }
    }

    // 🔥 НОВЫЙ метод для логирования пользовательских действий (всегда важно)
    fun logUserAction(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        action: String
    ) {
        // Действия пользователя всегда логируются без ограничений
        logEventWithLocation(context, bluetoothHelper, locationManager, "ДЕЙСТВИЕ: $action")
    }

    // 🔥 НОВЫЙ метод для системных событий с фильтрацией
    fun logSystemEvent(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: EnhancedLocationManager,
        event: String,
        category: String = "СИСТЕМА"
    ) {
        // Системные события логируются с ограничением
        logEventWithLimit(
            context, bluetoothHelper, locationManager,
            "$category: $event",
            timeLimitSeconds = 300 // 5 минут между системными событиями
        )
    }

    // Логирование изменений в системе местоположения
    fun logLocationSystemChange(
        context: Context,
        locationManager: EnhancedLocationManager,
        description: String
    ) {
        // 🔥 ИЗМЕНЕНО: Используем умное логирование GPS
        val isAvailable = description.contains("доступно") || description.contains("включен")
        logGpsStateChange(context, isAvailable, description)
    }

    // 🔥 НОВЫЙ метод для получения сводки состояния GPS
    fun getGpsStatusSummary(): String {
        return buildString {
            append("GPS состояние: ")
            when (lastGpsState) {
                true -> append("✅ Доступен")
                false -> append("❌ Недоступен ($consecutiveUnavailableCount раз)")
                null -> append("❓ Неизвестно")
            }
        }
    }

    // Устаревшие методы для обратной совместимости
    @Deprecated("Используйте logEventWithLocation", ReplaceWith("logEventWithLocation"))
    fun logEventWithEnhancedLocation(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        enhancedLocationManager: EnhancedLocationManager,
        event: String
    ) {
        // 🔥 НОВОЕ: Определяем тип события и логируем соответствующе
        when {
            event.contains("GPS") || event.contains("местоположение", ignoreCase = true) -> {
                logCriticalEvent(context, bluetoothHelper, enhancedLocationManager, event)
            }

            event.contains("включен") || event.contains("выключен") -> {
                logUserAction(context, bluetoothHelper, enhancedLocationManager, event)
            }

            else -> {
                logEventWithLocation(context, bluetoothHelper, enhancedLocationManager, event)
            }
        }
    }

    @Deprecated("Используйте logEventWithLimit", ReplaceWith("logEventWithLimit"))
    fun logEventWithLocationAndLimit(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: Any,
        event: String,
        timeLimitSeconds: Int = 60,
        noRepeat: Boolean = false
    ) {
        if (locationManager is EnhancedLocationManager) {
            logEventWithLimit(context, bluetoothHelper, locationManager, event, timeLimitSeconds)
        } else {
            logEvent(context, "$event @ Координаты недоступны (старый API)")
        }
    }

    // Простое логирование местоположения (для внутреннего использования)
    fun logLocation(context: Context, location: Location) {
        try {
            val logFile = File(context.getExternalFilesDir(null), "logs/location_log.txt")
            logFile.parentFile?.mkdirs()

            val logEntry =
                "${getCurrentTimestamp()}, ${location.latitude}, ${location.longitude}, ${location.accuracy}, ${location.bearing}\n"
            logFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи локации: ${e.message}")
        }
    }

    // 🔥 УЛУЧШЕННАЯ статистика с GPS информацией
    fun getLogStatistics(context: Context): LogStatistics {
        return try {
            val logFile = File(context.getExternalFilesDir(null), "logs/events_log.txt")
            if (!logFile.exists()) {
                LogStatistics(0, 0, 0, 0, "Нет логов")
            } else {
                val lines = logFile.readLines()
                val totalEvents = lines.size
                val criticalEvents = lines.count { it.contains("КРИТИЧЕСКОЕ") }
                val gpsEvents = lines.count {
                    it.contains("🛰️") || it.contains("📡") || it.contains("📶") || it.contains("GPS")
                }
                val userActions = lines.count { it.contains("ДЕЙСТВИЕ:") }
                val lastEventTime =
                    lines.lastOrNull()?.substringBefore(" -")?.trim() ?: "Нет событий"

                LogStatistics(totalEvents, criticalEvents, gpsEvents, userActions, lastEventTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения статистики: ${e.message}")
            LogStatistics(0, 0, 0, 0, "Ошибка чтения")
        }
    }

    // Очистка старых логов
    fun clearOldLogs(context: Context, daysToKeep: Int = 30): Int {
        return try {
            val logFile = File(context.getExternalFilesDir(null), "logs/events_log.txt")
            if (!logFile.exists()) return 0

            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val lines = logFile.readLines()

            val filteredLines = lines.filter { line ->
                val timestamp = line.substringBefore(" -").trim()
                try {
                    val date = dateFormat.parse(timestamp)
                    date != null && date.time >= cutoffTime
                } catch (e: Exception) {
                    true // Сохраняем строки с некорректными датами
                }
            }

            val removedCount = lines.size - filteredLines.size
            if (removedCount > 0) {
                logFile.writeText(filteredLines.joinToString("\n") + "\n")
                Log.i(TAG, "Удалено $removedCount старых записей")
            }

            removedCount
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки логов: ${e.message}")
            0
        }
    }

    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }

    // 🔥 ОБНОВЛЕННЫЙ data class для статистики
    data class LogStatistics(
        val totalEvents: Int,
        val criticalEvents: Int,
        val gpsEvents: Int,
        val userActions: Int,
        val lastEventTime: String
    )
}