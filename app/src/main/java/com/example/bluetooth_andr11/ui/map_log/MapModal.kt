package com.example.bluetooth_andr11.ui.map_log

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.bluetooth_andr11.map.MapModule
import org.osmdroid.util.GeoPoint

@Composable
fun MapModal(
    context: Context,
    coordinates: Pair<Double, Double>,
    onDismiss: () -> Unit,
    eventTitle: String
) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Заголовок с кнопкой закрытия
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = eventTitle,
                            modifier = Modifier.weight(1f),
                            fontSize = 18.sp,
                            color = Color.Black,
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onDismiss() }
                        )
                    }

                    // 🔥 ИСПРАВЛЕНО: Отображение карты с маркером
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.LightGray)
                    ) {
                        EventMapView(
                            context = context,
                            coordinates = coordinates,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// 🔥 НОВЫЙ компонент для отображения карты события
@Composable
fun EventMapView(
    context: Context,
    coordinates: Pair<Double, Double>,
    modifier: Modifier = Modifier
) {
    // Создаем новый экземпляр карты для события
    val mapView = remember {
        MapModule.initializeMap(context).apply {
            // Убираем пользовательский оверлей для карты событий
            overlays.clear()
        }
    }

    // Настройка карты для события
    LaunchedEffect(coordinates) {
        val (latitude, longitude) = coordinates
        val geoPoint = GeoPoint(latitude, longitude)

        // Центрируем карту на событии
        mapView.controller.setCenter(geoPoint)
        mapView.controller.setZoom(16.0) // Более детальный зум

        // Добавляем маркер события
        MapModule.addMarker(mapView, latitude, longitude)

        // Принудительно обновляем карту
        mapView.invalidate()
    }

    // Очистка при уничтожении
    DisposableEffect(Unit) {
        onDispose {
            mapView.overlays.clear()
        }
    }

    // Отображение карты
    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}