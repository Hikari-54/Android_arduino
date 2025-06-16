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

    // 🔥 ИСПРАВЛЕННЫЙ метод фильтрации логов по дате
    fun filterLogEntries(context: Context, startDate: String, endDate: String): List<String> {
        Log.d("LogHelper", "Функция filterLogEntries вызвана для периода: $startDate - $endDate")

        // Получаем директорию и файл логов
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        val logFile = File(logDir, LOG_FILE_NAME)
        Log.d("LogHelper", "Путь к лог-файлу: ${logFile.absolutePath}")

        // Проверяем, существует ли файл
        if (!logFile.exists()) {
            Log.w("LogHelper", "Файл лога не найден: ${logFile.absolutePath}")
            Toast.makeText(context, "Файл лога не найден.", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        // 🔥 НОВОЕ: Проверяем размер файла и первые строки для диагностики
        Log.d("LogHelper", "Размер файла: ${logFile.length()} байт")
        try {
            val allLines = logFile.readLines()
            Log.d("LogHelper", "Всего строк в файле: ${allLines.size}")
            if (allLines.isNotEmpty()) {
                Log.d("LogHelper", "Первая строка: ${allLines.first()}")
                Log.d("LogHelper", "Последняя строка: ${allLines.last()}")
            }
        } catch (e: Exception) {
            Log.e("LogHelper", "Ошибка чтения файла для диагностики: ${e.message}")
        }

        // Парсим даты
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val start: Date
        val end: Date
        try {
            start = dateFormat.parse("$startDate 00:00:00") ?: return emptyList()
            end = dateFormat.parse("$endDate 23:59:59") ?: return emptyList()
            Log.d(
                "LogHelper",
                "Период фильтрации: ${dateFormat.format(start)} - ${dateFormat.format(end)}"
            )
        } catch (e: ParseException) {
            Log.e("LogHelper", "Ошибка парсинга дат: ${e.message}")
            Toast.makeText(context, "Ошибка парсинга даты: ${e.message}", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        // 🔥 ИСПРАВЛЕНО: Правильный парсинг timestamp
        val filteredEntries = mutableListOf<String>()
        var processedLines = 0
        var parsedDates = 0
        var matchedLines = 0

        logFile.forEachLine { line ->
            processedLines++

            // 🔥 ИСПРАВЛЕНО: Правильный формат парсинга
            // Формат строки: "2024-01-01 12:00:00 - событие @ координаты"
            val timestamp = line.substringBefore(" -").trim() // ← ИСПРАВЛЕНО: убрали "Time: "

            if (timestamp.isNotEmpty() && timestamp.length >= 19) { // Минимальная длина для datetime
                val logDate = try {
                    dateFormat.parse(timestamp)
                } catch (e: ParseException) {
                    Log.w(
                        "LogHelper",
                        "Не удалось распарсить дату из строки: '$line', timestamp: '$timestamp'"
                    )
                    null
                }

                if (logDate != null) {
                    parsedDates++
                    if (logDate in start..end) {
                        filteredEntries.add(line)
                        matchedLines++
                    }
                }
            } else {
                Log.w("LogHelper", "Некорректный timestamp в строке: '$line'")
            }
        }

        Log.d("LogHelper", "Обработано строк: $processedLines")
        Log.d("LogHelper", "Успешно распарсено дат: $parsedDates")
        Log.d("LogHelper", "Найдено записей в периоде: $matchedLines")

        if (filteredEntries.isEmpty()) {
            val message =
                "Записи за указанный период не найдены. Проверено: $processedLines строк, распарсено дат: $parsedDates"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.w("LogHelper", message)
        } else {
            Log.i(
                "LogHelper",
                "Найдено ${filteredEntries.size} записей за период $startDate - $endDate"
            )
        }

        return filteredEntries
    }

    // 🔥 НОВЫЙ метод для диагностики лог-файла
    fun diagnoseLogFile(context: Context): String {
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        val logFile = File(logDir, LOG_FILE_NAME)

        return buildString {
            appendLine("=== ДИАГНОСТИКА ЛОГ-ФАЙЛА ===")
            appendLine("Путь к файлу: ${logFile.absolutePath}")
            appendLine("Файл существует: ${logFile.exists()}")

            if (logFile.exists()) {
                appendLine("Размер файла: ${logFile.length()} байт")
                appendLine("Можно читать: ${logFile.canRead()}")
                appendLine("Можно писать: ${logFile.canWrite()}")

                try {
                    val lines = logFile.readLines()
                    appendLine("Всего строк: ${lines.size}")

                    if (lines.isNotEmpty()) {
                        appendLine("\n=== ПЕРВЫЕ 3 СТРОКИ ===")
                        lines.take(3).forEachIndexed { index, line ->
                            appendLine("${index + 1}: $line")
                        }

                        appendLine("\n=== ПОСЛЕДНИЕ 3 СТРОКИ ===")
                        lines.takeLast(3).forEachIndexed { index, line ->
                            appendLine("${lines.size - 2 + index}: $line")
                        }

                        // Анализ формата
                        appendLine("\n=== АНАЛИЗ ФОРМАТА ===")
                        val dateFormat =
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        var validDates = 0
                        var invalidDates = 0

                        lines.take(10).forEach { line ->
                            val timestamp = line.substringBefore(" -").trim()
                            try {
                                dateFormat.parse(timestamp)
                                validDates++
                            } catch (e: ParseException) {
                                invalidDates++
                                appendLine("Некорректная дата: '$timestamp' в строке: '$line'")
                            }
                        }

                        appendLine("Корректные даты: $validDates")
                        appendLine("Некорректные даты: $invalidDates")
                    } else {
                        appendLine("Файл пуст!")
                    }
                } catch (e: Exception) {
                    appendLine("ОШИБКА чтения файла: ${e.message}")
                }
            } else {
                appendLine("Файл НЕ СУЩЕСТВУЕТ!")
                appendLine("Родительская папка существует: ${logDir.exists()}")
                if (logDir.exists()) {
                    appendLine("Содержимое папки logs:")
                    logDir.listFiles()?.forEach { file ->
                        appendLine("  - ${file.name} (${file.length()} байт)")
                    } ?: appendLine("  Папка пуста или недоступна")
                }
            }
        }
    }

    // 🔥 НОВЫЙ метод для создания тестового лога
    fun createTestLog(context: Context): Boolean {
        return try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val logFile = File(logDir, LOG_FILE_NAME)
            val timestamp =
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            logFile.appendText("$timestamp - ТЕСТ: Тестовое событие из LogHelper\n")

            Log.i("LogHelper", "Создан тестовый лог: ${logFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("LogHelper", "Ошибка создания тестового лога: ${e.message}")
            false
        }
    }

    // 🔥 НОВЫЙ метод для очистки старых логов
    fun clearOldLogs(context: Context, daysToKeep: Int = 30): Int {
        return try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
            val logFile = File(logDir, LOG_FILE_NAME)

            if (!logFile.exists()) return 0

            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            val lines = logFile.readLines()
            val filteredLines = lines.filter { line ->
                val timestamp = line.substringBefore(" -").trim()
                try {
                    val date = dateFormat.parse(timestamp)
                    date != null && date.time >= cutoffTime
                } catch (e: ParseException) {
                    true // Сохраняем строки с некорректными датами
                }
            }

            val removedCount = lines.size - filteredLines.size

            if (removedCount > 0) {
                logFile.writeText(filteredLines.joinToString("\n") + "\n")
                Log.i("LogHelper", "Удалено $removedCount старых записей (старше $daysToKeep дней)")
            }

            removedCount
        } catch (e: Exception) {
            Log.e("LogHelper", "Ошибка очистки старых логов: ${e.message}")
            0
        }
    }
}