package com.example.bluetooth_andr11.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bluetooth_andr11.R
import com.example.bluetooth_andr11.map.MapModule

@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { MapModule.initializeMap(context) }

    // Переменная для управления состоянием слежения
    var isFollowing by remember { mutableStateOf(false) }

    // Иконка пользователя всегда видима
    LaunchedEffect(mapView) {
        MapModule.initializeLocationOverlay(context, mapView)
    }

    DisposableEffect(Unit) {
        onDispose {
            MapModule.disableLocationUpdates(context)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Карта
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AndroidView(factory = { mapView })

            Box(contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .width(36.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp)) // Скругляем углы
                    .background(Color.White) // Фон кнопки
                    .clickable {
                        if (!isFollowing) {
                            MapModule.enableFollowLocationOverlay(mapView)
                            isFollowing = true
                            Toast
                                .makeText(context, "Слежение включено", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            MapModule.disableFollowLocationOverlay(mapView)
                            isFollowing = false
                            Toast
                                .makeText(context, "Слежение отключено", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }) {
                // Иконка центрирования
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.baseline_center_focus_strong_24),
                    contentDescription = "Центрирование",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun FullWidthMapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember { MapModule.initializeMap(context) }

    // Переменная для управления состоянием слежения
    var isFollowing by remember { mutableStateOf(false) }

    // Иконка пользователя всегда видима
    LaunchedEffect(mapView) {
        MapModule.initializeLocationOverlay(context, mapView)
    }

    DisposableEffect(Unit) {
        onDispose {
            MapModule.disableLocationUpdates(context)
        }
    }

    // 🔥 Карта без внутренних отступов
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize().clip(RectangleShape) // Убираем все padding
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize() // Карта занимает весь контейнер
        )

        // Кнопка центрирования в правом нижнем углу
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp) // Только отступ от края экрана
                .width(40.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.9f))
                .clickable {
                    if (!isFollowing) {
                        MapModule.enableFollowLocationOverlay(mapView)
                        isFollowing = true
                        Toast
                            .makeText(context, "Слежение включено", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        MapModule.disableFollowLocationOverlay(mapView)
                        isFollowing = false
                        Toast
                            .makeText(context, "Слежение отключено", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.baseline_center_focus_strong_24),
                contentDescription = "Центрирование",
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }


}