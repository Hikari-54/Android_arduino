package com.example.bluetooth_andr11.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Централизованное управление разрешениями Android с полной поддержкой всех версий API.
 *
 * Основные возможности:
 * - Автоматическая адаптация под версию Android (API 23+ для runtime permissions, API 31+ для новых Bluetooth)
 * - Интеллектуальное определение необходимых разрешений в зависимости от версии ОС
 * - Кэширование результатов проверки для оптимизации производительности
 * - Подробная диагностика состояния разрешений с человекочитаемыми названиями
 * - Группированная проверка разрешений по функциональности (GPS, Bluetooth)
 * - Thread-safe операции с синхронизированным кэшем
 * - Автоматическая инвалидация кэша при изменениях
 *
 * Поддерживаемые группы разрешений:
 *
 * Местоположение (обязательные):
 * - ACCESS_FINE_LOCATION: точные координаты GPS
 * - ACCESS_COARSE_LOCATION: приблизительное местоположение по сети (опционально)
 *
 * Bluetooth для Android < 12:
 * - BLUETOOTH: базовый доступ к Bluetooth
 * - BLUETOOTH_ADMIN: административные функции (поиск устройств)
 *
 * Bluetooth для Android 12+:
 * - BLUETOOTH_CONNECT: подключение к устройствам
 * - BLUETOOTH_SCAN: сканирование устройств
 *
 * Архитектурные принципы:
 * - Lazy initialization для оптимизации запуска
 * - Кэширование с автоматическим таймаутом для балансировки производительности и актуальности
 * - Defensive programming с проверкой null значений
 * - Подробное логирование для отладки проблем с разрешениями
 * - Graceful degradation при отсутствии критических разрешений
 */
class PermissionHelper(
    private val context: Context,
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>>?
) {
    companion object {
        private const val TAG = "PermissionHelper"

        // === КОНСТАНТЫ КЭШИРОВАНИЯ ===

        /** Время жизни кэша результатов проверки разрешений */
        private const val CACHE_TIMEOUT_MS = 5000L // 5 секунд

        /** Минимальная версия Android для runtime permissions */
        private const val MIN_RUNTIME_PERMISSIONS_API = Build.VERSION_CODES.M

        /** Версия Android с новыми Bluetooth разрешениями */
        private const val NEW_BLUETOOTH_PERMISSIONS_API = Build.VERSION_CODES.S

        // === ГРУППЫ РАЗРЕШЕНИЙ ===

        /** Разрешения местоположения (ACCESS_FINE_LOCATION обязательно) */
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        /** Bluetooth разрешения для Android < 12 */
        private val BLUETOOTH_PERMISSIONS_LEGACY = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        /** Bluetooth разрешения для Android 12+ */
        @RequiresApi(Build.VERSION_CODES.S)
        private val BLUETOOTH_PERMISSIONS_MODERN = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    }

    // === СОСТОЯНИЕ КЭША ===

    /** Lazy-инициализируемый список всех необходимых разрешений */
    private val requiredPermissions by lazy { buildPermissionList() }

    /** Время последней проверки разрешений */
    @Volatile
    private var lastPermissionCheck = 0L

    /** Кэшированный результат последней проверки */
    @Volatile
    private var cachedPermissionResult: Boolean? = null

    // === ОСНОВНЫЕ МЕТОДЫ ===

    /**
     * Проверяет, предоставлены ли все необходимые разрешения для работы приложения.
     * Использует кэширование для оптимизации частых проверок в UI потоке.
     *
     * @return true если все критические разрешения предоставлены
     */
    fun hasAllPermissions(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Проверяем актуальность кэша
        if (cachedPermissionResult != null &&
            currentTime - lastPermissionCheck < CACHE_TIMEOUT_MS
        ) {
            return cachedPermissionResult!!
        }

        // Выполняем полную проверку всех разрешений
        val allGranted = requiredPermissions.all { permission ->
            hasPermission(permission)
        }

        // Обновляем кэш thread-safe способом
        synchronized(this) {
            cachedPermissionResult = allGranted
            lastPermissionCheck = currentTime
        }

        Log.d(
            TAG,
            "🔍 Проверка разрешений: ${if (allGranted) "✅ все предоставлены" else "❌ отсутствуют"}"
        )
        return allGranted
    }

    /**
     * Проверяет наличие конкретного разрешения
     *
     * @param permission строковое название разрешения из Manifest
     * @return true если разрешение предоставлено
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Запрашивает все недостающие разрешения через ActivityResultLauncher
     * Автоматически определяет какие разрешения отсутствуют и запрашивает только их
     */
    fun requestPermissions() {
        val missingPermissions = getMissingPermissions()

        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "✅ Все разрешения уже предоставлены")
            return
        }

        Log.d(TAG, "📋 Запрашиваем недостающие разрешения: $missingPermissions")

        // Важно: сбрасываем кэш перед запросом для получения актуального результата
        invalidateCache()

        if (requestPermissionLauncher != null) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.e(
                TAG,
                "❌ RequestPermissionLauncher не инициализирован! Невозможно запросить разрешения"
            )
        }
    }

    // === ГРУППОВЫЕ ПРОВЕРКИ ===

    /**
     * Проверяет наличие разрешений на местоположение
     * Достаточно любого из разрешений местоположения для базовой работы
     *
     * @return true если есть хотя бы одно разрешение местоположения
     */
    fun hasLocationPermissions(): Boolean {
        return LOCATION_PERMISSIONS.any { permission ->
            hasPermission(permission)
        }
    }

    /**
     * Проверяет наличие точного разрешения местоположения
     * Необходимо для GPS функций высокой точности
     *
     * @return true если предоставлено разрешение ACCESS_FINE_LOCATION
     */
    fun hasFineLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Проверяет наличие всех необходимых Bluetooth разрешений
     * Автоматически определяет нужную группу разрешений по версии Android
     *
     * @return true если все Bluetooth разрешения предоставлены
     */
    fun hasBluetoothPermissions(): Boolean {
        val bluetoothPermissions = getBluetoothPermissionsForCurrentApi()
        return bluetoothPermissions.all { permission ->
            hasPermission(permission)
        }
    }

    /**
     * Проверяет критически важные разрешения для базовой работы приложения
     * Включает точное местоположение и все Bluetooth разрешения
     *
     * @return true если все критические разрешения предоставлены
     */
    fun hasCriticalPermissions(): Boolean {
        val criticalPermissions = mutableListOf<String>().apply {
            // Обязательно точное местоположение
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            // Все Bluetooth разрешения для текущей версии
            addAll(getBluetoothPermissionsForCurrentApi())
        }

        return criticalPermissions.all { permission ->
            hasPermission(permission)
        }
    }

    // === ИНФОРМАЦИОННЫЕ МЕТОДЫ ===

    /**
     * Возвращает список всех отсутствующих разрешений
     *
     * @return список строковых названий отсутствующих разрешений
     */
    fun getMissingPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            !hasPermission(permission)
        }
    }

    /**
     * Возвращает подробный статус всех разрешений по категориям
     * Полезно для отображения в UI и диагностики
     *
     * @return объект PermissionStatus с детальной информацией
     */
    fun getPermissionStatus(): PermissionStatus {
        return PermissionStatus(
            hasAllPermissions = hasAllPermissions(),
            hasLocationPermissions = hasLocationPermissions(),
            hasFineLocationPermission = hasFineLocationPermission(),
            hasBluetoothPermissions = hasBluetoothPermissions(),
            missingPermissions = getMissingPermissions(),
            requiredPermissions = requiredPermissions,
            androidVersion = Build.VERSION.SDK_INT,
            isModernBluetoothApi = Build.VERSION.SDK_INT >= NEW_BLUETOOTH_PERMISSIONS_API
        )
    }

    /**
     * Возвращает человекочитаемое название разрешения на русском языке
     *
     * @param permission системное название разрешения
     * @return понятное пользователю название
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION -> "Точное местоположение GPS"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Приблизительное местоположение"
            Manifest.permission.BLUETOOTH -> "Bluetooth (legacy API)"
            Manifest.permission.BLUETOOTH_ADMIN -> "Администрирование Bluetooth (legacy)"
            Manifest.permission.BLUETOOTH_CONNECT -> "Подключение к Bluetooth устройствам"
            Manifest.permission.BLUETOOTH_SCAN -> "Сканирование Bluetooth устройств"
            else -> permission.substringAfterLast('.').replace('_', ' ')
        }
    }

    // === УПРАВЛЕНИЕ КЭШЕМ ===

    /**
     * Принудительно сбрасывает кэш разрешений
     * Используется после запроса разрешений или при подозрении на изменения
     */
    fun invalidateCache() {
        synchronized(this) {
            cachedPermissionResult = null
            lastPermissionCheck = 0L
        }
        Log.d(TAG, "🗑️ Кэш разрешений сброшен")
    }

    /**
     * Принудительно обновляет кэш разрешений
     * Выполняет свежую проверку независимо от времени последней проверки
     *
     * @return актуальное состояние разрешений
     */
    fun refreshPermissions(): Boolean {
        invalidateCache()
        return hasAllPermissions()
    }

    // === ДИАГНОСТИКА И ОТЛАДКА ===

    /**
     * Выводит в лог детальную информацию о состоянии всех разрешений
     * Полезно для отладки проблем с разрешениями
     */
    fun logPermissionDetails() {
        Log.d(TAG, "=== 📋 Детальная диагностика разрешений ===")
        Log.d(TAG, "🤖 Android версия: ${Build.VERSION.SDK_INT} (API ${Build.VERSION.SDK_INT})")
        Log.d(TAG, "📦 Всего требуется разрешений: ${requiredPermissions.size}")
        Log.d(
            TAG,
            "🔄 Использую ${if (Build.VERSION.SDK_INT >= NEW_BLUETOOTH_PERMISSIONS_API) "современные" else "legacy"} Bluetooth разрешения"
        )

        requiredPermissions.forEachIndexed { index, permission ->
            val isGranted = hasPermission(permission)
            val status = if (isGranted) "✅ ПРЕДОСТАВЛЕНО" else "❌ ОТСУТСТВУЕТ"
            val displayName = getPermissionDisplayName(permission)
            Log.d(TAG, "${index + 1}. $status - $displayName")
            Log.d(TAG, "   └─ Системное имя: $permission")
        }

        val missingCount = getMissingPermissions().size
        val grantedCount = requiredPermissions.size - missingCount
        val percentage = if (requiredPermissions.isNotEmpty()) {
            (grantedCount * 100) / requiredPermissions.size
        } else 100

        Log.d(
            TAG,
            "📊 Итого: $grantedCount/${requiredPermissions.size} предоставлены ($percentage%)"
        )
        Log.d(
            TAG,
            "🚨 Отсутствует критических: ${if (hasCriticalPermissions()) 0 else missingCount}"
        )
        Log.d(TAG, "===============================================")
    }

    /**
     * Возвращает краткую информацию о состоянии разрешений для логирования
     *
     * @return строка с кратким статусом
     */
    fun getPermissionsSummary(): String {
        val status = getPermissionStatus()
        val granted = status.requiredPermissions.size - status.missingPermissions.size
        val total = status.requiredPermissions.size
        val percentage = if (total > 0) (granted * 100) / total else 100

        return "Разрешения: $granted/$total ($percentage%) | " +
                "GPS: ${if (status.hasLocationPermissions) "✅" else "❌"} | " +
                "BT: ${if (status.hasBluetoothPermissions) "✅" else "❌"}"
    }

    // === ПРИВАТНЫЕ МЕТОДЫ ===

    /**
     * Создает список всех необходимых разрешений в зависимости от версии Android
     * Вызывается только один раз благодаря lazy initialization
     *
     * @return список строковых названий разрешений
     */
    private fun buildPermissionList(): List<String> {
        val permissions = mutableListOf<String>()

        // Местоположение нужно всегда (точное обязательно, приблизительное опционально)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        // Для совместимости добавляем и приблизительное
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Bluetooth разрешения зависят от версии Android
        if (Build.VERSION.SDK_INT >= NEW_BLUETOOTH_PERMISSIONS_API) {
            permissions.addAll(BLUETOOTH_PERMISSIONS_MODERN)
            Log.d(TAG, "🆕 Используются современные Bluetooth разрешения (Android 12+)")
        } else {
            permissions.addAll(BLUETOOTH_PERMISSIONS_LEGACY)
            Log.d(TAG, "📱 Используются legacy Bluetooth разрешения (Android < 12)")
        }

        Log.d(TAG, "📝 Сформирован список разрешений: $permissions")
        return permissions
    }

    /**
     * Возвращает список Bluetooth разрешений для текущей версии API
     *
     * @return массив названий Bluetooth разрешений
     */
    private fun getBluetoothPermissionsForCurrentApi(): Array<String> {
        return if (Build.VERSION.SDK_INT >= NEW_BLUETOOTH_PERMISSIONS_API) {
            BLUETOOTH_PERMISSIONS_MODERN
        } else {
            BLUETOOTH_PERMISSIONS_LEGACY
        }
    }

    // === DATA CLASSES ===

    /**
     * Подробная информация о статусе разрешений для анализа и отображения
     *
     * @param hasAllPermissions предоставлены ли все разрешения
     * @param hasLocationPermissions есть ли разрешения местоположения
     * @param hasFineLocationPermission есть ли точное местоположение
     * @param hasBluetoothPermissions есть ли Bluetooth разрешения
     * @param missingPermissions список отсутствующих разрешений
     * @param requiredPermissions список всех необходимых разрешений
     * @param androidVersion версия Android устройства
     * @param isModernBluetoothApi используется ли современный Bluetooth API
     */
    data class PermissionStatus(
        val hasAllPermissions: Boolean,
        val hasLocationPermissions: Boolean,
        val hasFineLocationPermission: Boolean,
        val hasBluetoothPermissions: Boolean,
        val missingPermissions: List<String>,
        val requiredPermissions: List<String>,
        val androidVersion: Int,
        val isModernBluetoothApi: Boolean
    ) {
        /**
         * Возвращает количество предоставленных разрешений
         */
        fun getGrantedCount(): Int {
            return requiredPermissions.size - missingPermissions.size
        }

        /**
         * Возвращает общее количество требуемых разрешений
         */
        fun getTotalCount(): Int {
            return requiredPermissions.size
        }

        /**
         * Возвращает процент предоставленных разрешений
         */
        fun getGrantedPercentage(): Int {
            return if (requiredPermissions.isNotEmpty()) {
                (getGrantedCount() * 100) / requiredPermissions.size
            } else 100
        }

        /**
         * Проверяет, готово ли приложение к полноценной работе
         * Требует наличия местоположения и Bluetooth разрешений
         */
        fun isAppReadyToWork(): Boolean {
            return hasLocationPermissions && hasBluetoothPermissions
        }

        /**
         * Проверяет, готово ли приложение к работе в минимальном режиме
         * Требует только точное местоположение
         */
        fun isMinimalModeReady(): Boolean {
            return hasFineLocationPermission
        }

        /**
         * Возвращает краткое описание статуса разрешений
         */
        fun getSummary(): String {
            return when {
                hasAllPermissions -> "✅ Все разрешения предоставлены"
                isAppReadyToWork() -> "🟢 Основные разрешения предоставлены"
                isMinimalModeReady() -> "🟡 Доступен минимальный режим (только GPS)"
                hasLocationPermissions -> "🟠 Есть местоположение, нет Bluetooth"
                hasBluetoothPermissions -> "🟠 Есть Bluetooth, нет местоположения"
                else -> "🔴 Отсутствуют критические разрешения"
            }
        }

        /**
         * Возвращает список названий отсутствующих разрешений на русском языке
         */
        fun getMissingPermissionsDisplayNames(): List<String> {
            return missingPermissions.map { permission ->
                when (permission) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> "Точное местоположение"
                    Manifest.permission.ACCESS_COARSE_LOCATION -> "Приблизительное местоположение"
                    Manifest.permission.BLUETOOTH -> "Bluetooth (старый API)"
                    Manifest.permission.BLUETOOTH_ADMIN -> "Администрирование Bluetooth"
                    Manifest.permission.BLUETOOTH_CONNECT -> "Подключение Bluetooth"
                    Manifest.permission.BLUETOOTH_SCAN -> "Сканирование Bluetooth"
                    else -> permission.substringAfterLast('.').replace('_', ' ')
                }
            }
        }

        /**
         * Возвращает детальный отчет о состоянии разрешений
         */
        fun getDetailedReport(): String {
            return buildString {
                appendLine("📱 Отчет о разрешениях:")
                appendLine("• Android версия: $androidVersion")
                appendLine("• API Bluetooth: ${if (isModernBluetoothApi) "современный" else "legacy"}")
                appendLine("• Предоставлено: ${getGrantedCount()}/${getTotalCount()} (${getGrantedPercentage()}%)")
                appendLine("• Местоположение: ${if (hasLocationPermissions) "✅" else "❌"}")
                appendLine("• Точное местоположение: ${if (hasFineLocationPermission) "✅" else "❌"}")
                appendLine("• Bluetooth: ${if (hasBluetoothPermissions) "✅" else "❌"}")
                appendLine("• Статус: ${getSummary()}")

                if (missingPermissions.isNotEmpty()) {
                    appendLine("• Отсутствуют:")
                    getMissingPermissionsDisplayNames().forEach { name ->
                        appendLine("  - $name")
                    }
                }
            }
        }

        /**
         * Проверяет, есть ли критические проблемы с разрешениями
         */
        fun hasCriticalIssues(): Boolean {
            return !hasFineLocationPermission || !hasBluetoothPermissions
        }

        /**
         * Возвращает рекомендации по решению проблем с разрешениями
         */
        fun getRecommendations(): List<String> {
            val recommendations = mutableListOf<String>()

            if (!hasFineLocationPermission) {
                recommendations.add("Предоставьте разрешение на точное местоположение для GPS функций")
            }

            if (!hasBluetoothPermissions) {
                recommendations.add("Предоставьте Bluetooth разрешения для связи с устройством")
            }

            if (missingPermissions.isEmpty() && !hasAllPermissions) {
                recommendations.add("Перезапустите приложение для применения изменений")
            }

            return recommendations
        }
    }
}