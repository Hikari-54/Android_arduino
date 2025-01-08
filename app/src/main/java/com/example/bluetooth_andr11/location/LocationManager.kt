package com.example.bluetooth_andr11.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
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
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val coordinates = "${location.latitude}, ${location.longitude}"
                    locationCoordinates.value = coordinates
                }
            }
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000).apply {
            setMinUpdateIntervalMillis(30000) // Updated to ensure faster interval
        }.build()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startLocationUpdates(onLocationUpdated: (String) -> Unit) {
        if (!hasLocationPermission()) {
            // Уведомляем, если отсутствуют разрешения
            println("Permissions for location are missing")
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
                }
            }
        }

        // Регистрируем обновления местоположения
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback!!, Looper.getMainLooper()
            )
            isUpdatingLocation = true
        } catch (e: SecurityException) {
            println("Location permission is missing or denied: ${e.message}")
        }
    }

    fun getCurrentCoordinates(): String {
        return locationCoordinates.value
    }


}
