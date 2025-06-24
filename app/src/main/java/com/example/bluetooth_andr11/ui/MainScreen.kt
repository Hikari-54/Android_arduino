package com.example.bluetooth_andr11.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.ui.control.CardButton
import com.example.bluetooth_andr11.ui.control.ControlPanel
import com.example.bluetooth_andr11.ui.control.DeviceStatusDisplay

// Обновленный MainScreen.kt
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onCommandSend: (String) -> Unit,
    temp1: String,
    temp2: String,
    hallState: String,
    acc: String,
    onNavigateToLogs: () -> Unit,
    bluetoothHelper: BluetoothHelper,
    locationManager: EnhancedLocationManager
) {
    val context = LocalContext.current

    val isHeatOn = rememberSaveable { mutableStateOf(false) }
    val isCoolOn = rememberSaveable { mutableStateOf(false) }
    val isLightOn = rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenHeight = maxHeight
        val mapHeight = if (screenHeight > 600.dp) 300.dp else screenHeight * 0.4f

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🔥 Карта на полную ширину без padding
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                ) {
                    FullWidthMapScreen(modifier = Modifier.fillMaxSize())

                    // 🔥 Нижняя граница для четкого разделения
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.1f)
                                    )
                                )
                            )
                    )
                }
            }

            // 🔥 Контент с padding только снизу от карты
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp), // Padding только по бокам для контента
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Панель управления
                    DeviceStatusDisplay(
                        temp1 = temp1,
                        temp2 = temp2,
                        hallState = hallState,
                        acc = acc
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Кнопки управления
                    ControlPanel(
                        onCommandSend = onCommandSend,
                        temp1 = temp1,
                        temp2 = temp2,
                        hallState = hallState,
                        acc = acc,
                        isHeatOn = isHeatOn,
                        isCoolOn = isCoolOn,
                        isLightOn = isLightOn,
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Кнопка перехода на экран логов
                    CardButton(
                        text = "Посмотреть записи",
                        onClick = onNavigateToLogs,
                        modifier = Modifier.padding(horizontal = 4.dp) // Выравнивание с карточками
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}


