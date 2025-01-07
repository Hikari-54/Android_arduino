package com.example.bluetooth_andr11.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogHelper {

    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "events_log.txt"

    // Метод фильтрации логов по дате
    fun filterLogEntries(context: Context, startDate: String, endDate: String): List<String> {
        Log.d("LocationLogger", "Функция filterLogEntries вызвана")

        // Получаем директорию и файл логов
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        val logFile = File(logDir, LOG_FILE_NAME)
        Log.d("LocationLogger", "ПОПЫТКА ЧТЕНИЯ ИЗ: ${logFile.absolutePath}")

        // Проверяем, существует ли файл
        if (!logFile.exists()) {
            Toast.makeText(context, "Файл лога не найден.", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        // Парсим даты
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val start: Date
        val end: Date
        try {
            start = dateFormat.parse("$startDate 00:00:00") ?: return emptyList()
            end = dateFormat.parse("$endDate 23:59:59") ?: return emptyList()
        } catch (e: ParseException) {
            Toast.makeText(context, "Ошибка парсинга даты: ${e.message}", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        // Фильтруем записи
        val filteredEntries = mutableListOf<String>()
        logFile.forEachLine { line ->
            val timestamp = line.substringAfter("Time: ").substringBefore(",")
            val logDate = try {
                dateFormat.parse(timestamp)
            } catch (e: ParseException) {
                null
            }

            if (logDate != null && logDate in start..end) {
                filteredEntries.add(line)
            }
        }

        if (filteredEntries.isEmpty()) {
            Toast.makeText(context, "Записи за указанный период не найдены.", Toast.LENGTH_SHORT)
                .show()
        }

        return filteredEntries
    }
}
