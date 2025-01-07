package com.example.bluetooth_andr11.ui.control

import android.content.Context
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
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.R
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.log.LogModule

@Composable
fun ControlPanel(
    onCommandSend: (String) -> Unit,
    temp1: String,
    temp2: String,
    hallState: String,
//    functionState: String,
//    coordinates: String,
    acc: String,
    isHeatOn: MutableState<Boolean>,
    isCoolOn: MutableState<Boolean>,
    isLightOn: MutableState<Boolean>,
    context: Context,
    modifier: Modifier = Modifier,
    bluetoothHelper: BluetoothHelper,
    locationManager: LocationManager
) {
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
//        Text(
//            text = "Координаты: $coordinates", modifier = Modifier.padding(4.dp), fontSize = 18.sp
//        )
//        Spacer(modifier = Modifier.height(8.dp))
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
                        if (!bluetoothHelper.isDeviceConnected) {
                            Toast.makeText(context, "Bluetooth не подключен", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            isHeatOn.value = !isHeatOn.value
                            onCommandSend(if (isHeatOn.value) "H" else "h")
                            LogModule.logEventWithLocation(
                                context = context,
                                bluetoothHelper = bluetoothHelper,
                                locationManager = locationManager,
                                event = "Нагрев ${if (isHeatOn.value) "включен" else "выключен"}"
                            )
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHeatOn.value) Color(
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
                        if (!bluetoothHelper.isDeviceConnected) {
                            Toast.makeText(context, "Bluetooth не подключен", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            isCoolOn.value = !isCoolOn.value
                            onCommandSend(if (isCoolOn.value) "C" else "c")
                            LogModule.logEventWithLocation(
                                context = context,
                                bluetoothHelper = bluetoothHelper,
                                locationManager = locationManager,
                                event = "Холод ${if (isCoolOn.value) "включен" else "выключен"}"
                            )
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCoolOn.value) Color(0xFF1E90FF) else Color.Gray
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
                        if (!bluetoothHelper.isDeviceConnected) {
                            Toast.makeText(context, "Bluetooth не подключен", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            isLightOn.value = !isLightOn.value
                            onCommandSend(if (isLightOn.value) "L" else "l")
                            LogModule.logEventWithLocation(
                                context = context,
                                bluetoothHelper = bluetoothHelper,
                                locationManager = locationManager,
                                event = "Свет ${if (isLightOn.value) "включен" else "выключен"}"
                            )
                        }
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLightOn.value) Color(0xFFF0F000) else Color.Gray
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
