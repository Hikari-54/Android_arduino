package com.example.bluetooth_andr11.log

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogModule {

    private val lastLoggedEventTime = mutableMapOf<String, Long>()

    // Получаем директорию для логов
    private fun getLogDirectory(context: Context): File {
        val logDir = File(context.filesDir, "LocationLogs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return logDir
    }

    private fun getLogFile(context: Context): File {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, "log.txt")
    }

    private fun getEventLogFile(context: Context): File {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, "events_log.txt")
    }

    // Чтение логов
    fun readLogs(context: Context): List<String> {
        val logDir = getLogDirectory(context)
        val logFiles = logDir.listFiles() ?: return emptyList()

        return logFiles.flatMap { file ->
            file.readLines()
        }
    }

    // Логирование местоположения
    fun logLocation(context: Context, location: Location) {
        try {
//            val logFile = getLogFile(context)
//            Для удобного чтения
            val logFile = File(context.getExternalFilesDir("logs"), "log.txt")
            Log.d("LocationLogger", "Лог-файл сохранён в: ${logFile.absolutePath}")

            // Создаем новый форматтер для каждого вызова
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val logEntry =
                "${dateFormat.format(Date())}, ${location.latitude}, ${location.longitude}, ${location.accuracy}, ${location.bearing}\n"

            BufferedWriter(FileWriter(logFile, true)).use { writer ->
                writer.append(logEntry)
            }

            Log.d("LogModule", "Локация записана: $logEntry")
        } catch (e: Exception) {
            Log.e("LogModule", "Ошибка записи локации", e)
        }
    }

    fun logEventWithLocationAndLimit(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: LocationManager,
        event: String,
        timeLimitSeconds: Int = 60,
        noRepeat: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        val lastTime = lastLoggedEventTime[event] ?: 0

        if (!bluetoothHelper.isDeviceConnected) return

        if (!noRepeat && currentTime - lastTime < timeLimitSeconds * 1000) return

        lastLoggedEventTime[event] = currentTime

        val currentCoordinates = locationManager.getCurrentCoordinates()

        // 🔥 ИСПРАВЛЕНИЕ: правильная проверка координат
        val logMessage = if (currentCoordinates == "Неизвестно" || currentCoordinates.isBlank()) {
            "$event @ Координаты недоступны"
        } else {
            "$event @ $currentCoordinates"
        }

        logEvent(context, logMessage)

        // Дополнительное логирование для отладки
        Log.d("LogModule", "Координаты при логировании: '$currentCoordinates'")
        Log.d("LogModule", "Финальное сообщение: $logMessage")
    }

//    fun logEventWithLocation(
//        context: Context,
//        bluetoothHelper: BluetoothHelper,
//        locationManager: LocationManager,
//        event: String
//    ) {
//        val currentTime = System.currentTimeMillis()
//        val lastTime = lastLoggedEventTime[event] ?: 0
//
//        // Проверяем, подключено ли устройство по Bluetooth
//        if (!bluetoothHelper.isDeviceConnected) {
//            Log.d("LogModule", "Логи не записываются: устройство не подключено")
//            return
//        }
//
//        // Ограничиваем частоту логирования одного и того же события раз в минуту
//        if (currentTime - lastTime < 60_000) {
//            Log.d("LogModule", "Событие '$event' пропущено, так как интервал меньше минуты")
//            return
//        }
//        lastLoggedEventTime[event] = currentTime
//
//        // Получаем текущие координаты
//        val currentCoordinates = locationManager.getCurrentCoordinates()
//        val logMessage = if (currentCoordinates.isEmpty()) {
//            "$event @ Координаты недоступны"
//        } else {
//            "$event @ $currentCoordinates"
//        }
//
//        // Записываем событие в лог-файл
//        logEvent(context, logMessage)
//        Log.d("LogModule", "Событие записано: $logMessage")
//    }


    fun logEvent(context: Context, event: String) {
        try {
            // Получаем путь к файлу
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs() // Создаем папку, если она отсутствует
            }

            val logFile = File(logDir, "events_log.txt")
            logFile.appendText("${getCurrentTimestamp()} - $event\n")

            // Логируем успешную запись
            Log.d("LogModule", "Лог записан: $event")
        } catch (e: Exception) {
            Log.e("LogModule", "Ошибка записи лога: ${e.message}")
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

}
