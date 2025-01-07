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

    fun initializeMap(context: Context): MapView {
        return MapView(context).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(55.751244, 37.618423)) // Москва
        }
    }

    fun clearRoute(mapView: MapView) {
        // Оставляем на карте только оверлеи с иконкой текущего местоположения
        val locationOverlay =
            mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()

        // Очищаем только маршрут
        mapView.overlayManager.clear()

        // Возвращаем оверлей с иконкой текущего местоположения
        locationOverlay?.let { mapView.overlays.add(it) }

        // Обновляем карту
        mapView.invalidate()
    }

    fun enableAlwaysVisibleLocationOverlay(context: Context, mapView: MapView) {
        var lastBearing = 0f // Переменная для хранения последнего азимута

        if (myLocationOverlay == null) {
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

                        // Проверяем, изменился ли азимут
                        val direction = if (location.hasBearing()) {
                            location.bearing
                        } else {
                            lastBearing // Если азимут не изменился, используем последний
                        }
                        lastBearing = direction // Обновляем последний азимут

                        // Рисуем треугольную иконку
                        val path = android.graphics.Path().apply {
                            moveTo(
                                screenCoords.x.toFloat(), screenCoords.y.toFloat() - 40
                            ) // Вершина
                            lineTo(
                                screenCoords.x.toFloat() - 30, screenCoords.y.toFloat() + 30
                            ) // Лево
                            lineTo(
                                screenCoords.x.toFloat() + 30, screenCoords.y.toFloat() + 30
                            ) // Право
                            close()
                        }

                        // Настраиваем краску для треугольника
                        val paint = android.graphics.Paint().apply {
                            color = Color.parseColor("#2E8B57") // Зелёный цвет
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }

                        // Поворачиваем холст в направлении устройства
                        canvas?.save()
                        canvas?.rotate(
                            direction, screenCoords.x.toFloat(), screenCoords.y.toFloat()
                        )

                        // Рисуем треугольник
                        canvas?.drawPath(path, paint)

                        // Восстанавливаем холст
                        canvas?.restore()
                    }
                }
            }

            myLocationOverlay?.enableMyLocation()
            myLocationOverlay?.enableFollowLocation()
            mapView.overlays.add(myLocationOverlay)

            // Добавляем слушатель для логирования перемещений
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, // Приоритет высокой точности
                15000L // Интервал обновления в миллисекундах
            ).apply {
                setMinUpdateIntervalMillis(5000L) // Минимальный интервал обновления
            }.build()


            val locationCallback = object : LocationCallback() {
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
                    locationRequest, locationCallback, Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                Toast.makeText(
                    context, "Нет разрешения на доступ к местоположению", Toast.LENGTH_LONG
                ).show()
            }
        }
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
