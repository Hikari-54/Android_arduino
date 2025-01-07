package com.example.bluetooth_andr11.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    acc: String
) {
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
                    acc = acc
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Фильтр логов
            item {
                val context = LocalContext.current
                LogFilterScreen { startDate, endDate ->
                    Toast.makeText(
                        context, "Фильтр: с $startDate по $endDate", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

