package com.example.bluetooth_andr11.auth

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * Менеджер аутентификации доставочных сумок с уникальными идентификаторами.
 *
 * Основные функции:
 * - Обработка ID сообщений от сумок в формате "ID:SB000001"
 * - Валидация корректности идентификаторов сумок
 * - Отправка подтверждений в формате "ID_OK:SB000001"
 * - Сохранение текущего активного ID на время BT сессии
 * - Логирование всех событий аутентификации с GPS привязкой
 * - Автоматический сброс при переподключении для поддержки смены сумок
 * - Обработка ошибок и уведомление пользователя о проблемах
 *
 * Поддерживаемый формат ID: SB000001, SB000002, SB999999 и т.д.
 * - Префикс: "SB" (Smart Bag)
 * - Номер: 6-значное число с ведущими нулями
 *
 * Протокол аутентификации:
 * 1. Сумка отправляет: "ID:SB000001"
 * 2. Приложение проверяет формат ID
 * 3. Если корректно: отправляет "ID_OK:SB000001" и сохраняет ID
 * 4. Если некорректно: показывает ошибку пользователю
 * 5. При отключении BT: сбрасывает сохранённый ID
 *
 * Архитектурные принципы:
 * - Thread-safe операции с синхронизированным доступом к ID
 * - Интеграция с существующими системами логирования и уведомлений
 * - Минимальное влияние на производительность основного потока
 * - Graceful error handling без прерывания работы приложения
 * - Автоматическое управление жизненным циклом аутентификации
 */
class AuthenticationManager(
    private val context: Context,
    private val bluetoothHelper: BluetoothHelper,
    private val locationManager: EnhancedLocationManager
) {
    companion object {
        private const val TAG = "AuthenticationManager"

        /** Префикс команды ID от сумки */
        private const val ID_COMMAND_PREFIX = "ID:"

        /** Префикс ответа подтверждения ID */
        private const val ID_CONFIRMATION_PREFIX = "ID_OK:"

        /** Регулярное выражение для валидации ID сумки */
        private val BAG_ID_PATTERN = Pattern.compile("^SB\\d{6}$")

        /** Минимальная длина ID сообщения */
        private const val MIN_ID_MESSAGE_LENGTH = 11 // "ID:SB000001"

        /** Максимальная длина ID сообщения */
        private const val MAX_ID_MESSAGE_LENGTH = 20
    }

    // === СОСТОЯНИЕ АУТЕНТИФИКАЦИИ ===

    /** Текущий активный ID сумки (null если не аутентифицирована) */
    @Volatile
    private var currentBagId: String? = null

    /** Время последней успешной аутентификации */
    @Volatile
    private var lastAuthenticationTime: Long = 0

    /** Флаг активного состояния аутентификации */
    @Volatile
    private var isAuthenticated: Boolean = false

    /** Счётчик попыток аутентификации для статистики */
    private var authenticationAttempts: Int = 0

    /** Счётчик успешных аутентификаций */
    private var successfulAuthentications: Int = 0

    // === ОСНОВНЫЕ МЕТОДЫ ===

    /**
     * Обрабатывает входящее сообщение аутентификации от сумки.
     *
     * Выполняет полный цикл аутентификации:
     * 1. Валидация формата сообщения
     * 2. Извлечение и проверка ID сумки
     * 3. Отправка подтверждения через Bluetooth
     * 4. Сохранение ID и обновление статуса
     * 5. Логирование события с GPS координатами
     *
     * @param message сообщение от сумки в формате "ID:SB000001"
     * @return true если аутентификация прошла успешно
     */
    fun processAuthenticationMessage(message: String): Boolean {
        authenticationAttempts++

        Log.d(TAG, "🔐 === НАЧАЛО ОБРАБОТКИ АУТЕНТИФИКАЦИИ #$authenticationAttempts ===")
        Log.d(TAG, "🔐 Сообщение: '$message' (длина: ${message.length})")

        try {
            // === ШАГ 1: Валидация базового формата ===
            Log.d(TAG, "🔐 Шаг 1: Валидация формата сообщения")
            if (!isValidAuthenticationMessage(message)) {
                Log.e(TAG, "❌ Шаг 1 ПРОВАЛЕН: Некорректный формат")
                handleAuthenticationError(message, "Некорректный формат сообщения аутентификации")
                return false
            }
            Log.d(TAG, "✅ Шаг 1 ПРОЙДЕН: Формат сообщения корректен")

            // === ШАГ 2: Извлечение ID ===
            Log.d(TAG, "🔐 Шаг 2: Извлечение ID из сообщения")
            val bagId = extractBagIdFromMessage(message)
            if (bagId == null) {
                Log.e(TAG, "❌ Шаг 2 ПРОВАЛЕН: Не удалось извлечь ID")
                handleAuthenticationError(message, "Не удалось извлечь ID из сообщения")
                return false
            }
            Log.d(TAG, "✅ Шаг 2 ПРОЙДЕН: ID извлечен = '$bagId'")

            // === ШАГ 3: Валидация формата ID ===
            Log.d(TAG, "🔐 Шаг 3: Валидация формата ID")
            if (!isValidBagId(bagId)) {
                Log.e(TAG, "❌ Шаг 3 ПРОВАЛЕН: ID '$bagId' не соответствует формату")
                handleAuthenticationError(
                    message,
                    "ID сумки '$bagId' не соответствует требуемому формату"
                )
                return false
            }
            Log.d(TAG, "✅ Шаг 3 ПРОЙДЕН: ID '$bagId' соответствует формату")

            // === ШАГ 4: Выполнение аутентификации ===
            Log.d(TAG, "🔐 Шаг 4: Выполнение аутентификации")
            val result = performAuthentication(bagId)

            if (result) {
                Log.i(TAG, "🔐 === АУТЕНТИФИКАЦИЯ УСПЕШНА ===")
            } else {
                Log.e(TAG, "🔐 === АУТЕНТИФИКАЦИЯ ПРОВАЛЕНА ===")
            }

            return result

        } catch (e: Exception) {
            Log.e(TAG, "💥 === КРИТИЧЕСКАЯ ОШИБКА АУТЕНТИФИКАЦИИ ===")
            Log.e(TAG, "💥 Message: '$message'")
            Log.e(TAG, "💥 Exception: ${e.message}")
            Log.e(TAG, "💥 Stack trace: ${e.stackTraceToString()}")
            handleAuthenticationError(message, "Критическая ошибка аутентификации: ${e.message}")
            return false
        }
    }

    /**
     * Выполняет основную логику аутентификации сумки.
     *
     * @param bagId валидный ID сумки
     * @return true если аутентификация успешна
     */
    private fun performAuthentication(bagId: String): Boolean {
        return try {
            // Отправляем подтверждение на сумку
            val confirmationMessage = "$ID_CONFIRMATION_PREFIX$bagId"
            bluetoothHelper.sendCommand(confirmationMessage)

            // Обновляем состояние аутентификации
            synchronized(this) {
                currentBagId = bagId
                isAuthenticated = true
                lastAuthenticationTime = System.currentTimeMillis()
                successfulAuthentications++
            }

            // Логируем успешную аутентификацию
            logAuthenticationEvent(bagId, "Успешная аутентификация", true)

            Log.i(TAG, "✅ Аутентификация успешна: ID='$bagId', подтверждение отправлено")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка выполнения аутентификации для '$bagId': ${e.message}")
            false
        }
    }

    /**
     * Сбрасывает состояние аутентификации при отключении Bluetooth.
     * Вызывается автоматически при разрыве BT соединения.
     */
    fun resetAuthentication() {
        val previousBagId = currentBagId

        synchronized(this) {
            currentBagId = null
            isAuthenticated = false
            lastAuthenticationTime = 0
        }

        if (previousBagId != null) {
            logAuthenticationEvent(
                previousBagId,
                "Сброс аутентификации при отключении BT",
                false
            )
            Log.i(TAG, "🔄 Аутентификация сброшена для ID='$previousBagId'")
        } else {
            Log.d(TAG, "🔄 Сброс аутентификации (ID не был установлен)")
        }
    }

    // === ВАЛИДАЦИЯ ===

    /**
     * Проверяет базовый формат сообщения аутентификации.
     */
    private fun isValidAuthenticationMessage(message: String): Boolean {
        Log.d(TAG, "🔍 Проверка формата аутентификационного сообщения:")

        val isNotBlank = message.isNotBlank()
        Log.d(TAG, "   • Не пустое: $isNotBlank")

        val startsWithPrefix = message.startsWith(ID_COMMAND_PREFIX)
        Log.d(TAG, "   • Начинается с '$ID_COMMAND_PREFIX': $startsWithPrefix")

        val lengthOk =
            message.length >= MIN_ID_MESSAGE_LENGTH && message.length <= MAX_ID_MESSAGE_LENGTH
        Log.d(
            TAG,
            "   • Длина (${message.length}) в диапазоне [$MIN_ID_MESSAGE_LENGTH-$MAX_ID_MESSAGE_LENGTH]: $lengthOk"
        )

        val result = isNotBlank && startsWithPrefix && lengthOk
        Log.d(TAG, "   • ИТОГОВЫЙ РЕЗУЛЬТАТ: $result")

        return result
    }

    /**
     * Извлекает ID сумки из сообщения аутентификации.
     */
    private fun extractBagIdFromMessage(message: String): String? {
        Log.d(TAG, "🔗 Извлечение ID из сообщения: '$message'")

        return try {
            if (message.startsWith(ID_COMMAND_PREFIX)) {
                val extracted = message.substring(ID_COMMAND_PREFIX.length).trim()
                Log.d(TAG, "✅ ID успешно извлечен: '$extracted'")
                extracted
            } else {
                Log.e(TAG, "❌ Сообщение не начинается с префикса '$ID_COMMAND_PREFIX'")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Ошибка извлечения ID: ${e.message}")
            Log.e(TAG, "💥 Сообщение: '$message'")
            null
        }
    }

    /**
     * Проверяет корректность формата ID сумки.
     * Ожидаемый формат: SB000001, SB000002, SB999999 и т.д.
     */
    private fun isValidBagId(bagId: String): Boolean {
        Log.d(TAG, "🏷️ Проверка ID сумки: '$bagId'")

        val matchesPattern = BAG_ID_PATTERN.matcher(bagId).matches()
        Log.d(TAG, "   • Соответствует паттерну '^SB\\d{6}$': $matchesPattern")
        Log.d(TAG, "   • Длина: ${bagId.length}")
        Log.d(TAG, "   • Начинается с 'SB': ${bagId.startsWith("SB")}")

        if (bagId.length >= 3) {
            val numberPart = bagId.substring(2)
            Log.d(TAG, "   • Числовая часть: '$numberPart'")
            Log.d(TAG, "   • Длина числовой части: ${numberPart.length}")
            Log.d(TAG, "   • Числовая часть - число: ${numberPart.all { it.isDigit() }}")
        }

        return matchesPattern
    }

    // === ОБРАБОТКА ОШИБОК ===

    /**
     * Обрабатывает ошибки аутентификации с уведомлением пользователя.
     */
    private fun handleAuthenticationError(message: String, errorDescription: String) {
        Log.w(TAG, "⚠️ Ошибка аутентификации: $errorDescription (сообщение: '$message')")

        // Показываем пользователю понятное сообщение об ошибке
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(
                context,
                "ID сумки некорректный, свяжитесь со службой поддержки умных сумок",
                Toast.LENGTH_LONG
            ).show()
        }

        // Логируем техническую информацию об ошибке
        logAuthenticationEvent(
            message,
            "Ошибка аутентификации: $errorDescription",
            false
        )
    }

    // === ЛОГИРОВАНИЕ ===

    /**
     * Логирует события аутентификации с GPS привязкой.
     */
    private fun logAuthenticationEvent(bagId: String, event: String, isSuccess: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val logMessage = if (isSuccess) {
                    "🔐 Аутентификация: $event (ID: $bagId)"
                } else {
                    "⚠️ Ошибка аутентификации: $event (Данные: $bagId)"
                }

                LogModule.logEventWithLocation(
                    context,
                    bluetoothHelper,
                    locationManager,
                    logMessage
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка логирования события аутентификации: ${e.message}")
            }
        }
    }

    // === ГЕТТЕРЫ И СТАТУС ===

    /**
     * Возвращает текущий активный ID сумки или null если не аутентифицирована.
     */
    fun getCurrentBagId(): String? {
        return synchronized(this) { currentBagId }
    }

    /**
     * Проверяет, аутентифицирована ли текущая сумка.
     */
    fun isCurrentlyAuthenticated(): Boolean {
        return synchronized(this) { isAuthenticated }
    }

    /**
     * Возвращает время последней успешной аутентификации.
     */
    fun getLastAuthenticationTime(): Long {
        return synchronized(this) { lastAuthenticationTime }
    }

    /**
     * Возвращает статистику аутентификации для отладки.
     */
    fun getAuthenticationStatistics(): AuthenticationStatistics {
        return synchronized(this) {
            AuthenticationStatistics(
                totalAttempts = authenticationAttempts,
                successfulAuthentications = successfulAuthentications,
                currentBagId = currentBagId,
                isAuthenticated = isAuthenticated,
                lastAuthenticationTime = lastAuthenticationTime
            )
        }
    }

    /**
     * Возвращает подробный отчёт о состоянии аутентификации.
     */
    fun getStatusReport(): String {
        val stats = getAuthenticationStatistics()
        return buildString {
            appendLine("=== AUTHENTICATION STATUS ===")
            appendLine("Текущий статус: ${if (stats.isAuthenticated) "Аутентифицирована" else "Не аутентифицирована"}")
            appendLine("Активный ID: ${stats.currentBagId ?: "Отсутствует"}")
            appendLine("Всего попыток: ${stats.totalAttempts}")
            appendLine("Успешных: ${stats.successfulAuthentications}")
            if (stats.lastAuthenticationTime > 0) {
                appendLine(
                    "Последняя аутентификация: ${
                        java.text.SimpleDateFormat(
                            "HH:mm:ss",
                            java.util.Locale.getDefault()
                        ).format(stats.lastAuthenticationTime)
                    }"
                )
            }
            appendLine("===============================")
        }
    }

    // === DATA CLASSES ===

    /**
     * Статистика аутентификации для мониторинга и отладки.
     */
    data class AuthenticationStatistics(
        val totalAttempts: Int,
        val successfulAuthentications: Int,
        val currentBagId: String?,
        val isAuthenticated: Boolean,
        val lastAuthenticationTime: Long
    )
}