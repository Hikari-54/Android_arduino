package com.example.bluetooth_andr11.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.log.LogModule
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.location.LocationManager as AndroidLocationManager

/**
 * Расширенный менеджер местоположения с реальным мониторингом GPS и управлением ресурсами.
 *
 * Основные возможности:
 * - Реактивное отслеживание изменений GPS состояния в реальном времени
 * - Многоуровневые режимы точности с автоматической оптимизацией
 * - Fallback механизмы для обеспечения надежности получения координат
 * - Интеллектуальное кэширование с проверкой актуальности данных
 * - Thread-safe операции с concurrent коллекциями
 * - Автоматическое управление ресурсами и lifecycle
 * - Интеграция с системой логирования для аудита GPS событий
 *
 * Архитектурные принципы:
 * - Асинхронная обработка всех GPS операций
 * - Реактивные состояния для интеграции с Compose UI
 * - Автоматическая очистка ресурсов при завершении работы
 * - Защита от memory leaks через proper disposal pattern
 * - Graceful fallback при недоступности основных провайдеров
 * - Оптимизированное энергопотребление через умное управление запросами
 *
 * Поддерживаемые режимы точности:
 * - HIGH_ACCURACY: GPS + Network, высокая точность, больше энергии
 * - BALANCED: Сбалансированный режим, оптимальное соотношение точность/энергия
 * - LOW_POWER: Экономия батареи, меньше точности
 * - GPS_ONLY: Только GPS, максимальная точность
 * - NETWORK_ONLY: Только сеть, быстрое получение координат
 * - PASSIVE: Пассивное получение от других приложений
 *
 * Thread Safety:
 * Все публичные методы thread-safe, внутреннее состояние защищено от
 * concurrent access с использованием appropriate synchronization mechanisms.
 */
class EnhancedLocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    companion object {
        private const val TAG = "EnhancedLocationManager"

        // === ВРЕМЕННЫЕ КОНСТАНТЫ ===

        /** Максимальное время ожидания получения местоположения */
        private const val LOCATION_TIMEOUT_MS = 30000L

        /** Время жизни кэшированных данных местоположения */
        private const val CACHE_TIMEOUT_MS = 5 * 60 * 1000L // 5 минут

        /** Максимальный возраст местоположения для использования */
        private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1000L // 5 минут

        // === ТОЧНОСТЬ И КАЧЕСТВО ===

        /** Минимальная приемлемая точность в метрах */
        private const val MIN_ACCURACY_METERS = 1000f

        /** Максимальная разница во времени для сравнения местоположений */
        private const val LOCATION_COMPARISON_TIME_DELTA = 2 * 60 * 1000L // 2 минуты

        /** Максимальная разница в точности для замены местоположения */
        private const val MAX_ACCURACY_DEGRADATION = 200f

        // === РЕЖИМЫ ОБНОВЛЕНИЯ ===

        /** Интервал обновления для высокой точности */
        private const val HIGH_ACCURACY_INTERVAL = 10000L
        private const val HIGH_ACCURACY_MIN_INTERVAL = 5000L

        /** Интервал обновления для сбалансированного режима */
        private const val BALANCED_INTERVAL = 30000L
        private const val BALANCED_MIN_INTERVAL = 15000L

        /** Интервал обновления для экономии энергии */
        private const val LOW_POWER_INTERVAL = 60000L
        private const val LOW_POWER_MIN_INTERVAL = 30000L
    }

    // === REACTIVE UI СОСТОЯНИЯ ===

    /** Текущие координаты в формате "latitude, longitude" */
    val locationCoordinates = mutableStateOf("Неизвестно")

    /** Точность текущего местоположения в метрах */
    val locationAccuracy = mutableStateOf(0f)

    /** Источник получения местоположения (GPS, Network, Cache и т.д.) */
    val locationSource = mutableStateOf("Неизвестно")

    /** Доступность служб местоположения */
    val isLocationEnabled = mutableStateOf(false)

    // === УПРАВЛЕНИЕ СОСТОЯНИЕМ ===

    /** Флаг активности обновлений местоположения */
    @Volatile
    private var isUpdatingLocation = false

    /** Callback для получения обновлений местоположения */
    private var locationCallback: LocationCallback? = null

    /** Текущий режим работы GPS */
    private var currentLocationMode = LocationMode.BALANCED

    /** Флаг disposal состояния для предотвращения использования после cleanup */
    @Volatile
    private var isDisposed = false

    // === GPS МОНИТОРИНГ ===

    /** BroadcastReceiver для отслеживания изменений GPS */
    private var locationStatusReceiver: BroadcastReceiver? = null

    /** Callback для уведомлений об изменениях GPS состояния */
    private var onLocationStatusChanged: ((Boolean) -> Unit)? = null

    /** Последнее известное состояние GPS для определения изменений */
    @Volatile
    private var lastKnownGpsState = false

    // === КЭШИРОВАНИЕ И ОПТИМИЗАЦИЯ ===

    /** Время последнего обновления местоположения */
    @Volatile
    private var lastLocationUpdate = 0L

    /** Кэшированное местоположение для fallback операций */
    private var cachedLocation: Location? = null

    /** Concurrent map активных запросов местоположения для управления ресурсами */
    private val activeRequests = ConcurrentHashMap<String, CancellationTokenSource>()

    init {
        initializeLocationManager()
    }

    // === ИНИЦИАЛИЗАЦИЯ ===

    /**
     * Инициализирует менеджер местоположения со всеми необходимыми компонентами.
     *
     * Последовательность инициализации:
     * 1. Настройка callback'ов для получения местоположения
     * 2. Проверка текущих настроек GPS и разрешений
     * 3. Получение последнего известного местоположения из всех источников
     * 4. Установка мониторинга изменений GPS в реальном времени
     */
    private fun initializeLocationManager() {
        try {
            setupLocationCallback()
            checkLocationSettings()
            getLastKnownLocationAll()
            setupLocationStatusMonitoring()

            Log.d(TAG, "✅ EnhancedLocationManager успешно инициализирован")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации: ${e.message}")

            // Логируем критическую ошибку инициализации
            // Используем простое логирование через LogModule.logEvent так как this ещё не готов
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    LogModule.logEvent(
                        context,
                        "СИСТЕМА_GPS: Критическая ошибка инициализации LocationManager: ${e.message}"
                    )
                } catch (logError: Exception) {
                    Log.e(
                        TAG,
                        "❌ Не удалось записать лог ошибки инициализации: ${logError.message}"
                    )
                }
            }
        }
    }

    /**
     * Настраивает мониторинг изменений GPS в реальном времени через BroadcastReceiver.
     *
     * Отслеживает:
     * - Включение/выключение GPS провайдера
     * - Изменения доступности Network провайдера
     * - Системные изменения настроек местоположения
     */
    private fun setupLocationStatusMonitoring() {
        if (isDisposed) return

        try {
            locationStatusReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        LocationManager.PROVIDERS_CHANGED_ACTION -> {
                            handleProviderChange(context)
                        }
                    }
                }
            }

            val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            context.registerReceiver(locationStatusReceiver, filter)

            // Сохраняем начальное состояние для отслеживания изменений
            lastKnownGpsState = isLocationServiceEnabled(context)
            isLocationEnabled.value = lastKnownGpsState

            Log.d(TAG, "📡 GPS мониторинг активирован. Начальное состояние: $lastKnownGpsState")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка настройки GPS мониторинга: ${e.message}")
        }
    }

    /**
     * Обрабатывает изменения состояния провайдеров местоположения.
     *
     * Выполняет:
     * - Проверку текущего состояния GPS и Network провайдеров
     * - Сравнение с предыдущим состоянием для определения изменений
     * - Логирование изменений с соответствующими сообщениями
     * - Уведомление подписчиков об изменениях состояния
     * - Обновление общих настроек местоположения
     */
    private fun handleProviderChange(context: Context?) {
        if (context == null || isDisposed) return

        try {
            val currentGpsState = isLocationServiceEnabled(context)
            val wasEnabled = lastKnownGpsState
            lastKnownGpsState = currentGpsState

            Log.d(TAG, "📍 GPS состояние изменилось: было=$wasEnabled, стало=$currentGpsState")

            // Обновляем реактивное состояние для UI
            isLocationEnabled.value = currentGpsState

            // Логируем только реальные изменения состояния
            if (wasEnabled != currentGpsState) {
                val event = if (currentGpsState) {
                    "✅ GPS ВКЛЮЧЕН - службы местоположения восстановлены"
                } else {
                    "❌ GPS ВЫКЛЮЧЕН - службы местоположения недоступны"
                }

                logLocationStatusChange(context, event)
                onLocationStatusChanged?.invoke(currentGpsState)
            }

            // Обновляем общие настройки после изменения
            checkLocationSettings()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка обработки изменения провайдера: ${e.message}")
        }
    }

    /**
     * Логирует изменения статуса местоположения через централизованную систему логирования.
     */
    private fun logLocationStatusChange(context: Context, event: String) {
        try {
            val isGpsAvailable = event.contains("ВКЛЮЧЕН") || event.contains("восстановлены")
            LogModule.logGpsStateChange(context, isGpsAvailable, event)

            Log.i(TAG, "📝 GPS событие обработано и записано в лог: $event")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка логирования GPS изменения: ${e.message}")

            // Fallback к простому логированию при ошибке
            try {
                LogModule.logEvent(context, "GPS: $event")
            } catch (fallbackError: Exception) {
                Log.e(TAG, "❌ Критическая ошибка fallback логирования: ${fallbackError.message}")
            }
        }
    }

    /**
     * Проверяет, включены ли службы местоположения в системе.
     *
     * @return true если GPS или Network провайдер включен
     */
    private fun isLocationServiceEnabled(context: Context): Boolean {
        return try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки состояния GPS: ${e.message}")
            false
        }
    }

    // === ПУБЛИЧНЫЕ МЕТОДЫ УПРАВЛЕНИЯ ===

    /**
     * Устанавливает callback для уведомлений об изменениях GPS состояния.
     *
     * @param listener функция, которая будет вызвана при изменении состояния GPS
     */
    fun setLocationStatusChangeListener(listener: (Boolean) -> Unit) {
        onLocationStatusChanged = listener
    }

    /**
     * Принудительно проверяет текущее состояние GPS и обновляет внутреннее состояние.
     *
     * Используется для:
     * - Синхронизации состояния после возврата в приложение
     * - Принудительной проверки после включения GPS в настройках
     * - Восстановления состояния после системных изменений
     *
     * @return текущее состояние GPS (true если доступен)
     */
    fun forceLocationStatusCheck(): Boolean {
        if (isDisposed) return false

        val currentState = isLocationServiceEnabled(context)
        val previousState = lastKnownGpsState

        Log.d(
            TAG,
            "🔍 Принудительная проверка GPS: предыдущее=$previousState, текущее=$currentState"
        )

        if (previousState != currentState) {
            lastKnownGpsState = currentState
            isLocationEnabled.value = currentState

            val event = if (currentState) {
                "✅ GPS ВКЛЮЧЕН (принудительная проверка пользователем)"
            } else {
                "❌ GPS ВЫКЛЮЧЕН (принудительная проверка)"
            }

            logLocationStatusChange(context, event)
            onLocationStatusChanged?.invoke(currentState)
        }

        return currentState
    }

    /**
     * Устанавливает режим работы GPS с автоматическим перезапуском обновлений.
     *
     * @param mode новый режим работы GPS
     */
    fun setLocationMode(mode: LocationMode) {
        if (isDisposed) return

        val oldMode = currentLocationMode
        currentLocationMode = mode

        if (oldMode != mode) {
            // Логируем изменение режима без использования this в LogModule
            try {
                LogModule.logEvent(context, "GPS: Режим изменен с $oldMode на $mode")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка логирования изменения режима: ${e.message}")
            }

            // Перезапускаем обновления с новыми параметрами
            if (isUpdatingLocation) {
                stopLocationUpdates()
                startLocationUpdates()
            }
        }
    }

    // === CALLBACK И ОБНОВЛЕНИЯ МЕСТОПОЛОЖЕНИЯ ===

    /**
     * Настраивает callback для получения обновлений местоположения от FusedLocationProviderClient.
     */
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isDisposed) return

                locationResult.lastLocation?.let { location ->
                    updateLocationInfo(location, false)
                    cachedLocation = location
                    lastLocationUpdate = System.currentTimeMillis()
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (isDisposed) return

                val wasAvailable = isLocationEnabled.value
                isLocationEnabled.value = availability.isLocationAvailable

                // Логируем изменения доступности местоположения
                if (wasAvailable != availability.isLocationAvailable) {
                    LogModule.logGpsStateChange(
                        context,
                        availability.isLocationAvailable,
                        "LocationAvailability callback изменение"
                    )
                }

                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "⚠️ Местоположение временно недоступно")
                }
            }
        }
    }

    /**
     * Обновляет информацию о местоположении в reactive состояниях UI.
     *
     * @param location новое местоположение
     * @param isFromCache флаг указывающий, что данные из кэша
     */
    private fun updateLocationInfo(location: Location, isFromCache: Boolean = false) {
        if (isDisposed) return

        try {
            val coordinates = String.format(
                Locale.US,
                "%.6f, %.6f",
                location.latitude,
                location.longitude
            )
            locationCoordinates.value = coordinates
            locationAccuracy.value = location.accuracy

            val source = determineLocationSource(location, isFromCache)
            locationSource.value = source

            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "📍 Координаты обновлены: $coordinates (±${location.accuracy.toInt()}м, $source)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка обновления информации о местоположении: ${e.message}")
        }
    }

    /**
     * Определяет источник получения местоположения на основе характеристик Location.
     *
     * @param location объект местоположения
     * @param isFromCache флаг кэшированных данных
     * @return строковое описание источника с иконкой
     */
    @Suppress("DEPRECATION")
    private fun determineLocationSource(location: Location, isFromCache: Boolean): String {
        return when {
            location.isFromMockProvider -> "🧪 Mock"
            isFromCache -> "💾 Cache"
            location.accuracy <= 10f -> "🛰️ GPS"
            location.accuracy <= 50f -> "📡 Network+"
            location.accuracy <= 500f -> "📶 Network"
            else -> "❓ Unknown"
        }
    }

    /**
     * Проверяет общие настройки местоположения и обновляет состояние.
     */
    private fun checkLocationSettings() {
        if (isDisposed) return

        try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
            val isGpsEnabled =
                locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
            val hasPermission = hasLocationPermission()

            val overallEnabled = (isGpsEnabled || isNetworkEnabled) && hasPermission

            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "📋 Настройки местоположения: GPS=$isGpsEnabled, Network=$isNetworkEnabled, Permission=$hasPermission"
                )
            }

            isLocationEnabled.value = overallEnabled
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки настроек местоположения: ${e.message}")
        }
    }

    // === УПРАВЛЕНИЕ ЗАПРОСАМИ МЕСТОПОЛОЖЕНИЯ ===

    /**
     * Создает оптимизированный запрос местоположения для указанного режима работы.
     *
     * @param mode режим работы GPS
     * @return настроенный LocationRequest
     */
    private fun createLocationRequest(mode: LocationMode): LocationRequest {
        return when (mode) {
            LocationMode.HIGH_ACCURACY -> LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, HIGH_ACCURACY_INTERVAL
            ).setMinUpdateIntervalMillis(HIGH_ACCURACY_MIN_INTERVAL).build()

            LocationMode.BALANCED -> LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, BALANCED_INTERVAL
            ).setMinUpdateIntervalMillis(BALANCED_MIN_INTERVAL).build()

            LocationMode.LOW_POWER -> LocationRequest.Builder(
                Priority.PRIORITY_LOW_POWER, LOW_POWER_INTERVAL
            ).setMinUpdateIntervalMillis(LOW_POWER_MIN_INTERVAL).build()

            LocationMode.PASSIVE -> LocationRequest.Builder(
                Priority.PRIORITY_PASSIVE, 300000L // 5 минут
            ).build()

            LocationMode.GPS_ONLY -> LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 15000L
            ).setMinUpdateIntervalMillis(10000L).build()

            LocationMode.NETWORK_ONLY -> LocationRequest.Builder(
                Priority.PRIORITY_LOW_POWER, 20000L
            ).setMinUpdateIntervalMillis(10000L).build()
        }
    }

    /**
     * Проверяет наличие разрешений на доступ к местоположению.
     *
     * @return true если разрешение ACCESS_FINE_LOCATION предоставлено
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Запускает регулярные обновления местоположения с указанным callback.
     *
     * @param onLocationUpdated опциональный callback для получения координат
     */
    fun startLocationUpdates(onLocationUpdated: ((String) -> Unit)? = null) {
        if (!hasLocationPermission() || isUpdatingLocation || isDisposed) {
            Log.w(
                TAG,
                "⚠️ Не удалось запустить обновления: permission=${hasLocationPermission()}, " +
                        "updating=$isUpdatingLocation, disposed=$isDisposed"
            )
            return
        }

        try {
            val locationRequest = createLocationRequest(currentLocationMode)

            // Удаляем предыдущий callback для избежания дублирования
            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }

            // Создаем новый callback с интеграцией пользовательского callback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (isDisposed) return

                    locationResult.lastLocation?.let { location ->
                        updateLocationInfo(location, false)
                        cachedLocation = location
                        lastLocationUpdate = System.currentTimeMillis()
                        onLocationUpdated?.invoke("${location.latitude}, ${location.longitude}")
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (isDisposed) return

                    val wasAvailable = isLocationEnabled.value
                    isLocationEnabled.value = availability.isLocationAvailable

                    if (wasAvailable != availability.isLocationAvailable) {
                        val event = if (availability.isLocationAvailable) {
                            "📶 Местоположение стало доступно через callback"
                        } else {
                            "📵 Местоположение стало недоступно через callback"
                        }
                        logLocationStatusChange(context, event)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            isUpdatingLocation = true
            Log.i(TAG, "🚀 Обновления местоположения запущены в режиме: $currentLocationMode")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка безопасности при запуске обновлений: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Общая ошибка запуска обновлений: ${e.message}")
        }
    }

    /**
     * Останавливает все активные обновления местоположения.
     */
    fun stopLocationUpdates() {
        try {
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
                Log.d(TAG, "🛑 Обновления местоположения остановлены")
            }
            isUpdatingLocation = false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка остановки обновлений: ${e.message}")
        }
    }

    // === ИНФОРМАЦИЯ О МЕСТОПОЛОЖЕНИИ ===

    /**
     * Возвращает текущую информацию о местоположении в структурированном виде.
     *
     * @return объект LocationInfo с актуальными данными
     */
    fun getLocationInfo(): LocationInfo {
        return LocationInfo(
            coordinates = locationCoordinates.value,
            accuracy = locationAccuracy.value,
            source = locationSource.value,
            timestamp = lastLocationUpdate,
            isFromCache = cachedLocation != null &&
                    System.currentTimeMillis() - lastLocationUpdate > CACHE_TIMEOUT_MS
        )
    }

    /**
     * Принудительно обновляет местоположение с указанным приоритетом.
     *
     * @param mode режим получения местоположения
     */
    fun forceLocationUpdate(mode: LocationMode = LocationMode.HIGH_ACCURACY) {
        if (!hasLocationPermission() || isDisposed) {
            Log.w(TAG, "⚠️ Нет разрешений для принудительного обновления")
            return
        }

        val requestId = "force_update_${System.currentTimeMillis()}"
        val priority = when (mode) {
            LocationMode.HIGH_ACCURACY, LocationMode.GPS_ONLY -> Priority.PRIORITY_HIGH_ACCURACY
            LocationMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationMode.LOW_POWER, LocationMode.NETWORK_ONLY -> Priority.PRIORITY_LOW_POWER
            LocationMode.PASSIVE -> Priority.PRIORITY_PASSIVE
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            activeRequests[requestId] = cancellationTokenSource

            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    activeRequests.remove(requestId)
                    location?.let {
                        updateLocationInfo(it, false)
                        cachedLocation = it
                        lastLocationUpdate = System.currentTimeMillis()
                        Log.i(TAG, "✅ Принудительно получены координаты: ±${it.accuracy.toInt()}м")
                    } ?: tryAlternativeLocationMethod(mode)
                }
                .addOnFailureListener { exception ->
                    activeRequests.remove(requestId)
                    Log.w(TAG, "❌ Ошибка принудительного обновления: ${exception.message}")
                    tryAlternativeLocationMethod(mode)
                }

            // Автоматическая отмена по timeout
            CoroutineScope(Dispatchers.IO).launch {
                delay(LOCATION_TIMEOUT_MS)
                if (activeRequests.containsKey(requestId)) {
                    cancellationTokenSource.cancel()
                    activeRequests.remove(requestId)
                    Log.w(TAG, "⏱️ Принудительное обновление отменено по timeout")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка безопасности при принудительном обновлении: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Общая ошибка принудительного обновления: ${e.message}")
        }
    }

    // === FALLBACK МЕТОДЫ ===

    /**
     * Получает последнее известное местоположение из всех доступных источников.
     */
    private fun getLastKnownLocationAll() {
        if (!hasLocationPermission() || isDisposed) return

        try {
            // Сначала пробуем FusedLocationClient как основной источник
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (!isDisposed) {
                    location?.let {
                        updateLocationInfo(it, true)
                        cachedLocation = it
                        lastLocationUpdate = System.currentTimeMillis()
                    } ?: getLastKnownFromSystem()
                }
            }.addOnFailureListener {
                if (!isDisposed) {
                    getLastKnownFromSystem()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка получения последнего местоположения: ${e.message}")
        }
    }

    /**
     * Получает последнее местоположение из системных провайдеров как fallback.
     */
    private fun getLastKnownFromSystem() {
        if (!hasLocationPermission() || isDisposed) return

        try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
            val providers = listOf(
                AndroidLocationManager.GPS_PROVIDER,
                AndroidLocationManager.NETWORK_PROVIDER,
                AndroidLocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                try {
                    if (locationManager.isProviderEnabled(provider)) {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null && isBetterLocation(location, bestLocation)) {
                            bestLocation = location
                        }
                    }
                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "⚠️ Ошибка получения местоположения от провайдера $provider: ${e.message}"
                    )
                }
            }

            bestLocation?.let {
                updateLocationInfo(it, true)
                cachedLocation = it
                lastLocationUpdate = System.currentTimeMillis()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка получения системного местоположения: ${e.message}")
        }
    }

    /**
     * Определяет, является ли новое местоположение лучше текущего.
     *
     * Критерии оценки:
     * - Время получения (новые данные предпочтительнее)
     * - Точность измерения (меньше accuracy = лучше)
     * - Возраст данных (не слишком старые)
     *
     * @param location новое местоположение для сравнения
     * @param currentBestLocation текущее лучшее местоположение
     * @return true если новое местоположение лучше
     */
    private fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) return true

        val timeDelta = location.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > LOCATION_COMPARISON_TIME_DELTA
        val isSignificantlyOlder = timeDelta < -LOCATION_COMPARISON_TIME_DELTA

        return when {
            isSignificantlyNewer -> true
            isSignificantlyOlder -> false
            else -> {
                val accuracyDelta = location.accuracy - currentBestLocation.accuracy
                val isMoreAccurate = accuracyDelta < 0
                val isSignificantlyLessAccurate = accuracyDelta > MAX_ACCURACY_DEGRADATION

                isMoreAccurate && !isSignificantlyLessAccurate
            }
        }
    }

    /**
     * Альтернативный метод получения местоположения при сбое основного.
     *
     * @param mode режим получения местоположения
     */
    private fun tryAlternativeLocationMethod(mode: LocationMode) {
        if (isDisposed) return

        Log.d(TAG, "🔄 Используем альтернативный метод получения местоположения")

        // Сначала пробуем получить последнее известное
        getLastKnownLocationAll()

        // Если режим позволяет, запускаем временные обновления
        if (mode == LocationMode.NETWORK_ONLY || mode == LocationMode.LOW_POWER) {
            startTemporaryLocationUpdates()
        }
    }

    /**
     * Запускает временные обновления местоположения для быстрого получения координат.
     */
    private fun startTemporaryLocationUpdates() {
        if (!hasLocationPermission() || isDisposed) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(10000L)
            .build()

        val tempCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isDisposed) return

                locationResult.lastLocation?.let { location ->
                    updateLocationInfo(location, false)
                    cachedLocation = location
                    lastLocationUpdate = System.currentTimeMillis()

                    // Останавливаем временные обновления после получения результата
                    fusedLocationClient.removeLocationUpdates(this)
                    Log.d(TAG, "⏹️ Временные обновления остановлены после получения результата")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                tempCallback,
                Looper.getMainLooper()
            )

            Log.d(TAG, "⏱️ Запущены временные обновления местоположения")

            // Автоматическая остановка через timeout
            CoroutineScope(Dispatchers.IO).launch {
                delay(LOCATION_TIMEOUT_MS)
                if (!isDisposed) {
                    fusedLocationClient.removeLocationUpdates(tempCallback)
                    Log.d(TAG, "⏱️ Временные обновления остановлены по timeout")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка запуска временных обновлений: ${e.message}")
        }
    }

    // === СТАТУС И ДИАГНОСТИКА ===

    /**
     * Возвращает подробный статус системы местоположения.
     *
     * @return объект LocationStatus с полной диагностической информацией
     */
    fun getLocationStatus(): LocationStatus {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        return LocationStatus(
            hasPermission = hasLocationPermission(),
            isGpsEnabled = locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER),
            isNetworkEnabled = locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER),
            isLocationAvailable = isLocationEnabled.value,
            currentMode = currentLocationMode,
            lastUpdate = getLocationInfo(),
            isUpdating = isUpdatingLocation,
            cachedLocation = cachedLocation
        )
    }

    /**
     * Проверяет общую доступность местоположения.
     *
     * @return true если местоположение доступно и разрешения предоставлены
     */
    fun isLocationAvailable(): Boolean {
        return locationCoordinates.value != "Неизвестно" &&
                hasLocationPermission() &&
                !isDisposed
    }

    /**
     * Возвращает рекомендуемый режим работы на основе текущих условий системы.
     *
     * @return оптимальный LocationMode для текущих условий
     */
    fun getRecommendedMode(): LocationMode {
        val status = getLocationStatus()
        return when {
            !status.hasPermission -> LocationMode.PASSIVE
            status.isGpsEnabled && status.isNetworkEnabled -> LocationMode.BALANCED
            status.isGpsEnabled -> LocationMode.GPS_ONLY
            status.isNetworkEnabled -> LocationMode.NETWORK_ONLY
            status.hasPermission -> LocationMode.LOW_POWER
            else -> LocationMode.PASSIVE
        }
    }

    /**
     * Отменяет все активные запросы местоположения для освобождения ресурсов.
     */
    private fun cancelAllActiveRequests() {
        activeRequests.values.forEach { cancellationToken ->
            try {
                cancellationToken.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Ошибка отмены запроса: ${e.message}")
            }
        }
        activeRequests.clear()
        Log.d(TAG, "🗑️ Все активные запросы местоположения отменены")
    }

    /**
     * Получает подробную статистику работы менеджера местоположения.
     *
     * @return объект LocationStatistics с метриками производительности
     */
    fun getLocationStatistics(): LocationStatistics {
        val status = getLocationStatus()
        return LocationStatistics(
            totalActiveRequests = activeRequests.size,
            isMonitoringActive = locationStatusReceiver != null,
            lastUpdateAge = if (lastLocationUpdate > 0) {
                (System.currentTimeMillis() - lastLocationUpdate) / 1000
            } else -1,
            currentAccuracy = locationAccuracy.value,
            hasValidCache = cachedLocation != null &&
                    System.currentTimeMillis() - lastLocationUpdate < CACHE_TIMEOUT_MS,
            currentMode = currentLocationMode,
            systemReady = status.isSystemReady()
        )
    }

    // === УПРАВЛЕНИЕ РЕСУРСАМИ ===

    /**
     * Полная очистка всех ресурсов менеджера местоположения.
     *
     * Выполняет:
     * - Остановку всех обновлений местоположения
     * - Отмену активных запросов
     * - Отписку от BroadcastReceiver
     * - Очистку всех callback'ов и кэша
     * - Установку флага disposed для предотвращения дальнейшего использования
     *
     * Метод идемпотентен - безопасно вызывать несколько раз.
     */
    fun cleanup() {
        if (isDisposed) return

        try {
            Log.d(TAG, "🧹 Начинаем полную очистку ресурсов EnhancedLocationManager")

            // Отмечаем как disposed в первую очередь
            isDisposed = true

            // Останавливаем все обновления местоположения
            stopLocationUpdates()

            // Отменяем все активные запросы
            cancelAllActiveRequests()

            // Отписываемся от BroadcastReceiver
            locationStatusReceiver?.let { receiver ->
                try {
                    context.unregisterReceiver(receiver)
                    Log.d(TAG, "📡 BroadcastReceiver успешно отписан")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Ошибка отписки от BroadcastReceiver: ${e.message}")
                }
            }
            locationStatusReceiver = null

            // Очищаем все callback'ы
            onLocationStatusChanged = null
            locationCallback = null

            // Очищаем кэшированные данные
            cachedLocation = null

            Log.d(TAG, "✅ Ресурсы EnhancedLocationManager полностью очищены")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки ресурсов: ${e.message}")
        }
    }

    /**
     * Проверяет, был ли менеджер очищен и более не может использоваться.
     *
     * @return true если cleanup() был вызван
     */
    fun isDisposed(): Boolean = isDisposed

    // === ДИАГНОСТИЧЕСКИЕ МЕТОДЫ ===

    /**
     * Возвращает краткий отчет о состоянии менеджера для логирования.
     *
     * @return строка с ключевыми метриками состояния
     */
    fun getStatusSummary(): String {
        val coordinates = locationCoordinates.value
        val accuracy = locationAccuracy.value
        val source = locationSource.value
        val mode = currentLocationMode
        val updating = if (isUpdatingLocation) "ON" else "OFF"
        val enabled = if (isLocationEnabled.value) "ON" else "OFF"

        return "GPS: $enabled | Mode: $mode | Updates: $updating | " +
                "Coords: ${if (coordinates != "Неизвестно") "✓" else "✗"} | " +
                "Accuracy: ±${accuracy.toInt()}м | Source: $source"
    }

    /**
     * Возвращает подробную диагностическую информацию для отладки.
     *
     * @return многострочный отчет с детальной информацией
     */
    fun getDetailedDiagnostics(): String {
        val stats = getLocationStatistics()
        val status = getLocationStatus()

        return buildString {
            appendLine("🔍 ДИАГНОСТИКА EnhancedLocationManager:")
            appendLine("════════════════════════════════════════")
            appendLine("• Статус: ${if (isDisposed) "DISPOSED" else "ACTIVE"}")
            appendLine("• Режим: $currentLocationMode")
            appendLine("• Координаты: ${locationCoordinates.value}")
            appendLine("• Точность: ±${locationAccuracy.value.toInt()}м")
            appendLine("• Источник: ${locationSource.value}")
            appendLine("• GPS включен: ${status.isGpsEnabled}")
            appendLine("• Network включен: ${status.isNetworkEnabled}")
            appendLine("• Разрешения: ${status.hasPermission}")
            appendLine("• Обновления активны: $isUpdatingLocation")
            appendLine("• Активных запросов: ${activeRequests.size}")
            appendLine("• Мониторинг: ${if (stats.isMonitoringActive) "ON" else "OFF"}")
            appendLine("• Последнее обновление: ${stats.lastUpdateAge}с назад")
            appendLine("• Кэш валиден: ${stats.hasValidCache}")
            appendLine("• Система готова: ${stats.systemReady}")
            appendLine("════════════════════════════════════════")
        }
    }

    /**
     * Возвращает список рекомендаций для улучшения работы системы местоположения.
     *
     * @return список строк с рекомендациями
     */
    fun getSystemRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val status = getLocationStatus()
        val stats = getLocationStatistics()

        if (!status.hasPermission) {
            recommendations.add("🔐 Предоставьте разрешения на доступ к местоположению")
        }

        if (!status.isGpsEnabled && !status.isNetworkEnabled) {
            recommendations.add("🛰️ Включите службы местоположения в настройках")
        }

        if (!status.isLocationAvailable && status.hasPermission) {
            recommendations.add("📡 Проверьте доступность GPS сигнала или подключения к сети")
        }

        if (stats.lastUpdateAge > 300) {
            recommendations.add("⏱️ Данные местоположения устарели - требуется обновление")
        }

        if (locationAccuracy.value > 100) {
            recommendations.add("🎯 Низкая точность GPS - переместитесь в место с лучшим приемом")
        }

        if (status.isSystemReady() && !isUpdatingLocation) {
            recommendations.add("▶️ Запустите обновления местоположения для получения актуальных данных")
        }

        if (isUpdatingLocation && currentLocationMode == LocationMode.LOW_POWER) {
            recommendations.add("⚡ Рассмотрите переключение в режим HIGH_ACCURACY для лучшей точности")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("✅ Система местоположения работает оптимально")
        }

        return recommendations
    }

    // === ДОПОЛНИТЕЛЬНЫЕ УТИЛИТАРНЫЕ МЕТОДЫ ===

    /**
     * Проверяет, требует ли система обновления настроек для оптимальной работы.
     *
     * @return true если рекомендуется изменение настроек
     */
    fun requiresSettingsUpdate(): Boolean {
        val status = getLocationStatus()
        return !status.hasPermission ||
                (!status.isGpsEnabled && !status.isNetworkEnabled) ||
                !status.isLocationAvailable
    }

    /**
     * Возвращает расчетное время до получения первого местоположения.
     *
     * @return время в секундах, -1 если невозможно определить
     */
    fun getEstimatedTimeToFirstFix(): Int {
        val status = getLocationStatus()

        return when {
            !status.isSystemReady() -> -1
            status.isGpsEnabled && status.isNetworkEnabled -> when (currentLocationMode) {
                LocationMode.HIGH_ACCURACY -> 15
                LocationMode.BALANCED -> 30
                LocationMode.LOW_POWER -> 60
                LocationMode.GPS_ONLY -> 45
                LocationMode.NETWORK_ONLY -> 10
                LocationMode.PASSIVE -> 120
            }

            status.isNetworkEnabled -> 10
            status.isGpsEnabled -> 60
            else -> -1
        }
    }

    /**
     * Возвращает информацию о расходе батареи текущим режимом.
     *
     * @return объект с информацией о потреблении энергии
     */
    fun getBatteryUsageInfo(): BatteryUsageInfo {
        val powerLevel = currentLocationMode.powerConsumption
        val isOptimized =
            currentLocationMode in listOf(LocationMode.LOW_POWER, LocationMode.PASSIVE)

        val estimatedHours = when (powerLevel) {
            PowerLevel.MINIMAL -> 48f
            PowerLevel.LOW -> 24f
            PowerLevel.MEDIUM -> 12f
            PowerLevel.HIGH -> 6f
        }

        return BatteryUsageInfo(
            powerLevel = powerLevel,
            isOptimized = isOptimized,
            estimatedBatteryLifeHours = estimatedHours,
            recommendations = getBatteryOptimizationTips()
        )
    }

    /**
     * Возвращает советы по оптимизации энергопотребления.
     */
    private fun getBatteryOptimizationTips(): List<String> {
        val tips = mutableListOf<String>()

        when (currentLocationMode.powerConsumption) {
            PowerLevel.HIGH -> {
                tips.add("Переключитесь в режим BALANCED для экономии энергии")
                tips.add("Используйте HIGH_ACCURACY только при необходимости")
                tips.add("Останавливайте обновления когда местоположение не нужно")
            }

            PowerLevel.MEDIUM -> {
                tips.add("Для длительной работы рассмотрите режим LOW_POWER")
                tips.add("Увеличьте интервал обновлений если возможно")
            }

            PowerLevel.LOW -> {
                tips.add("Текущий режим энергоэффективен")
                tips.add("Для максимальной экономии используйте PASSIVE режим")
            }

            PowerLevel.MINIMAL -> {
                tips.add("Режим максимально энергоэффективен")
            }
        }

        return tips
    }

    /**
     * Выполняет комплексную диагностику системы местоположения.
     *
     * @return объект с результатами диагностики
     */
    fun performSystemDiagnostic(): LocationDiagnosticResult {
        val status = getLocationStatus()
        val stats = getLocationStatistics()
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val recommendations = getSystemRecommendations()

        // Проверка критических проблем
        if (!status.hasPermission) {
            issues.add("Отсутствуют разрешения на местоположение")
        }

        if (!status.isGpsEnabled && !status.isNetworkEnabled) {
            issues.add("Все провайдеры местоположения отключены")
        }

        // Проверка предупреждений
        if (stats.lastUpdateAge > 300) {
            warnings.add("Данные местоположения устарели")
        }

        if (locationAccuracy.value > 200) {
            warnings.add("Очень низкая точность GPS")
        }

        if (activeRequests.size > 10) {
            warnings.add("Слишком много одновременных запросов")
        }

        val overallHealth = when {
            issues.isNotEmpty() -> DiagnosticHealth.CRITICAL
            warnings.size > 2 -> DiagnosticHealth.POOR
            warnings.size > 0 -> DiagnosticHealth.FAIR
            else -> DiagnosticHealth.GOOD
        }

        return LocationDiagnosticResult(
            overallHealth = overallHealth,
            criticalIssues = issues,
            warnings = warnings,
            recommendations = recommendations,
            systemStatus = status,
            performanceStats = stats,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Пытается автоматически оптимизировать настройки системы.
     *
     * @return true если оптимизация была применена
     */
    fun attemptAutoOptimization(): Boolean {
        if (isDisposed) return false

        val status = getLocationStatus()
        val stats = getLocationStatistics()
        var optimized = false

        // Оптимизация режима на основе качества сигнала
        if (locationAccuracy.value > 100 && currentLocationMode == LocationMode.LOW_POWER) {
            setLocationMode(LocationMode.BALANCED)
            optimized = true
            Log.i(TAG, "🔧 Автооптимизация: переключен в BALANCED режим для лучшей точности")
        }

        // Переключение в энергосберегающий режим при хорошем сигнале
        if (locationAccuracy.value <= 20 && currentLocationMode == LocationMode.HIGH_ACCURACY &&
            stats.lastUpdateAge < 60
        ) {
            setLocationMode(LocationMode.BALANCED)
            optimized = true
            Log.i(TAG, "🔧 Автооптимизация: переключен в BALANCED для экономии энергии")
        }

        // Попытка получить fresh данные если они устарели
        if (stats.lastUpdateAge > 180 && status.isSystemReady()) {
            forceLocationUpdate()
            optimized = true
            Log.i(TAG, "🔧 Автооптимизация: принудительное обновление устаревших данных")
        }

        // Логируем результат автооптимизации
        if (optimized) {
            try {
                LogModule.logEvent(context, "GPS: Выполнена автооптимизация системы")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка логирования автооптимизации: ${e.message}")
            }
        }

        return optimized
    }

    /**
     * Экспортирует конфигурацию системы для восстановления состояния.
     *
     * @return объект с текущей конфигурацией
     */
    fun exportConfiguration(): LocationConfiguration {
        return LocationConfiguration(
            currentMode = currentLocationMode,
            isUpdating = isUpdatingLocation,
            hasPermissions = hasLocationPermission(),
            lastKnownLocation = cachedLocation,
            configurationTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Восстанавливает конфигурацию системы из экспортированных данных.
     *
     * @param config конфигурация для восстановления
     * @return true если восстановление успешно
     */
    fun restoreConfiguration(config: LocationConfiguration): Boolean {
        if (isDisposed) return false

        try {
            // Восстанавливаем режим работы
            if (config.currentMode != currentLocationMode) {
                setLocationMode(config.currentMode)
            }

            // Восстанавливаем состояние обновлений
            if (config.isUpdating && !isUpdatingLocation && hasLocationPermission()) {
                startLocationUpdates()
            } else if (!config.isUpdating && isUpdatingLocation) {
                stopLocationUpdates()
            }

            // Восстанавливаем кэшированное местоположение если оно fresher
            config.lastKnownLocation?.let { location ->
                if (cachedLocation == null || location.time > cachedLocation!!.time) {
                    cachedLocation = location
                    updateLocationInfo(location, true)
                }
            }

            Log.i(TAG, "✅ Конфигурация системы успешно восстановлена")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка восстановления конфигурации: ${e.message}")
            return false
        }
    }

    // === DATA CLASSES ДЛЯ ДИАГНОСТИКИ ===

    /**
     * Информация о потреблении батареи
     */
    data class BatteryUsageInfo(
        val powerLevel: PowerLevel,
        val isOptimized: Boolean,
        val estimatedBatteryLifeHours: Float,
        val recommendations: List<String>
    ) {
        fun getSummary(): String {
            val optimizedText = if (isOptimized) "✅ Оптимизировано" else "⚠️ Можно улучшить"
            return "${powerLevel.getIcon()} ${powerLevel.displayName} потребление | " +
                    "~${estimatedBatteryLifeHours.toInt()}ч работы | $optimizedText"
        }
    }

    /**
     * Результат комплексной диагностики системы
     */
    data class LocationDiagnosticResult(
        val overallHealth: DiagnosticHealth,
        val criticalIssues: List<String>,
        val warnings: List<String>,
        val recommendations: List<String>,
        val systemStatus: LocationStatus,
        val performanceStats: LocationStatistics,
        val timestamp: Long
    ) {
        fun getSummary(): String {
            val issuesCount = criticalIssues.size + warnings.size
            val issuesText = if (issuesCount > 0) "$issuesCount проблем" else "проблем нет"

            return "${overallHealth.icon} ${overallHealth.displayName} | $issuesText | " +
                    "${recommendations.size} рекомендаций"
        }

        fun getDetailedReport(): String {
            return buildString {
                appendLine("🔍 РЕЗУЛЬТАТЫ ДИАГНОСТИКИ СИСТЕМЫ")
                appendLine("════════════════════════════════════════════")
                appendLine("• Общее состояние: ${overallHealth.getFullDescription()}")
                appendLine(
                    "• Время диагностики: ${
                        SimpleDateFormat(
                            "HH:mm:ss",
                            Locale.getDefault()
                        ).format(Date(timestamp))
                    }"
                )
                appendLine()

                if (criticalIssues.isNotEmpty()) {
                    appendLine("🚨 КРИТИЧЕСКИЕ ПРОБЛЕМЫ:")
                    criticalIssues.forEach { appendLine("  • $it") }
                    appendLine()
                }

                if (warnings.isNotEmpty()) {
                    appendLine("⚠️ ПРЕДУПРЕЖДЕНИЯ:")
                    warnings.forEach { appendLine("  • $it") }
                    appendLine()
                }

                if (recommendations.isNotEmpty()) {
                    appendLine("💡 РЕКОМЕНДАЦИИ:")
                    recommendations.take(5).forEach { appendLine("  • $it") }
                    if (recommendations.size > 5) {
                        appendLine("  • ... и еще ${recommendations.size - 5}")
                    }
                    appendLine()
                }

                appendLine("📊 ПРОИЗВОДИТЕЛЬНОСТЬ:")
                appendLine("  • ${performanceStats.getSummary()}")
                appendLine("════════════════════════════════════════════")
            }
        }
    }

    /**
     * Конфигурация системы для экспорта/импорта
     */
    data class LocationConfiguration(
        val currentMode: LocationMode,
        val isUpdating: Boolean,
        val hasPermissions: Boolean,
        val lastKnownLocation: Location?,
        val configurationTimestamp: Long
    ) {
        fun isValid(): Boolean {
            return configurationTimestamp > 0 &&
                    System.currentTimeMillis() - configurationTimestamp < 24 * 60 * 60 * 1000L // 24 часа
        }

        fun getAge(): Long {
            return (System.currentTimeMillis() - configurationTimestamp) / 1000L
        }
    }

    /**
     * Уровни здоровья системы для диагностики
     */
    enum class DiagnosticHealth(val displayName: String, val icon: String) {
        GOOD("Хорошее", "✅"),
        FAIR("Удовлетворительное", "🟡"),
        POOR("Плохое", "⚠️"),
        CRITICAL("Критическое", "🚨");

        fun getFullDescription(): String {
            return "$icon $displayName состояние системы"
        }
    }
}