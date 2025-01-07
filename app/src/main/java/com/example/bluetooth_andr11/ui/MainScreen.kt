package com.example.bluetooth_andr11.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.ui.control.ControlPanel


@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onCommandSend: (String) -> Unit,
    temp1: String,
    temp2: String,
    hallState: String,
//    functionState: String,
    coordinates: String,
    acc: String,
    onNavigateToLogs: () -> Unit,
    bluetoothHelper: BluetoothHelper,
    locationManager: LocationManager
) {
    // Используем rememberSaveable для сохранения состояния между переходами
    val context = LocalContext.current
    val isHeatOn = rememberSaveable { mutableStateOf(false) }
    val isCoolOn = rememberSaveable { mutableStateOf(false) }
    val isLightOn = rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val screenHeight = maxHeight

        // Карта занимает адаптивную часть экрана
        val mapHeight = if (screenHeight > 600.dp) 300.dp else screenHeight * 0.4f

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Карта
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(mapHeight)
                ) {
                    MapScreen(modifier = Modifier.fillMaxSize())
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Панель управления
            item {
                ControlPanel(
                    onCommandSend = onCommandSend,
                    temp1 = temp1,
                    temp2 = temp2,
                    hallState = hallState,
                    coordinates = coordinates,
                    acc = acc,
                    isHeatOn = isHeatOn,
                    isCoolOn = isCoolOn,
                    isLightOn = isLightOn,
                    context = LocalContext.current,
                    bluetoothHelper = bluetoothHelper,
                    locationManager = locationManager
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Кнопка перехода на экран логов
            item {
                Button(onClick = onNavigateToLogs) {
                    Text(text = "Показать логи")
                }
            }
        }
    }
}

