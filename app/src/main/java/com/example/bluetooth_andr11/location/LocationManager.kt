package com.example.bluetooth_andr11.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority

class LocationManager(
    private val context: Context, private val fusedLocationClient: FusedLocationProviderClient
) {
    val locationCoordinates = mutableStateOf("Неизвестно")
    private var isUpdatingLocation = false
    private var locationCallback: LocationCallback? = null

    init {
        setupLocationCallback()
        // Пытаемся получить последнее известное местоположение при инициализации
        getLastKnownLocation()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val coordinates = "${location.latitude}, ${location.longitude}"
                    locationCoordinates.value = coordinates
                    Log.d("LocationManager", "Новые координаты получены: $coordinates")
                }
            }
        }
    }

    // 🔥 НОВОЕ: Получение последнего известного местоположения
    private fun getLastKnownLocation() {
        if (!hasLocationPermission()) {
            Log.w("LocationManager", "Нет разрешения на местоположение")
            return
        }

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val coordinates = "${it.latitude}, ${it.longitude}"
                    locationCoordinates.value = coordinates
                    Log.d("LocationManager", "Последнее известное местоположение: $coordinates")
                }
            }.addOnFailureListener { e ->
                Log.e("LocationManager", "Ошибка получения последнего местоположения: ${e.message}")
            }
        } catch (e: SecurityException) {
            Log.e("LocationManager", "SecurityException при получении последнего местоположения: ${e.message}")
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000).apply {
            setMinUpdateIntervalMillis(15000) // Более частые обновления
            setMaxUpdateDelayMillis(60000)
        }.build()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startLocationUpdates(onLocationUpdated: (String) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w("LocationManager", "Нет разрешений на местоположение")
            return
        }

        val locationRequest = createLocationRequest()

        // Удаляем предыдущий callback, если он существует
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }

        // Инициализируем новый LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val coordinates = "${location.latitude}, ${location.longitude}"
                    locationCoordinates.value = coordinates
                    onLocationUpdated(coordinates)
                    Log.d("LocationManager", "Обновлены координаты: $coordinates")
                }
            }
        }

        // Регистрируем обновления местоположения
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback!!, Looper.getMainLooper()
            )
            isUpdatingLocation = true
            Log.d("LocationManager", "Запущены обновления местоположения")
        } catch (e: SecurityException) {
            Log.e("LocationManager", "SecurityException при запуске обновлений: ${e.message}")
        }
    }

    fun getCurrentCoordinates(): String {
        val coordinates = locationCoordinates.value
        Log.d("LocationManager", "Запрошены текущие координаты: '$coordinates'")
        return coordinates
    }

    // 🔥 НОВОЕ: Принудительное обновление местоположения
    fun forceLocationUpdate() {
        if (!hasLocationPermission()) return

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        val coordinates = "${it.latitude}, ${it.longitude}"
                        locationCoordinates.value = coordinates
                        Log.d("LocationManager", "Принудительно получены координаты: $coordinates")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("LocationManager", "Ошибка принудительного получения координат: ${e.message}")
                }
        } catch (e: SecurityException) {
            Log.e("LocationManager", "SecurityException при принудительном обновлении: ${e.message}")
        }
    }

    // 🔥 НОВОЕ: Проверка готовности к получению координат
    fun isLocationAvailable(): Boolean {
        return locationCoordinates.value != "Неизвестно"
    }
}
