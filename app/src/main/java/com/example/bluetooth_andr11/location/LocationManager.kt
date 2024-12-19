package com.example.bluetooth_andr11.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority

class LocationManager(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private lateinit var locationCallback: LocationCallback
    private val locationCoordinates = mutableStateOf("Неизвестно")
    private var isUpdatingLocation = false

    init {
        setupLocationCallback()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    locationCoordinates.value =
                        "Широта: ${it.latitude}, Долгота: ${it.longitude}"
                }
            }
        }
    }

    private fun createLocationRequest(): LocationRequest {
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000).apply {
            setMinUpdateIntervalMillis(50000)
        }.build()
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Toast.makeText(
                context,
                "Нет разрешения на получение местоположения",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isUpdatingLocation) {
            val locationRequest = createLocationRequest()
            try {
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                isUpdatingLocation = true
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Ошибка обновления местоположения: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun stopLocationUpdates() {
        if (isUpdatingLocation) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isUpdatingLocation = false
        }
    }

    fun getLocationCoordinates(): String {
        return locationCoordinates.value
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
