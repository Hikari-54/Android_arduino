package com.example.bluetooth_andr11.ui.map_log

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bluetooth_andr11.map.MapModule
import org.osmdroid.util.GeoPoint

@Composable
fun MapOverlay(
    context: Context,
    coordinates: Pair<Double, Double>,
    modifier: Modifier = Modifier
) {
    // Инициализация карты
    val mapView = remember { MapModule.initializeMap(context) }

    // Центрирование на переданных координатах
    LaunchedEffect(mapView) {
        val (latitude, longitude) = coordinates
        mapView.controller.setCenter(GeoPoint(latitude, longitude))
        MapModule.addMarker(mapView, latitude, longitude)
    }

    // Обработка жизненного цикла карты
    DisposableEffect(Unit) {
        onDispose {
            MapModule.disableLocationUpdates(context)
        }
    }

    // Отображение карты
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White, shape = RoundedCornerShape(8.dp))
    ) {
        AndroidView(factory = { mapView })
    }
}
