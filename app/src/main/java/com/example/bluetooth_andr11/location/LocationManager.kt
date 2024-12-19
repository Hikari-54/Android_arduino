package com.example.bluetooth_andr11.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
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
                locationResult.lastLocation?.let { location ->
                    val coordinates = "Широта: ${location.latitude}, Долгота: ${location.longitude}"
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

    fun startLocationUpdates(onLocationUpdated: (String) -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are missing, gracefully handle this
            return
        }

        val locationRequest = createLocationRequest()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val coordinates =
                            "Широта: ${location.latitude}, Долгота: ${location.longitude}"
                        locationCoordinates.value = coordinates
                        onLocationUpdated(coordinates)
                    }
                }
            },
            Looper.getMainLooper()
        )

        isUpdatingLocation = true
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
