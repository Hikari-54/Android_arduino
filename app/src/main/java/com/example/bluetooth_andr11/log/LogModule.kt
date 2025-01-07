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

    // Получаем директорию для логов
    private fun getLogDirectory(context: Context): File {
        val logDir = File(context.filesDir, "LocationLogs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return logDir
    }

    // Получаем лог-файл на основе текущей даты
//    private fun getLogFile(context: Context): File {
//        val logDir = getLogDirectory(context)
//        val fileName = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
//        return File(logDir, "$fileName.txt")
//    }

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

    fun logEventWithLocation(
        context: Context,
        bluetoothHelper: BluetoothHelper,
        locationManager: LocationManager,
        event: String
    ) {
        if (bluetoothHelper.isDeviceConnected) {
            Log.d("LogModule", "Логи не записываются: устройство не подключено")
            return
        }

        // Используем метод LocationManager для получения координат
        val currentCoordinates = locationManager.getCurrentCoordinates()
        val logMessage = if (currentCoordinates.isEmpty()) {
            "$event @ Координаты недоступны"
        } else {
            "$event @ $currentCoordinates"
        }

        // Записываем событие в лог-файл
        logEvent(context, logMessage)
    }

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
