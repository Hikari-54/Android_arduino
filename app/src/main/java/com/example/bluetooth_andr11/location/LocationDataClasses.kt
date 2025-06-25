package com.example.bluetooth_andr11.location

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Режимы работы GPS с различными приоритетами точности и энергопотребления.
 *
 * Каждый режим оптимизирован для конкретных сценариев использования:
 * - HIGH_ACCURACY: Максимальная точность для критически важных операций
 * - BALANCED: Оптимальное соотношение точность/энергопотребление
 * - LOW_POWER: Экономия батареи для фоновых операций
 * - GPS_ONLY: Только спутниковая навигация для максимальной точности
 * - NETWORK_ONLY: Только сетевое позиционирование для быстрого получения координат
 * - PASSIVE: Пассивное получение от других приложений без собственных запросов
 */
enum class LocationMode(
    val displayName: String,
    val description: String,
    val powerConsumption: PowerLevel,
    val expectedAccuracy: AccuracyLevel,
    val updateInterval: Long,
    val icon: String
) {
    HIGH_ACCURACY(
        displayName = "Высокая точность",
        description = "GPS + Network, максимальная точность, повышенное энергопотребление",
        powerConsumption = PowerLevel.HIGH,
        expectedAccuracy = AccuracyLevel.EXCELLENT,
        updateInterval = 10000L,
        icon = "🎯"
    ),

    BALANCED(
        displayName = "Сбалансированный",
        description = "Оптимальное соотношение точности и энергопотребления",
        powerConsumption = PowerLevel.MEDIUM,
        expectedAccuracy = AccuracyLevel.GOOD,
        updateInterval = 30000L,
        icon = "⚖️"
    ),

    LOW_POWER(
        displayName = "Экономия энергии",
        description = "Минимальное энергопотребление, сниженная точность",
        powerConsumption = PowerLevel.LOW,
        expectedAccuracy = AccuracyLevel.FAIR,
        updateInterval = 60000L,
        icon = "🔋"
    ),

    PASSIVE(
        displayName = "Пассивный",
        description = "Получение данных от других приложений без собственных запросов",
        powerConsumption = PowerLevel.MINIMAL,
        expectedAccuracy = AccuracyLevel.VARIABLE,
        updateInterval = 300000L,
        icon = "⏸️"
    ),

    GPS_ONLY(
        displayName = "Только GPS",
        description = "Исключительно спутниковая навигация для максимальной точности",
        powerConsumption = PowerLevel.HIGH,
        expectedAccuracy = AccuracyLevel.EXCELLENT,
        updateInterval = 15000L,
        icon = "🛰️"
    ),

    NETWORK_ONLY(
        displayName = "Только сеть",
        description = "Сетевое позиционирование для быстрого получения координат",
        powerConsumption = PowerLevel.LOW,
        expectedAccuracy = AccuracyLevel.FAIR,
        updateInterval = 20000L,
        icon = "📶"
    );

    /**
     * Возвращает полное описание режима с иконкой
     */
    fun getFullDescription(): String {
        return "$icon $displayName - $description"
    }

    /**
     * Возвращает краткую информацию о производительности
     */
    fun getPerformanceInfo(): String {
        return "Энергопотребление: ${powerConsumption.displayName}, " +
                "Точность: ${expectedAccuracy.displayName}, " +
                "Интервал: ${updateInterval / 1000}с"
    }

    /**
     * Проверяет, подходит ли режим для критических операций
     */
    fun isCriticalOperationSuitable(): Boolean {
        return expectedAccuracy in listOf(AccuracyLevel.EXCELLENT, AccuracyLevel.GOOD)
    }

    /**
     * Проверяет, энергоэффективен ли режим
     */
    fun isEnergyEfficient(): Boolean {
        return powerConsumption in listOf(PowerLevel.LOW, PowerLevel.MINIMAL)
    }
}

/**
 * Уровни энергопотребления для различных режимов GPS
 */
enum class PowerLevel(val displayName: String, val value: Int) {
    MINIMAL("Минимальное", 1),
    LOW("Низкое", 2),
    MEDIUM("Среднее", 3),
    HIGH("Высокое", 4);

    fun getIcon(): String {
        return when (this) {
            MINIMAL -> "🟢"
            LOW -> "🟡"
            MEDIUM -> "🟠"
            HIGH -> "🔴"
        }
    }
}

/**
 * Уровни ожидаемой точности для различных режимов GPS
 */
enum class AccuracyLevel(val displayName: String, val typicalAccuracy: Float) {
    EXCELLENT("Отличная", 5f),
    GOOD("Хорошая", 20f),
    FAIR("Удовлетворительная", 100f),
    POOR("Низкая", 500f),
    VARIABLE("Переменная", Float.MAX_VALUE);

    fun getIcon(): String {
        return when (this) {
            EXCELLENT -> "🎯"
            GOOD -> "✅"
            FAIR -> "🟡"
            POOR -> "⚠️"
            VARIABLE -> "❓"
        }
    }
}

/**
 * Comprehensive data class для информации о местоположении с расширенной функциональностью.
 *
 * Содержит полную информацию о координатах, точности, источнике данных и временных метках
 * с методами для анализа качества и актуальности данных.
 *
 * @param coordinates строка с координатами в формате "latitude, longitude"
 * @param accuracy точность измерения в метрах
 * @param source источник получения данных (GPS, Network, Cache и т.д.)
 * @param timestamp время получения данных в миллисекундах
 * @param isFromCache флаг указывающий на использование кэшированных данных
 * @param provider провайдер местоположения (если доступен)
 * @param bearing направление движения в градусах (если доступно)
 * @param speed скорость движения в м/с (если доступна)
 */
data class LocationInfo(
    val coordinates: String,
    val accuracy: Float,
    val source: String,
    val timestamp: Long,
    val isFromCache: Boolean = false,
    val provider: String? = null,
    val bearing: Float? = null,
    val speed: Float? = null
) {
    companion object {
        /** Максимальный допустимый возраст данных местоположения */
        private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1000L // 5 минут

        /** Формат даты для отображения времени */
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        /** Формат полной даты */
        private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        /**
         * Создает объект LocationInfo из Android Location
         */
        fun fromLocation(
            location: Location,
            source: String,
            isFromCache: Boolean = false
        ): LocationInfo {
            return LocationInfo(
                coordinates = String.format(
                    Locale.US,
                    "%.6f, %.6f",
                    location.latitude,
                    location.longitude
                ),
                accuracy = location.accuracy,
                source = source,
                timestamp = System.currentTimeMillis(),
                isFromCache = isFromCache,
                provider = location.provider,
                bearing = if (location.hasBearing()) location.bearing else null,
                speed = if (location.hasSpeed()) location.speed else null
            )
        }

        /**
         * Создает пустой объект LocationInfo для случаев отсутствия данных
         */
        fun empty(): LocationInfo {
            return LocationInfo(
                coordinates = "Неизвестно",
                accuracy = 0f,
                source = "Недоступно",
                timestamp = 0L,
                isFromCache = false
            )
        }
    }

    /**
     * Проверяет актуальность данных местоположения
     *
     * @param maxAgeMs максимальный допустимый возраст в миллисекундах
     * @return true если данные свежие
     */
    fun isFresh(maxAgeMs: Long = MAX_LOCATION_AGE_MS): Boolean {
        return System.currentTimeMillis() - timestamp < maxAgeMs
    }

    /**
     * Возвращает возраст данных в секундах
     */
    fun getAgeSeconds(): Long {
        return (System.currentTimeMillis() - timestamp) / 1000
    }

    /**
     * Возвращает возраст данных в минутах
     */
    fun getAgeMinutes(): Long {
        return getAgeSeconds() / 60
    }

    /**
     * Проверяет доступность координат
     */
    fun hasCoordinates(): Boolean {
        return coordinates != "Неизвестно" && coordinates.isNotBlank()
    }

    /**
     * Проверяет доступность информации о движении
     */
    fun hasMovementInfo(): Boolean {
        return bearing != null || speed != null
    }

    /**
     * Возвращает уровень качества данных местоположения
     */
    fun getQualityLevel(): LocationQuality {
        return when {
            !hasCoordinates() -> LocationQuality.NO_DATA
            !isFresh() -> LocationQuality.STALE
            accuracy <= 5f -> LocationQuality.EXCELLENT
            accuracy <= 20f -> LocationQuality.GOOD
            accuracy <= 100f -> LocationQuality.FAIR
            accuracy <= 500f -> LocationQuality.POOR
            else -> LocationQuality.VERY_POOR
        }
    }

    /**
     * Возвращает краткое форматированное описание
     */
    fun getShortDescription(): String {
        if (!hasCoordinates()) return "Местоположение недоступно"

        val ageText = when {
            getAgeSeconds() < 30 -> "сейчас"
            getAgeSeconds() < 60 -> "${getAgeSeconds()}с назад"
            getAgeMinutes() < 60 -> "${getAgeMinutes()}м назад"
            else -> "более часа назад"
        }

        return "±${accuracy.toInt()}м, $ageText"
    }

    /**
     * Возвращает полную форматированную информацию
     */
    fun getFormattedInfo(): String {
        if (!hasCoordinates()) return "Местоположение недоступно"

        val ageText = if (getAgeSeconds() > 0) " (${getAgeSeconds()}с назад)" else ""
        val cacheText = if (isFromCache) " [кэш]" else ""
        val providerText = provider?.let { " [$it]" } ?: ""

        return "$coordinates ±${accuracy.toInt()}м $source$cacheText$providerText$ageText"
    }

    /**
     * Возвращает детальную информацию включая движение
     */
    fun getDetailedInfo(): String {
        val basic = getFormattedInfo()

        val movementInfo = buildString {
            bearing?.let { append(" | Направление: ${it.toInt()}°") }
            speed?.let { append(" | Скорость: ${"%.1f".format(it * 3.6)}км/ч") }
        }

        return basic + movementInfo
    }

    /**
     * Возвращает время получения данных в читаемом формате
     */
    fun getFormattedTime(): String {
        return if (timestamp > 0) {
            timeFormat.format(Date(timestamp))
        } else {
            "Неизвестно"
        }
    }

    /**
     * Возвращает полную дату и время получения данных
     */
    fun getFormattedDateTime(): String {
        return if (timestamp > 0) {
            fullDateFormat.format(Date(timestamp))
        } else {
            "Данные недоступны"
        }
    }

    /**
     * Проверяет пригодность данных для навигации
     */
    fun isSuitableForNavigation(): Boolean {
        return hasCoordinates() && isFresh() && accuracy <= 100f
    }

    /**
     * Проверяет пригодность данных для точного позиционирования
     */
    fun isSuitableForPrecisionTasks(): Boolean {
        return hasCoordinates() && isFresh(60000L) && accuracy <= 20f
    }

    /**
     * Возвращает рекомендации по улучшению качества данных
     */
    fun getImprovementSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()

        if (!hasCoordinates()) {
            suggestions.add("Проверьте разрешения на доступ к местоположению")
            suggestions.add("Включите службы местоположения в настройках")
        } else {
            if (!isFresh()) {
                suggestions.add("Обновите данные местоположения")
            }

            if (accuracy > 100f) {
                suggestions.add("Переместитесь в место с лучшим приемом GPS")
                suggestions.add("Убедитесь, что вы не находитесь в помещении")
            }

            if (isFromCache) {
                suggestions.add("Дождитесь получения свежих данных от GPS")
            }
        }

        return suggestions
    }

    /**
     * Возвращает статистическую сводку данных
     */
    fun getStatsSummary(): String {
        val quality = getQualityLevel()
        val freshness = if (isFresh()) "Свежие" else "Устарели"
        val cacheStatus = if (isFromCache) "Кэш" else "Актуальные"

        return "${quality.displayName} | $freshness | $cacheStatus"
    }
}

/**
 * Уровни качества данных местоположения с детальной характеристикой
 */
enum class LocationQuality(
    val displayName: String,
    val description: String,
    val icon: String,
    val color: String
) {
    NO_DATA("Нет данных", "Местоположение недоступно", "❌", "🔴"),
    STALE("Устарели", "Данные требуют обновления", "⏰", "🟡"),
    VERY_POOR("Очень низкое", "Точность хуже 500м", "❗", "🔴"),
    POOR("Низкое", "Точность 100-500м", "⚠️", "🟠"),
    FAIR("Удовлетворительное", "Точность 20-100м", "🟡", "🟡"),
    GOOD("Хорошее", "Точность 5-20м", "✅", "🟢"),
    EXCELLENT("Отличное", "Точность до 5м", "🎯", "💚");

    /**
     * Возвращает полное описание с иконкой
     */
    fun getFullDescription(): String {
        return "$icon $displayName - $description"
    }

    /**
     * Проверяет пригодность для критических операций
     */
    fun isSuitableForCriticalOperations(): Boolean {
        return this in listOf(GOOD, EXCELLENT)
    }

    /**
     * Проверяет пригодность для обычных операций
     */
    fun isSuitableForGeneralUse(): Boolean {
        return this in listOf(FAIR, GOOD, EXCELLENT)
    }
}

/**
 * Comprehensive data class для подробного статуса системы местоположения.
 *
 * Предоставляет полную диагностическую информацию о состоянии всех компонентов
 * системы местоположения включая разрешения, провайдеры, режимы работы и качество данных.
 *
 * @param hasPermission наличие разрешений на доступ к местоположению
 * @param isGpsEnabled состояние GPS провайдера
 * @param isNetworkEnabled состояние Network провайдера
 * @param isLocationAvailable общая доступность местоположения
 * @param currentMode текущий режим работы GPS
 * @param lastUpdate информация о последнем обновлении
 * @param isUpdating активность процесса обновления
 * @param cachedLocation кэшированное местоположение (если есть)
 * @param batteryOptimized оптимизирован ли режим для экономии батареи
 * @param signalQuality качество GPS сигнала
 */
data class LocationStatus(
    val hasPermission: Boolean,
    val isGpsEnabled: Boolean,
    val isNetworkEnabled: Boolean,
    val isLocationAvailable: Boolean,
    val currentMode: LocationMode,
    val lastUpdate: LocationInfo,
    val isUpdating: Boolean,
    val cachedLocation: Location?,
    val batteryOptimized: Boolean = false,
    val signalQuality: SignalQuality = SignalQuality.UNKNOWN
) {
    /**
     * Проверяет готовность системы к работе
     */
    fun isSystemReady(): Boolean {
        return hasPermission && (isGpsEnabled || isNetworkEnabled)
    }

    /**
     * Проверяет полную готовность системы (включая актуальные данные)
     */
    fun isFullyOperational(): Boolean {
        return isSystemReady() && lastUpdate.hasCoordinates() && lastUpdate.isFresh()
    }

    /**
     * Возвращает приоритетные рекомендуемые действия
     */
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        if (!hasPermission) {
            recommendations.add("🔐 Предоставьте разрешения на доступ к местоположению")
        }

        if (!isGpsEnabled && !isNetworkEnabled) {
            recommendations.add("🛰️ Включите службы местоположения в настройках устройства")
        }

        if (!isLocationAvailable && hasPermission) {
            recommendations.add("📡 Проверьте доступность GPS сигнала или подключения к сети")
        }

        if (isSystemReady() && !isUpdating) {
            recommendations.add("▶️ Запустите обновления местоположения")
        }

        if (!lastUpdate.isFresh() && isUpdating) {
            recommendations.add("⏱️ Дождитесь получения свежих данных")
        }

        if (lastUpdate.accuracy > 100f) {
            recommendations.add("🎯 Переместитесь в место с лучшим GPS приемом")
        }

        return recommendations
    }

    /**
     * Возвращает текстовое описание состояния системы
     */
    fun getStatusDescription(): String {
        return when {
            !hasPermission -> "🔴 Нет разрешений на местоположение"
            !isGpsEnabled && !isNetworkEnabled -> "🔴 Службы местоположения отключены"
            !isLocationAvailable -> "🟡 Местоположение временно недоступно"
            !isUpdating -> "🟡 Обновления остановлены"
            !lastUpdate.hasCoordinates() -> "🟡 Ожидание первых данных"
            !lastUpdate.isFresh() -> "🟠 Данные требуют обновления"
            lastUpdate.accuracy > 100f -> "🟡 Низкая точность GPS"
            else -> "🟢 Система работает нормально"
        }
    }

    /**
     * Возвращает краткую сводку состояния
     */
    fun getSummary(): String {
        val updateStatus = if (isUpdating) "активны" else "остановлены"
        val modeText = currentMode.displayName

        return "${getStatusDescription()} | Режим: $modeText | Обновления: $updateStatus"
    }

    /**
     * Возвращает детальный отчет о состоянии системы
     */
    fun getDetailedReport(): String {
        return buildString {
            appendLine("📍 СТАТУС СИСТЕМЫ МЕСТОПОЛОЖЕНИЯ")
            appendLine("════════════════════════════════════")
            appendLine("• Общее состояние: ${getStatusDescription()}")
            appendLine("• Разрешения: ${if (hasPermission) "✅ Предоставлены" else "❌ Отсутствуют"}")
            appendLine("• GPS провайдер: ${if (isGpsEnabled) "✅ Включен" else "❌ Отключен"}")
            appendLine("• Network провайдер: ${if (isNetworkEnabled) "✅ Включен" else "❌ Отключен"}")
            appendLine("• Доступность: ${if (isLocationAvailable) "✅ Доступно" else "❌ Недоступно"}")
            appendLine("• Текущий режим: ${currentMode.getFullDescription()}")
            appendLine("• Обновления: ${if (isUpdating) "✅ Активны" else "⏹️ Остановлены"}")
            appendLine("• Качество сигнала: ${signalQuality.getFullDescription()}")
            appendLine("• Последние данные: ${lastUpdate.getFormattedInfo()}")
            appendLine("• Кэш: ${if (cachedLocation != null) "✅ Доступен" else "❌ Пуст"}")
            appendLine("• Оптимизация батареи: ${if (batteryOptimized) "✅ Включена" else "❌ Отключена"}")
            appendLine("════════════════════════════════════")
        }
    }

    /**
     * Проверяет критические проблемы системы
     */
    fun hasCriticalIssues(): Boolean {
        return !hasPermission || (!isGpsEnabled && !isNetworkEnabled)
    }

    /**
     * Проверяет проблемы с производительностью
     */
    fun hasPerformanceIssues(): Boolean {
        return !lastUpdate.isFresh() || lastUpdate.accuracy > 200f || signalQuality == SignalQuality.POOR
    }

    /**
     * Возвращает рейтинг качества системы (0-100)
     */
    fun getQualityScore(): Int {
        var score = 0

        if (hasPermission) score += 25
        if (isGpsEnabled) score += 20
        if (isNetworkEnabled) score += 10
        if (isLocationAvailable) score += 15
        if (isUpdating) score += 10
        if (lastUpdate.hasCoordinates()) score += 10
        if (lastUpdate.isFresh()) score += 5
        if (lastUpdate.accuracy <= 50f) score += 5

        return score.coerceIn(0, 100)
    }

    /**
     * Возвращает приоритет проблем для решения
     */
    fun getIssuePriority(): IssuePriority {
        return when {
            !hasPermission -> IssuePriority.CRITICAL
            !isGpsEnabled && !isNetworkEnabled -> IssuePriority.HIGH
            !isLocationAvailable -> IssuePriority.MEDIUM
            !lastUpdate.isFresh() -> IssuePriority.LOW
            else -> IssuePriority.NONE
        }
    }
}

/**
 * Приоритеты проблем системы местоположения
 */
enum class IssuePriority(val displayName: String, val icon: String) {
    NONE("Проблем нет", "✅"),
    LOW("Низкий", "🟡"),
    MEDIUM("Средний", "🟠"),
    HIGH("Высокий", "🔴"),
    CRITICAL("Критический", "🚨");

    fun getDescription(): String {
        return "$icon $displayName приоритет"
    }
}

/**
 * Comprehensive data class для статистики работы менеджера местоположения.
 *
 * Содержит подробные метрики производительности, качества и эффективности работы
 * системы местоположения для мониторинга и оптимизации.
 *
 * @param totalActiveRequests количество активных запросов местоположения
 * @param isMonitoringActive активность мониторинга изменений GPS
 * @param lastUpdateAge возраст последнего обновления в секундах (-1 если нет)
 * @param currentAccuracy текущая точность в метрах
 * @param hasValidCache наличие валидного кэша
 * @param currentMode текущий режим работы
 * @param systemReady готовность системы к работе
 * @param totalUpdatesReceived общее количество полученных обновлений
 * @param averageAccuracy средняя точность за сессию
 * @param batteryUsageLevel уровень использования батареи
 */
data class LocationStatistics(
    val totalActiveRequests: Int,
    val isMonitoringActive: Boolean,
    val lastUpdateAge: Long, // в секундах, -1 если обновлений не было
    val currentAccuracy: Float,
    val hasValidCache: Boolean,
    val currentMode: LocationMode,
    val systemReady: Boolean,
    val totalUpdatesReceived: Int = 0,
    val averageAccuracy: Float = 0f,
    val batteryUsageLevel: PowerLevel = PowerLevel.MEDIUM
) {
    /**
     * Возвращает статус системы в текстовом виде с иконкой
     */
    fun getStatusText(): String {
        return when {
            !systemReady -> "🔴 Система не готова к работе"
            lastUpdateAge < 0 -> "🟡 Нет данных о местоположении"
            lastUpdateAge > 300 -> "🟠 Данные устарели (${lastUpdateAge}с назад)"
            currentAccuracy > 100 -> "🟡 Низкая точность (±${currentAccuracy.toInt()}м)"
            totalActiveRequests > 5 -> "⚠️ Высокая нагрузка (${totalActiveRequests} запросов)"
            else -> "🟢 Система работает оптимально"
        }
    }

    /**
     * Возвращает краткую сводку с ключевыми метриками
     */
    fun getSummary(): String {
        val ageText = if (lastUpdateAge >= 0) "${lastUpdateAge}с назад" else "нет данных"
        val requestsText =
            if (totalActiveRequests > 0) "${totalActiveRequests} запросов" else "нет запросов"

        return "${getStatusText()} | Точность: ±${currentAccuracy.toInt()}м | " +
                "Обновлено: $ageText | $requestsText"
    }

    /**
     * Возвращает детальную информацию о статистике
     */
    fun getDetailedInfo(): String {
        return buildString {
            appendLine("📊 СТАТИСТИКА МЕСТОПОЛОЖЕНИЯ")
            appendLine("═══════════════════════════════")
            appendLine("• Статус: ${getStatusText()}")
            appendLine("• Режим работы: ${currentMode.getFullDescription()}")
            appendLine("• Активных запросов: $totalActiveRequests")
            appendLine("• Мониторинг GPS: ${if (isMonitoringActive) "✅ Активен" else "❌ Неактивен"}")
            appendLine("• Последнее обновление: ${formatLastUpdate()}")
            appendLine("• Текущая точность: ±${currentAccuracy.toInt()}м")
            appendLine("• Средняя точность: ±${averageAccuracy.toInt()}м")
            appendLine("• Всего обновлений: $totalUpdatesReceived")
            appendLine("• Кэш актуален: ${if (hasValidCache) "✅ Да" else "❌ Нет"}")
            appendLine("• Система готова: ${if (systemReady) "✅ Да" else "❌ Нет"}")
            appendLine("• Энергопотребление: ${batteryUsageLevel.getIcon()} ${batteryUsageLevel.displayName}")
            appendLine("═══════════════════════════════")
        }
    }

    /**
     * Форматирует информацию о последнем обновлении
     */
    private fun formatLastUpdate(): String {
        return when {
            lastUpdateAge < 0 -> "Нет данных"
            lastUpdateAge < 60 -> "${lastUpdateAge} секунд назад"
            lastUpdateAge < 3600 -> "${lastUpdateAge / 60} минут назад"
            else -> "Более ${lastUpdateAge / 3600} часов назад"
        }
    }

    /**
     * Проверяет наличие проблем с производительностью
     */
    fun hasPerformanceIssues(): Boolean {
        return !systemReady || lastUpdateAge > 300 || currentAccuracy > 200 || totalActiveRequests > 10
    }

    /**
     * Возвращает уровень производительности системы
     */
    fun getPerformanceLevel(): PerformanceLevel {
        return when {
            !systemReady -> PerformanceLevel.CRITICAL
            lastUpdateAge > 600 -> PerformanceLevel.POOR
            currentAccuracy > 200 -> PerformanceLevel.POOR
            totalActiveRequests > 10 -> PerformanceLevel.POOR
            lastUpdateAge > 120 -> PerformanceLevel.FAIR
            currentAccuracy > 50 -> PerformanceLevel.FAIR
            totalActiveRequests > 5 -> PerformanceLevel.FAIR
            lastUpdateAge < 30 && currentAccuracy <= 20 -> PerformanceLevel.EXCELLENT
            else -> PerformanceLevel.GOOD
        }
    }

    /**
     * Возвращает рекомендации по оптимизации
     */
    fun getOptimizationSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()

        if (!systemReady) {
            suggestions.add("Проверьте разрешения и настройки местоположения")
        }

        if (lastUpdateAge > 300) {
            suggestions.add("Перезапустите обновления местоположения")
        }

        if (currentAccuracy > 100) {
            suggestions.add("Смените режим на HIGH_ACCURACY для лучшей точности")
        }

        if (totalActiveRequests > 8) {
            suggestions.add("Оптимизируйте количество одновременных запросов")
        }

        if (batteryUsageLevel == PowerLevel.HIGH) {
            suggestions.add("Рассмотрите переключение в энергосберегающий режим")
        }

        if (!hasValidCache) {
            suggestions.add("Дождитесь получения данных для кэширования")
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Система работает оптимально")
        }

        return suggestions
    }

    /**
     * Возвращает оценку эффективности работы (0-100)
     */
    fun getEfficiencyScore(): Int {
        var score = 50 // базовая оценка

        // Положительные факторы
        if (systemReady) score += 20
        if (isMonitoringActive) score += 10
        if (hasValidCache) score += 5
        if (lastUpdateAge in 0..60) score += 15
        if (currentAccuracy <= 20) score += 10
        if (totalActiveRequests <= 3) score += 5

        // Негативные факторы
        if (lastUpdateAge > 300) score -= 20
        if (currentAccuracy > 100) score -= 15
        if (totalActiveRequests > 8) score -= 10
        if (!isMonitoringActive) score -= 10

        return score.coerceIn(0, 100)
    }

    /**
     * Проверяет критические проблемы
     */
    fun hasCriticalIssues(): Boolean {
        return !systemReady || lastUpdateAge > 600 || totalActiveRequests > 15
    }

    /**
     * Возвращает общий рейтинг здоровья системы
     */
    fun getHealthRating(): HealthRating {
        val score = getEfficiencyScore()
        return when {
            score >= 90 -> HealthRating.EXCELLENT
            score >= 75 -> HealthRating.GOOD
            score >= 60 -> HealthRating.FAIR
            score >= 40 -> HealthRating.POOR
            else -> HealthRating.CRITICAL
        }
    }
}

/**
 * Уровни производительности системы
 */
enum class PerformanceLevel(val displayName: String, val icon: String, val color: String) {
    CRITICAL("Критическая", "🚨", "🔴"),
    POOR("Низкая", "❌", "🔴"),
    FAIR("Удовлетворительная", "⚠️", "🟡"),
    GOOD("Хорошая", "✅", "🟢"),
    EXCELLENT("Отличная", "🏆", "💚");

    fun getFullDescription(): String {
        return "$color $icon $displayName производительность"
    }
}

/**
 * Общий рейтинг здоровья системы
 */
enum class HealthRating(val displayName: String, val icon: String, val description: String) {
    CRITICAL("Критическое", "🚨", "Система требует немедленного вмешательства"),
    POOR("Плохое", "❌", "Множественные проблемы влияют на работу"),
    FAIR("Удовлетворительное", "⚠️", "Некоторые проблемы требуют внимания"),
    GOOD("Хорошее", "✅", "Система работает стабильно с минимальными проблемами"),
    EXCELLENT("Отличное", "🏆", "Система работает на оптимальном уровне");

    fun getFullDescription(): String {
        return "$icon $displayName - $description"
    }
}

/**
 * Enum для качества GPS сигнала с расширенной функциональностью
 */
enum class SignalQuality(
    val displayName: String,
    val icon: String,
    val description: String,
    val minAccuracy: Float,
    val maxAccuracy: Float
) {
    UNKNOWN("Неизвестно", "❓", "Качество сигнала не определено", 0f, Float.MAX_VALUE),
    NO_SIGNAL("Нет сигнала", "🚫", "GPS сигнал отсутствует", Float.MAX_VALUE, Float.MAX_VALUE),
    POOR("Слабый", "📶", "Слабый GPS сигнал, низкая точность", 200f, Float.MAX_VALUE),
    FAIR("Средний", "📶📶", "Умеренный GPS сигнал", 50f, 200f),
    GOOD("Хороший", "📶📶📶", "Хороший GPS сигнал", 10f, 50f),
    EXCELLENT("Отличный", "📶📶📶📶", "Отличный GPS сигнал, высокая точность", 0f, 10f);

    /**
     * Возвращает цветовой индикатор качества
     */
    fun getColorIndicator(): String {
        return when (this) {
            UNKNOWN -> "⚪"
            NO_SIGNAL -> "🔴"
            POOR -> "🔴"
            FAIR -> "🟡"
            GOOD -> "🟢"
            EXCELLENT -> "💚"
        }
    }

    /**
     * Возвращает полное описание с индикаторами
     */
    fun getFullDescription(): String {
        return "${getColorIndicator()} $displayName $icon - $description"
    }

    /**
     * Определяет качество сигнала по точности
     */
    companion object {
        fun fromAccuracy(accuracy: Float): SignalQuality {
            return values().firstOrNull { accuracy in it.minAccuracy..it.maxAccuracy } ?: UNKNOWN
        }
    }

    /**
     * Проверяет пригодность для критических операций
     */
    fun isSuitableForCriticalOperations(): Boolean {
        return this in listOf(GOOD, EXCELLENT)
    }

    /**
     * Проверяет пригодность для обычного использования
     */
    fun isSuitableForGeneralUse(): Boolean {
        return this in listOf(FAIR, GOOD, EXCELLENT)
    }

    /**
     * Возвращает рекомендации по улучшению сигнала
     */
    fun getImprovementSuggestions(): List<String> {
        return when (this) {
            NO_SIGNAL, POOR -> listOf(
                "Выйдите на открытое пространство",
                "Убедитесь, что GPS включен",
                "Проверьте, не блокирует ли чехол антенну"
            )

            FAIR -> listOf(
                "Переместитесь подальше от высоких зданий",
                "Дождитесь лучшего приема сигнала"
            )

            GOOD, EXCELLENT -> listOf("Качество сигнала хорошее")
            UNKNOWN -> listOf("Невозможно определить качество сигнала")
        }
    }
}