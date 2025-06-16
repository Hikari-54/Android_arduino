// 🔥 ОБНОВЛЕННЫЙ EnhancedLocationManager с реальным мониторингом GPS

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
import java.util.Locale
import android.location.LocationManager as AndroidLocationManager

class EnhancedLocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    val locationCoordinates = mutableStateOf("Неизвестно")
    val locationAccuracy = mutableStateOf(0f)
    val locationSource = mutableStateOf("Неизвестно")
    val isLocationEnabled = mutableStateOf(false)

    private var isUpdatingLocation = false
    private var locationCallback: LocationCallback? = null
    private var currentLocationMode = LocationMode.BALANCED

    // 🔥 НОВОЕ: Для мониторинга изменений GPS
    private var locationStatusReceiver: BroadcastReceiver? = null
    private var onLocationStatusChanged: ((Boolean) -> Unit)? = null
    private var lastKnownGpsState = false

    enum class LocationMode {
        HIGH_ACCURACY, BALANCED, LOW_POWER, PASSIVE, GPS_ONLY, NETWORK_ONLY
    }

    data class LocationInfo(
        val coordinates: String,
        val accuracy: Float,
        val source: String,
        val timestamp: Long,
        val isFromCache: Boolean = false
    )

    data class LocationStatus(
        val hasPermission: Boolean,
        val isGpsEnabled: Boolean,
        val isNetworkEnabled: Boolean,
        val isLocationAvailable: Boolean,
        val currentMode: LocationMode,
        val lastUpdate: LocationInfo
    )

    init {
        setupLocationCallback()
        checkLocationSettings()
        getLastKnownLocationAll()
        setupLocationStatusMonitoring() // 🔥 НОВОЕ
    }

    // 🔥 НОВАЯ функция для мониторинга состояния GPS в реальном времени
    private fun setupLocationStatusMonitoring() {
        locationStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    LocationManager.PROVIDERS_CHANGED_ACTION -> {
                        Log.d(TAG, "🔄 Изменение провайдеров местоположения")

                        val currentGpsState = isLocationServiceEnabled(context!!)
                        val wasEnabled = lastKnownGpsState
                        lastKnownGpsState = currentGpsState

                        Log.d(TAG, "📍 GPS состояние: было=$wasEnabled, стало=$currentGpsState")

                        // Обновляем внутреннее состояние
                        isLocationEnabled.value = currentGpsState

                        // 🔥 ЛОГИРУЕМ ИЗМЕНЕНИЯ
                        if (wasEnabled != currentGpsState) {
                            val event = if (currentGpsState) {
                                "✅ GPS ВКЛЮЧЕН - местоположение доступно"
                            } else {
                                "❌ GPS ВЫКЛЮЧЕН - местоположение недоступно"
                            }

                            // Логируем критическое событие изменения GPS
                            logLocationStatusChange(context, event)

                            // Уведомляем callback
                            onLocationStatusChanged?.invoke(currentGpsState)
                        }

                        // Обновляем общие настройки
                        checkLocationSettings()
                    }
                }
            }
        }

        // Регистрируем receiver
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(locationStatusReceiver, filter)

        // Сохраняем текущее состояние
        lastKnownGpsState = isLocationServiceEnabled(context)
        Log.d(TAG, "🔄 Мониторинг GPS инициализирован. Текущее состояние: $lastKnownGpsState")
    }

    // 🔥 НОВАЯ функция для логирования изменений GPS
    private fun logLocationStatusChange(context: Context, event: String) {
        try {
            // 🔥 НОВОЕ: Определяем является ли это GPS событием
            val isGpsAvailable = event.contains("ВКЛЮЧЕН") || event.contains("доступно")

            // Используем умное логирование через LogModule
            LogModule.logGpsStateChange(context, isGpsAvailable, event)

            Log.i(TAG, "📝 GPS событие обработано умным логированием: $event")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка умного логирования GPS: ${e.message}")

            // Fallback к простому логированию
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date())
            val logMessage = "$timestamp - $event @ Системное событие\n"

            val logDir = java.io.File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = java.io.File(logDir, "events_log.txt")
            logFile.appendText(logMessage)
        }
    }

    // 🔥 НОВАЯ функция для проверки состояния GPS
    private fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // 🔥 НОВАЯ функция для установки callback изменений GPS
    fun setLocationStatusChangeListener(listener: (Boolean) -> Unit) {
        onLocationStatusChanged = listener
    }

    // 🔥 НОВАЯ функция для принудительной проверки GPS
    fun forceLocationStatusCheck(): Boolean {
        val currentState = isLocationServiceEnabled(context)
        val previousState = lastKnownGpsState

        Log.d(TAG, "🔍 Принудительная проверка GPS: было=$previousState, стало=$currentState")

        if (previousState != currentState) {
            lastKnownGpsState = currentState
            isLocationEnabled.value = currentState

            val event = if (currentState) {
                "✅ GPS ВКЛЮЧЕН (принудительная проверка)"
            } else {
                "❌ GPS ВЫКЛЮЧЕН (принудительная проверка)"
            }

            logLocationStatusChange(context, event)
            onLocationStatusChanged?.invoke(currentState)
        }

        return currentState
    }

    // 🔥 УЛУЧШЕННАЯ функция очистки ресурсов
    fun cleanup() {
        try {
            locationStatusReceiver?.let {
                context.unregisterReceiver(it)
            }
            stopLocationUpdates()
            Log.d(TAG, "🧹 Ресурсы EnhancedLocationManager очищены")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки ресурсов: ${e.message}")
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationInfo(location, false)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                val wasAvailable = isLocationEnabled.value
                isLocationEnabled.value = availability.isLocationAvailable

                // 🔥 ИЗМЕНЕНО: Используем умное логирование GPS
                if (wasAvailable != availability.isLocationAvailable) {
                    LogModule.logGpsStateChange(
                        context,
                        availability.isLocationAvailable,
                        "LocationAvailability callback"
                    )
                }

                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Местоположение недоступно")
                }
            }
        }
    }

    private fun updateLocationInfo(location: Location, isFromCache: Boolean = false) {
        val coordinates =
            String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
        locationCoordinates.value = coordinates
        locationAccuracy.value = location.accuracy

        val source = when {
            location.isFromMockProvider -> "🧪 Mock"
            isFromCache -> "💾 Cache"
            location.accuracy <= 10f -> "🛰️ GPS"
            location.accuracy <= 50f -> "📡 Network+"
            location.accuracy <= 500f -> "📶 Network"
            else -> "❓ Unknown"
        }
        locationSource.value = source

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Координаты обновлены: $coordinates (±${location.accuracy.toInt()}м, $source)"
            )
        }
    }

    private fun checkLocationSettings() {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)
        val isNetworkEnabled =
            locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
        val hasPermission = hasLocationPermission()

        val overallEnabled = isGpsEnabled || isNetworkEnabled || hasPermission

        // 🔥 ЛОГИРУЕМ ДЕТАЛЬНУЮ ИНФОРМАЦИЮ
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "📋 Проверка настроек: GPS=$isGpsEnabled, Network=$isNetworkEnabled, Permission=$hasPermission, Overall=$overallEnabled"
            )
        }

        isLocationEnabled.value = overallEnabled
    }

    // Остальные методы остаются без изменений...
    // [Включить все остальные методы из предыдущей версии]

    private fun getLastKnownLocationAll() {
        if (!hasLocationPermission()) return

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    updateLocationInfo(it, true)
                } ?: getLastKnownFromSystem()
            }.addOnFailureListener {
                getLastKnownFromSystem()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка получения последнего местоположения: ${e.message}")
        }
    }

    private fun getLastKnownFromSystem() {
        if (!hasLocationPermission()) return

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
                    // Игнорируем ошибки отдельных провайдеров
                }
            }

            bestLocation?.let {
                updateLocationInfo(it, true)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка получения системного местоположения: ${e.message}")
        }
    }

    private fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) return true

        val timeDelta = location.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > 2 * 60 * 1000
        val isSignificantlyOlder = timeDelta < -2 * 60 * 1000

        return when {
            isSignificantlyNewer -> true
            isSignificantlyOlder -> false
            else -> (location.accuracy - currentBestLocation.accuracy) < 0
        }
    }

    fun setLocationMode(mode: LocationMode) {
        val oldMode = currentLocationMode
        currentLocationMode = mode

        // 🔥 ЛОГИРУЕМ ИЗМЕНЕНИЕ РЕЖИМА
        if (oldMode != mode) {
            logLocationStatusChange(context, "🔄 Режим GPS изменен: $oldMode → $mode")
        }

        if (isUpdatingLocation) {
            stopLocationUpdates()
            startLocationUpdates()
        }
    }

    private fun createLocationRequest(mode: LocationMode): LocationRequest {
        return when (mode) {
            LocationMode.HIGH_ACCURACY -> LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000L
            )
                .setMinUpdateIntervalMillis(5000L).build()

            LocationMode.BALANCED -> LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                30000L
            )
                .setMinUpdateIntervalMillis(15000L).build()

            LocationMode.LOW_POWER -> LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 60000L)
                .setMinUpdateIntervalMillis(30000L).build()

            LocationMode.PASSIVE -> LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 300000L)
                .build()

            LocationMode.GPS_ONLY -> LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                15000L
            )
                .setMinUpdateIntervalMillis(10000L).build()

            LocationMode.NETWORK_ONLY -> LocationRequest.Builder(
                Priority.PRIORITY_LOW_POWER,
                20000L
            )
                .setMinUpdateIntervalMillis(10000L).build()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startLocationUpdates(onLocationUpdated: ((String) -> Unit)? = null) {
        if (!hasLocationPermission() || isUpdatingLocation) return

        val locationRequest = createLocationRequest(currentLocationMode)

        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationInfo(location, false)
                    onLocationUpdated?.invoke("${location.latitude}, ${location.longitude}")
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                val wasAvailable = isLocationEnabled.value
                isLocationEnabled.value = availability.isLocationAvailable

                if (wasAvailable != availability.isLocationAvailable) {
                    val event = if (availability.isLocationAvailable) {
                        "📶 Местоположение стало доступно"
                    } else {
                        "📵 Местоположение стало недоступно"
                    }
                    logLocationStatusChange(context, event)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isUpdatingLocation = true
            Log.i(TAG, "Обновления местоположения запущены: $currentLocationMode")
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка запуска обновлений: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        isUpdatingLocation = false
    }

    fun getCurrentCoordinates(): String = locationCoordinates.value

    fun getLocationInfo(): LocationInfo {
        return LocationInfo(
            coordinates = locationCoordinates.value,
            accuracy = locationAccuracy.value,
            source = locationSource.value,
            timestamp = System.currentTimeMillis()
        )
    }

    fun forceLocationUpdate(mode: LocationMode = LocationMode.HIGH_ACCURACY) {
        if (!hasLocationPermission()) return

        val priority = when (mode) {
            LocationMode.HIGH_ACCURACY, LocationMode.GPS_ONLY -> Priority.PRIORITY_HIGH_ACCURACY
            LocationMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            LocationMode.LOW_POWER, LocationMode.NETWORK_ONLY -> Priority.PRIORITY_LOW_POWER
            LocationMode.PASSIVE -> Priority.PRIORITY_PASSIVE
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    location?.let {
                        updateLocationInfo(it, false)
                        Log.i(TAG, "Принудительно получены координаты: ±${it.accuracy.toInt()}м")
                    } ?: tryAlternativeLocationMethod(mode)
                }
                .addOnFailureListener {
                    tryAlternativeLocationMethod(mode)
                }

            CoroutineScope(Dispatchers.IO).launch {
                delay(30000)
                cancellationTokenSource.cancel()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка принудительного обновления: ${e.message}")
        }
    }

    private fun tryAlternativeLocationMethod(mode: LocationMode) {
        getLastKnownLocationAll()
        if (mode == LocationMode.NETWORK_ONLY || mode == LocationMode.LOW_POWER) {
            startTemporaryLocationUpdates()
        }
    }

    private fun startTemporaryLocationUpdates() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(10000L)
            .build()

        val tempCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationInfo(location, false)
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                tempCallback,
                Looper.getMainLooper()
            )
            CoroutineScope(Dispatchers.IO).launch {
                delay(30000)
                fusedLocationClient.removeLocationUpdates(tempCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка временных обновлений: ${e.message}")
        }
    }

    fun getLocationStatus(): LocationStatus {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        return LocationStatus(
            hasPermission = hasLocationPermission(),
            isGpsEnabled = locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER),
            isNetworkEnabled = locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER),
            isLocationAvailable = isLocationEnabled.value,
            currentMode = currentLocationMode,
            lastUpdate = getLocationInfo()
        )
    }

    fun isLocationAvailable(): Boolean {
        return locationCoordinates.value != "Неизвестно" && hasLocationPermission()
    }

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

    companion object {
        private const val TAG = "EnhancedLocationManager"
    }
}