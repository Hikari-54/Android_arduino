package com.example.bluetooth_andr11.map

import android.content.Context
import android.graphics.Color
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.bluetooth_andr11.log.LogModule
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

object MapModule {

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var locationCallback: LocationCallback? = null

    fun initializeMap(context: Context): MapView {
        return MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(55.751244, 37.618423)) // Москва
        }
    }

    // Удаление всех маршрутов, кроме оверлея пользователя
    fun clearRoute(mapView: MapView) {
        val locationOverlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        mapView.overlayManager.clear()
        locationOverlay?.let { mapView.overlays.add(it) }
        mapView.invalidate()
    }

    fun initializeLocationOverlay(context: Context, mapView: MapView) {
        var lastBearing = 0f

        // Удаляем предыдущий оверлей, чтобы избежать дублирования
        myLocationOverlay?.let {
            mapView.overlays.remove(it)
            it.disableMyLocation()
        }

        val locationProvider = GpsMyLocationProvider(context)
        myLocationOverlay = object : MyLocationNewOverlay(locationProvider, mapView) {
            override fun drawMyLocation(
                canvas: android.graphics.Canvas?,
                pj: org.osmdroid.views.Projection?,
                lastFix: Location?
            ) {
                lastFix?.let { location ->
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    val screenCoords = android.graphics.Point()
                    pj?.toPixels(geoPoint, screenCoords)

                    val direction = if (location.hasBearing()) location.bearing else lastBearing
                    lastBearing = direction

                    val path = android.graphics.Path().apply {
                        moveTo(screenCoords.x.toFloat(), screenCoords.y.toFloat() - 40)
                        lineTo(screenCoords.x.toFloat() - 30, screenCoords.y.toFloat() + 30)
                        lineTo(screenCoords.x.toFloat() + 30, screenCoords.y.toFloat() + 30)
                        close()
                    }

                    val paint = android.graphics.Paint().apply {
                        color = Color.parseColor("#2E8B57")
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }

                    canvas?.save()
                    canvas?.rotate(direction, screenCoords.x.toFloat(), screenCoords.y.toFloat())
                    canvas?.drawPath(path, paint)
                    canvas?.restore()
                }
            }
        }

        myLocationOverlay?.apply {
            enableMyLocation()
            enableFollowLocation()
        }

        mapView.overlays.add(myLocationOverlay)
        setupLocationUpdates(context)
    }

    // Настройка обновлений местоположения
    private fun setupLocationUpdates(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            15000L
        ).apply {
            setMinUpdateIntervalMillis(5000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    LogModule.logLocation(context, location)
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.d("LocationListener", "Местоположение недоступно")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Toast.makeText(context, "Нет разрешения на доступ к местоположению", Toast.LENGTH_LONG).show()
        }
    }

    // Отключение обновлений местоположения
    fun disableLocationUpdates(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        myLocationOverlay = null
    }

    fun enableFollowLocationOverlay(mapView: MapView) {
        val myLocationOverlay =
            mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        myLocationOverlay?.apply {
            enableFollowLocation()
            mapView.controller.animateTo(myLocation)
        }
    }

    fun disableFollowLocationOverlay(mapView: MapView) {
        val myLocationOverlay =
            mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        myLocationOverlay?.disableFollowLocation()
    }

}
