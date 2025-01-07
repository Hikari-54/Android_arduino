package com.example.bluetooth_andr11.log

import android.content.Context
import android.location.Location
import android.util.Log
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
    private fun getLogFile(context: Context): File {
        val logDir = getLogDirectory(context)
        val fileName = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return File(logDir, "$fileName.txt")
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

    // Чтение логов
    fun readLogs(context: Context): List<String> {
        val logDir = getLogDirectory(context)
        val logFiles = logDir.listFiles() ?: return emptyList()

        return logFiles.flatMap { file ->
            file.readLines()
        }
    }

    // Фильтрация логов по дате
    fun filterLogsByDate(context: Context, startDate: String, endDate: String): List<String> {
        val logEntries = readLogs(context)

        // Создаем новый форматтер для каждого вызова
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val start = dateFormat.parse("$startDate 00:00:00")
        val end = dateFormat.parse("$endDate 23:59:59")

        return logEntries.filter { entry ->
            val date = dateFormat.parse(entry.substringBefore(","))
            date in start..end
        }
    }
}
