package com.example.bluetooth_andr11.ui.control

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.R
import com.example.bluetooth_andr11.ui.LogFilterScreen

@Composable
fun ControlPanel(
    onCommandSend: (String) -> Unit, temp1: String, temp2: String, hallState: String,
//    functionState: String,
    coordinates: String, acc: String, modifier: Modifier = Modifier
) {
    // Состояния кнопок
    var isHeatOn by remember { mutableStateOf(false) }
    var isCoolOn by remember { mutableStateOf(false) }
    var isLightOn by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        // Отображение данных устройства
        Text(
            text = "Температура верхний отсек: $temp1°C",
            modifier = Modifier.padding(4.dp),
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Температура нижний отсек: $temp2°C",
            modifier = Modifier.padding(4.dp),
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Статус: $hallState", modifier = Modifier.padding(4.dp), fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Координаты: $coordinates", modifier = Modifier.padding(4.dp), fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Text(text = "Функциональное состояние: $functionState", modifier = Modifier.padding(4.dp))
        // Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Уровень тряски: $acc", modifier = Modifier.padding(4.dp), fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Ряд кнопок с иконками
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Кнопка "Нагрев"
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        isHeatOn = !isHeatOn
                        onCommandSend(if (isHeatOn) "H" else "h")
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHeatOn) Color(
                            0xFFFF4500
                        ) else Color.Gray
                    ), modifier = Modifier.padding(4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(id = R.drawable.fire),
                        contentDescription = "Нагрев",
                        tint = Color.White
                    )
                }
                Text(text = "Нагрев", modifier = Modifier.padding(top = 4.dp))
            }

            // Кнопка "Холод"
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        isCoolOn = !isCoolOn
                        onCommandSend(if (isCoolOn) "C" else "c")
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCoolOn) Color(
                            0xFF1E90FF
                        ) else Color.Gray
                    ), modifier = Modifier.padding(4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(id = R.drawable.snowflake),
                        contentDescription = "Холод",
                        tint = Color.White
                    )
                }
                Text(text = "Холод", modifier = Modifier.padding(top = 4.dp))
            }

            // Кнопка "Свет"
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = {
                        isLightOn = !isLightOn
                        onCommandSend(if (isLightOn) "L" else "l")
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLightOn) Color(0xFFF0F000) else Color.Gray
                    ), modifier = Modifier.padding(4.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(id = R.drawable.light),
                        contentDescription = "Свет",
                        tint = Color.White
                    )
                }
                Text(text = "Свет", modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
