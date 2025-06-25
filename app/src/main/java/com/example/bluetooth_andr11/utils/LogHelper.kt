package com.example.bluetooth_andr11.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Утилитарный класс для работы с файлами логов.
 * Предоставляет методы фильтрации, анализа и очистки логов.
 */
object LogHelper {
    private const val TAG = "LogHelper"
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "events_log.txt"
    private const val MAX_LOG_SIZE_MB = 50 // Максимальный размер файла лога в МБ
    private const val CHUNK_SIZE = 1000 // Размер чанка для чтения больших файлов

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Фильтрует записи логов по диапазону дат с оптимизацией для больших файлов
     * @param context контекст приложения
     * @param startDate начальная дата в формате "yyyy-MM-dd"
     * @param endDate конечная дата в формате "yyyy-MM-dd"
     * @return список отфильтрованных записей
     */
    suspend fun filterLogEntries(
        context: Context,
        startDate: String,
        endDate: String
    ): List<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Начинаем фильтрацию логов за период: $startDate - $endDate")

        val logFile = getLogFile(context)
        if (!validateLogFile(context, logFile)) {
            return@withContext emptyList()
        }

        val (start, end) = parseDateRange(context, startDate, endDate)
            ?: return@withContext emptyList()

        Log.d(TAG, "Период фильтрации: ${dateFormat.format(start)} - ${dateFormat.format(end)}")

        // Проверяем размер файла и выбираем стратегию чтения
        val fileSizeMB = logFile.length() / (1024 * 1024)
        Log.d(TAG, "Размер файла: ${fileSizeMB}MB")

        val filteredEntries = if (fileSizeMB > MAX_LOG_SIZE_MB) {
            filterLargeFile(logFile, start, end)
        } else {
            filterSmallFile(logFile, start, end)
        }

        logFilterResults(context, filteredEntries.size, startDate, endDate)
        filteredEntries
    }

    /**
     * Асинхронная очистка старых логов
     * @param context контекст приложения
     * @param daysToKeep количество дней для сохранения (по умолчанию 30)
     * @return количество удаленных записей
     */
    suspend fun clearOldLogs(
        context: Context,
        daysToKeep: Int = 30
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Начинаем очистку логов старше $daysToKeep дней")

        val logFile = getLogFile(context)
        if (!logFile.exists()) {
            Log.w(TAG, "Файл логов не существует")
            return@withContext 0
        }

        try {
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
            val lines = logFile.readLines()

            val filteredLines = lines.filter { line ->
                val timestamp = extractTimestamp(line)
                try {
                    val date = dateFormat.parse(timestamp)
                    date != null && date.time >= cutoffTime
                } catch (e: ParseException) {
                    Log.w(TAG, "Не удается распарсить дату в строке: $line")
                    true // Сохраняем строки с некорректными датами
                }
            }

            val removedCount = lines.size - filteredLines.size

            if (removedCount > 0) {
                // Создаем резервную копию перед изменением
                createBackup(logFile)

                logFile.writeText(filteredLines.joinToString("\n") + "\n")
                Log.i(TAG, "Удалено $removedCount записей старше $daysToKeep дней")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Очищено $removedCount старых записей",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            removedCount
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки логов: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Ошибка очистки логов: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            0
        }
    }

    /**
     * Анализирует статистику логов
     * @param context контекст приложения
     * @return объект с статистикой логов
     */
    suspend fun analyzeLogStatistics(context: Context): LogStatistics =
        withContext(Dispatchers.IO) {
            val logFile = getLogFile(context)
            if (!logFile.exists()) {
                return@withContext LogStatistics()
            }

            try {
                val lines = logFile.readLines()
                val totalEntries = lines.size
                val fileSizeKB = logFile.length() / 1024

                // Анализируем типы событий
                val criticalEvents = lines.count { it.contains("КРИТИЧЕСКОЕ", ignoreCase = true) }
                val temperatureEvents =
                    lines.count { it.contains("ТЕМПЕРАТУРА", ignoreCase = true) }
                val gpsEvents = lines.count {
                    it.contains("GPS", ignoreCase = true) ||
                            it.contains("местоположение", ignoreCase = true)
                }
                val batteryEvents = lines.count { it.contains("БАТАРЕЯ", ignoreCase = true) }

                // Находим первое и последнее событие
                val firstEvent = lines.firstOrNull()?.let { extractTimestamp(it) } ?: "Нет данных"
                val lastEvent = lines.lastOrNull()?.let { extractTimestamp(it) } ?: "Нет данных"

                LogStatistics(
                    totalEntries = totalEntries,
                    fileSizeKB = fileSizeKB,
                    criticalEvents = criticalEvents,
                    temperatureEvents = temperatureEvents,
                    gpsEvents = gpsEvents,
                    batteryEvents = batteryEvents,
                    firstEvent = firstEvent,
                    lastEvent = lastEvent
                )
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка анализа статистики: ${e.message}")
                LogStatistics(error = e.message)
            }
        }

    /**
     * Поиск записей по ключевому слову
     * @param context контекст приложения
     * @param keyword ключевое слово для поиска
     * @param limit максимальное количество результатов
     * @return список найденных записей
     */
    suspend fun searchLogEntries(
        context: Context,
        keyword: String,
        limit: Int = 100
    ): List<String> = withContext(Dispatchers.IO) {
        val logFile = getLogFile(context)
        if (!logFile.exists() || keyword.isBlank()) {
            return@withContext emptyList()
        }

        try {
            val results = mutableListOf<String>()
            logFile.forEachLine { line ->
                if (results.size >= limit) return@forEachLine

                if (line.contains(keyword, ignoreCase = true)) {
                    results.add(line)
                }
            }

            Log.d(TAG, "Поиск '$keyword': найдено ${results.size} записей")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска: ${e.message}")
            emptyList()
        }
    }

    // Приватные методы

    /**
     * Получает файл логов
     */
    private fun getLogFile(context: Context): File {
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        return File(logDir, LOG_FILE_NAME)
    }

    /**
     * Валидирует существование и доступность файла логов
     */
    private fun validateLogFile(context: Context, logFile: File): Boolean {
        if (!logFile.exists()) {
            Log.w(TAG, "Файл лога не найден: ${logFile.absolutePath}")
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Файл лога не найден", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        if (!logFile.canRead()) {
            Log.e(TAG, "Нет прав на чтение файла лога")
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Нет доступа к файлу лога", Toast.LENGTH_SHORT).show()
            }
            return false
        }

        Log.d(TAG, "Файл лога валиден. Размер: ${logFile.length()} байт")
        return true
    }

    /**
     * Парсит диапазон дат
     */
    private fun parseDateRange(
        context: Context,
        startDate: String,
        endDate: String
    ): Pair<Date, Date>? {
        return try {
            val start = dateFormat.parse("$startDate 00:00:00")
            val end = dateFormat.parse("$endDate 23:59:59")

            if (start == null || end == null) {
                throw ParseException("Не удалось распарсить даты", 0)
            }

            Pair(start, end)
        } catch (e: ParseException) {
            Log.e(TAG, "Ошибка парсинга дат: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    "Ошибка парсинга даты: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            null
        }
    }

    /**
     * Фильтрует большой файл по частям
     */
    private fun filterLargeFile(logFile: File, start: Date, end: Date): List<String> {
        Log.d(TAG, "Используем чанковое чтение для большого файла")

        val filteredEntries = mutableListOf<String>()
        var processedLines = 0

        try {
            logFile.forEachLine { line ->
                processedLines++

                // Логируем прогресс каждые 1000 строк
                if (processedLines % CHUNK_SIZE == 0) {
                    Log.d(TAG, "Обработано $processedLines строк")
                }

                val timestamp = extractTimestamp(line)
                if (timestamp.isNotEmpty()) {
                    try {
                        val logDate = dateFormat.parse(timestamp)
                        if (logDate != null && logDate in start..end) {
                            filteredEntries.add(line)
                        }
                    } catch (e: ParseException) {
                        // Игнорируем строки с некорректными датами
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения большого файла: ${e.message}")
        }

        Log.d(
            TAG,
            "Обработано всего $processedLines строк, найдено ${filteredEntries.size} записей"
        )
        return filteredEntries
    }

    /**
     * Фильтрует небольшой файл целиком в память
     */
    private fun filterSmallFile(logFile: File, start: Date, end: Date): List<String> {
        Log.d(TAG, "Загружаем весь файл в память для фильтрации")

        return try {
            val lines = logFile.readLines()
            var parsedDates = 0

            val filteredEntries = lines.mapNotNull { line ->
                val timestamp = extractTimestamp(line)
                if (timestamp.isNotEmpty()) {
                    try {
                        val logDate = dateFormat.parse(timestamp)
                        if (logDate != null) {
                            parsedDates++
                            if (logDate in start..end) line else null
                        } else null
                    } catch (e: ParseException) {
                        null
                    }
                } else null
            }

            Log.d(
                TAG,
                "Всего строк: ${lines.size}, распарсено дат: $parsedDates, найдено записей: ${filteredEntries.size}"
            )
            filteredEntries
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Недостаточно памяти для загрузки файла, переключаемся на чанковое чтение")
            filterLargeFile(logFile, start, end)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения файла: ${e.message}")
            emptyList()
        }
    }

    /**
     * Извлекает timestamp из строки лога
     */
    private fun extractTimestamp(line: String): String {
        // Формат строки: "2024-01-01 12:00:00 - событие @ координаты"
        return line.substringBefore(" -").trim()
    }

    /**
     * Создает резервную копию файла лога
     */
    private fun createBackup(logFile: File) {
        try {
            val backupFile = File(logFile.parent, "${logFile.nameWithoutExtension}_backup.txt")
            logFile.copyTo(backupFile, overwrite = true)
            Log.d(TAG, "Создана резервная копия: ${backupFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось создать резервную копию: ${e.message}")
        }
    }

    /**
     * Логирует результаты фильтрации
     */
    private fun logFilterResults(
        context: Context,
        foundEntries: Int,
        startDate: String,
        endDate: String
    ) {
        val message = if (foundEntries > 0) {
            "Найдено $foundEntries записей за период $startDate - $endDate"
        } else {
            "Записи за указанный период не найдены"
        }

        Log.i(TAG, message)

        if (foundEntries <= 0) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Экспортирует логи в выбранную директорию
     * @param context контекст приложения
     * @param targetDir целевая директория для экспорта
     * @return true если экспорт успешен
     */
    suspend fun exportLogs(context: Context, targetDir: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val logFile = getLogFile(context)
                if (!logFile.exists()) {
                    Log.w(TAG, "Файл логов не существует для экспорта")
                    return@withContext false
                }

                val exportFile = File(targetDir, "delivery_bag_logs_${getCurrentDateString()}.txt")
                logFile.copyTo(exportFile, overwrite = true)

                Log.i(TAG, "Логи экспортированы в: ${exportFile.absolutePath}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Логи экспортированы: ${exportFile.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка экспорта логов: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Ошибка экспорта: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                false
            }
        }

    /**
     * Сжимает логи для экономии места
     * @param context контекст приложения
     * @return размер сэкономленного места в байтах
     */
    suspend fun compressLogs(context: Context): Long = withContext(Dispatchers.IO) {
        try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return@withContext 0L

            val originalSize = logFile.length()

            // Удаляем дублирующиеся строки
            val uniqueLines = logFile.readLines().distinct()
            val compressedContent = uniqueLines.joinToString("\n") + "\n"

            // Создаем резервную копию
            createBackup(logFile)

            // Записываем сжатые данные
            logFile.writeText(compressedContent)

            val newSize = logFile.length()
            val savedBytes = originalSize - newSize

            Log.i(
                TAG,
                "Логи сжаты: было ${originalSize}б, стало ${newSize}б, сэкономлено ${savedBytes}б"
            )

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Логи сжаты, сэкономлено: ${formatBytes(savedBytes)}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            savedBytes
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сжатия логов: ${e.message}")
            0L
        }
    }

    /**
     * Проверяет целостность файла логов
     * @param context контекст приложения
     * @return отчет о целостности
     */
    suspend fun validateLogIntegrity(context: Context): LogIntegrityReport =
        withContext(Dispatchers.IO) {
            try {
                val logFile = getLogFile(context)
                if (!logFile.exists()) {
                    return@withContext LogIntegrityReport(
                        isValid = false,
                        totalLines = 0,
                        corruptedLines = 0,
                        errors = listOf("Файл логов не существует")
                    )
                }

                val lines = logFile.readLines()
                val errors = mutableListOf<String>()
                var corruptedLines = 0

                lines.forEachIndexed { index, line ->
                    val timestamp = extractTimestamp(line)
                    if (timestamp.isEmpty()) {
                        errors.add("Строка ${index + 1}: отсутствует timestamp")
                        corruptedLines++
                    } else {
                        try {
                            dateFormat.parse(timestamp)
                        } catch (e: Exception) {
                            errors.add("Строка ${index + 1}: некорректный timestamp '$timestamp'")
                            corruptedLines++
                        }
                    }

                    // Ограничиваем количество ошибок в отчете
                    if (errors.size >= 100) {
                        errors.add("... и еще ошибок (ограничено 100)")
                        return@forEachIndexed
                    }
                }

                LogIntegrityReport(
                    isValid = errors.isEmpty(),
                    totalLines = lines.size,
                    corruptedLines = corruptedLines,
                    errors = errors
                )
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка проверки целостности: ${e.message}")
                LogIntegrityReport(
                    isValid = false,
                    totalLines = 0,
                    corruptedLines = 0,
                    errors = listOf("Ошибка проверки: ${e.message}")
                )
            }
        }

    // Утилитарные методы

    /**
     * Возвращает текущую дату в формате для имени файла
     */
    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * Форматирует размер в байтах в читаемый вид
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}б"
            bytes < 1024 * 1024 -> "${bytes / 1024}КБ"
            else -> String.format("%.1fМБ", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Data class для статистики логов
     */
    data class LogStatistics(
        val totalEntries: Int = 0,
        val fileSizeKB: Long = 0,
        val criticalEvents: Int = 0,
        val temperatureEvents: Int = 0,
        val gpsEvents: Int = 0,
        val batteryEvents: Int = 0,
        val firstEvent: String = "Нет данных",
        val lastEvent: String = "Нет данных",
        val error: String? = null
    ) {
        /**
         * Проверяет, есть ли ошибка в статистике
         */
        fun hasError(): Boolean = error != null

        /**
         * Возвращает человекочитаемый размер файла
         */
        fun getFormattedFileSize(): String {
            return when {
                fileSizeKB < 1024 -> "${fileSizeKB} КБ"
                else -> String.format("%.1f МБ", fileSizeKB / 1024.0)
            }
        }

        /**
         * Возвращает краткую сводку статистики
         */
        fun getSummary(): String {
            return if (hasError()) {
                "Ошибка: $error"
            } else {
                "Всего записей: $totalEntries, размер: ${getFormattedFileSize()}"
            }
        }

        /**
         * Возвращает детальную статистику
         */
        fun getDetailedSummary(): String {
            return if (hasError()) {
                "Ошибка анализа: $error"
            } else {
                buildString {
                    appendLine("📊 Общая статистика логов:")
                    appendLine("• Всего записей: $totalEntries")
                    appendLine("• Размер файла: ${getFormattedFileSize()}")
                    appendLine("• Критические: $criticalEvents")
                    appendLine("• Температурные: $temperatureEvents")
                    appendLine("• GPS события: $gpsEvents")
                    appendLine("• Батарея: $batteryEvents")
                    appendLine("• Первое событие: $firstEvent")
                    appendLine("• Последнее событие: $lastEvent")
                }
            }
        }
    }

    /**
     * Data class для отчета о целостности логов
     */
    data class LogIntegrityReport(
        val isValid: Boolean,
        val totalLines: Int,
        val corruptedLines: Int,
        val errors: List<String>
    ) {
        /**
         * Возвращает процент корректных строк
         */
        fun getValidityPercentage(): Double {
            return if (totalLines > 0) {
                ((totalLines - corruptedLines).toDouble() / totalLines) * 100
            } else 100.0
        }

        /**
         * Возвращает краткий отчет
         */
        fun getSummary(): String {
            return if (isValid) {
                "✅ Файл логов корректен ($totalLines строк)"
            } else {
                "⚠️ Найдено $corruptedLines ошибок из $totalLines строк (${
                    String.format(
                        "%.1f",
                        getValidityPercentage()
                    )
                }% корректны)"
            }
        }
    }
}