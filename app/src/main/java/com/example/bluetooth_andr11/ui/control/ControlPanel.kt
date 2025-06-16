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
import androidx.compose.material3.Icon
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
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule

@Composable
fun ControlPanel(
    onCommandSend: (String) -> Unit,
    temp1: String,
    temp2: String,
    hallState: String,
    acc: String,
    isHeatOn: MutableState<Boolean>,
    isCoolOn: MutableState<Boolean>,
    isLightOn: MutableState<Boolean>,
    context: Context,
    modifier: Modifier = Modifier,
    bluetoothHelper: BluetoothHelper,
    locationManager: EnhancedLocationManager
) {
    Column(
        modifier = modifier.padding(10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        // Отображение данных устройства
        DeviceStatusDisplay(
            temp1 = temp1,
            temp2 = temp2,
            hallState = hallState,
            acc = acc
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Панель управления
        ControlButtons(
            context = context,
            bluetoothHelper = bluetoothHelper,
            locationManager = locationManager,
            onCommandSend = onCommandSend,
            isHeatOn = isHeatOn,
            isCoolOn = isCoolOn,
            isLightOn = isLightOn
        )
    }
}

@Composable
private fun DeviceStatusDisplay(
    temp1: String,
    temp2: String,
    hallState: String,
    acc: String
) {
    Column {
        StatusItem(label = "Температура верхний отсек", value = "$temp1°C")
        StatusItem(label = "Температура нижний отсек", value = "$temp2°C")
        StatusItem(label = "Статус", value = hallState)
        StatusItem(label = "Уровень тряски", value = acc)
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Text(
        text = "$label: $value",
        modifier = Modifier.padding(4.dp),
        fontSize = 18.sp
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ControlButtons(
    context: Context,
    bluetoothHelper: BluetoothHelper,
    locationManager: EnhancedLocationManager,
    onCommandSend: (String) -> Unit,
    isHeatOn: MutableState<Boolean>,
    isCoolOn: MutableState<Boolean>,
    isLightOn: MutableState<Boolean>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 🔥 ОБНОВЛЕННАЯ кнопка "Нагрев"
        ControlButton(
            iconRes = R.drawable.fire,
            label = "Нагрев",
            isActive = isHeatOn.value,
            activeColor = Color(0xFFFF4500),
            onClick = {
                if (checkBluetoothConnection(context, bluetoothHelper)) {
                    isHeatOn.value = !isHeatOn.value
                    onCommandSend(if (isHeatOn.value) "H" else "h")

                    // 🔥 ИЗМЕНЕНО: Используем logUserAction для действий пользователя
                    LogModule.logUserAction(
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager,
                        action = "Нагрев ${if (isHeatOn.value) "включен" else "выключен"}"
                    )
                }
            }
        )

        // 🔥 ОБНОВЛЕННАЯ кнопка "Холод"
        ControlButton(
            iconRes = R.drawable.snowflake,
            label = "Холод",
            isActive = isCoolOn.value,
            activeColor = Color(0xFF1E90FF),
            onClick = {
                if (checkBluetoothConnection(context, bluetoothHelper)) {
                    isCoolOn.value = !isCoolOn.value
                    onCommandSend(if (isCoolOn.value) "C" else "c")

                    // 🔥 ИЗМЕНЕНО: Используем logUserAction для действий пользователя
                    LogModule.logUserAction(
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager,
                        action = "Холод ${if (isCoolOn.value) "включен" else "выключен"}"
                    )
                }
            }
        )

        // 🔥 ОБНОВЛЕННАЯ кнопка "Свет"
        ControlButton(
            iconRes = R.drawable.light,
            label = "Свет",
            isActive = isLightOn.value,
            activeColor = Color(0xFFF0F000),
            onClick = {
                if (checkBluetoothConnection(context, bluetoothHelper)) {
                    isLightOn.value = !isLightOn.value
                    onCommandSend(if (isLightOn.value) "L" else "l")

                    // 🔥 ИЗМЕНЕНО: Используем logUserAction для действий пользователя
                    LogModule.logUserAction(
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager,
                        action = "Свет ${if (isLightOn.value) "включен" else "выключен"}"
                    )
                }
            }
        )
    }
}

@Composable
private fun ControlButton(
    iconRes: Int,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isActive) activeColor else Color.Gray
            ),
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color.White
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun checkBluetoothConnection(context: Context, bluetoothHelper: BluetoothHelper): Boolean {
    return if (bluetoothHelper.isDeviceConnected) {
        true
    } else {
        Toast.makeText(context, "Bluetooth не подключен", Toast.LENGTH_SHORT).show()
        false
    }
}