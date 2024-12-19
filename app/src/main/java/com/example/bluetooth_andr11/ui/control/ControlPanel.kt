package com.example.bluetooth_andr11.ui.control

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bluetooth_andr11.log.LogFilterScreen

@Composable
fun ControlPanel(
    onCommandSend: (String) -> Unit,
    batteryPercent: String,
    temp1: String,
    temp2: String,
    hallState: String,
    functionState: String,
    coordinates: String,
    acc: String,
    responseMessage: String
) {
    // Состояния кнопок
    var isHeatOn by remember { mutableStateOf(false) }
    var isCoolOn by remember { mutableStateOf(false) }
    var isLightOn by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Отображение данных устройства
        Text(text = "Уровень заряда батареи: $batteryPercent%", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Температура 1: $temp1°C", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Температура 2: $temp2°C", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Состояние датчика Холла: $hallState", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Функциональное состояние: $functionState", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Координаты: $coordinates", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Акселерометр: $acc", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Последний ответ: $responseMessage", modifier = Modifier.padding(4.dp))
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопки управления устройством
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Кнопка управления нагревом
            Button(
                onClick = {
                    isHeatOn = !isHeatOn
                    onCommandSend(if (isHeatOn) "H" else "h")
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isHeatOn) Color.Red else Color.Gray),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(text = if (isHeatOn) "ВЫКЛ НАГРЕВ" else "ВКЛ НАГРЕВ", maxLines = 1)
            }

            // Кнопка управления охлаждением
            Button(
                onClick = {
                    isCoolOn = !isCoolOn
                    onCommandSend(if (isCoolOn) "C" else "c")
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isCoolOn) Color.Blue else Color.Gray),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(text = if (isCoolOn) "ВЫКЛ ХОЛОД" else "ВКЛ ХОЛОД", maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка управления освещением
        Button(
            onClick = {
                isLightOn = !isLightOn
                onCommandSend(if (isLightOn) "L" else "l")
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (isLightOn) Color.Yellow else Color.Gray),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Text(text = if (isLightOn) "ВЫКЛ СВЕТ" else "ВКЛ СВЕТ", maxLines = 1)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Фильтр логов
        val context = LocalContext.current
        LogFilterScreen { startDate, endDate ->
            Toast.makeText(
                context,
                "Фильтр: с $startDate по $endDate",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
