package com.example.bluetooth_andr11.ui.control

import android.content.Context
import android.util.Log
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

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

        // Простые защищенные кнопки
        SimpleProtectedButtons(
            context = context,
            bluetoothHelper = bluetoothHelper,
            locationManager = locationManager,
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
private fun SimpleProtectedButtons(
    context: Context,
    bluetoothHelper: BluetoothHelper,
    locationManager: EnhancedLocationManager,
    isHeatOn: MutableState<Boolean>,
    isCoolOn: MutableState<Boolean>,
    isLightOn: MutableState<Boolean>
) {
    // ✅ ОБЩАЯ блокировка для ВСЕХ кнопок (синхронный канал)
    var lastAnyCommandTime by remember { mutableLongStateOf(0L) }

    val debounceTime = 1500L // ✅ Увеличил до 1.5сек для агрессивной отправки

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Кнопка "Нагрев"
        SimpleButton(
            iconRes = R.drawable.fire,
            label = "Нагрев",
            isActive = isHeatOn.value,
            activeColor = Color(0xFFFF4500),
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastAnyCommandTime < debounceTime) {
                    Log.w(
                        "ControlPanel",
                        "🚫 Нагрев заблокирован: канал занят (${now - lastAnyCommandTime}мс)"
                    )
                    Toast.makeText(context, "⏳ Канал занят, подождите...", Toast.LENGTH_SHORT)
                        .show()
                    return@SimpleButton
                }
                lastAnyCommandTime = now

                if (checkConnection(context, bluetoothHelper)) {
                    val command = if (isHeatOn.value) "h" else "H"
                    val newState = !isHeatOn.value

                    Log.d(
                        "ControlPanel",
                        "🔥 Нагрев: агрессивная отправка '$command' (${isHeatOn.value} -> $newState)"
                    )

                    // ✅ АГРЕССИВНАЯ ОТПРАВКА команды
                    sendAggressiveCommand(bluetoothHelper, command)

                    isHeatOn.value = newState

                    LogModule.logUserAction(
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager,
                        action = "Нагрев ${if (newState) "включен" else "выключен"}"
                    )
                }
            }
        )

        // Кнопка "Холод"
        SimpleButton(
            iconRes = R.drawable.snowflake,
            label = "Холод",
            isActive = isCoolOn.value,
            activeColor = Color(0xFF1E90FF),
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastAnyCommandTime < debounceTime) {
                    Log.w(
                        "ControlPanel",
                        "🚫 Холод заблокирован: канал занят (${now - lastAnyCommandTime}мс)"
                    )
                    Toast.makeText(context, "⏳ Канал занят, подождите...", Toast.LENGTH_SHORT)
                        .show()
                    return@SimpleButton
                }
                lastAnyCommandTime = now

                if (checkConnection(context, bluetoothHelper)) {
                    val command = if (isCoolOn.value) "c" else "C"
                    val newState = !isCoolOn.value

                    Log.d(
                        "ControlPanel",
                        "❄️ Холод: агрессивная отправка '$command' (${isCoolOn.value} -> $newState)"
                    )

                    // ✅ АГРЕССИВНАЯ ОТПРАВКА команды
                    sendAggressiveCommand(bluetoothHelper, command)

                    isCoolOn.value = newState

                    LogModule.logUserAction(
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager,
                        action = "Холод ${if (newState) "включен" else "выключен"}"
                    )
                }
            }
        )

        // Кнопка "Свет"
        SimpleButton(
            iconRes = R.drawable.light,
            label = "Свет",
            isActive = isLightOn.value,
            activeColor = Color(0xFFF0F000),
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastAnyCommandTime < debounceTime) {
                    Log.w(
                        "ControlPanel",
                        "🚫 Свет заблокирован: канал занят (${now - lastAnyCommandTime}мс)"
                    )
                    Toast.makeText(context, "⏳ Канал занят, подождите...", Toast.LENGTH_SHORT)
                        .show()
                    return@SimpleButton
                }
                lastAnyCommandTime = now

                if (checkConnection(context, bluetoothHelper)) {
                    val command = if (isLightOn.value) "l" else "L"
                    val newState = !isLightOn.value

                    Log.d(
                        "ControlPanel",
                        "💡 Свет: агрессивная отправка '$command' (${isLightOn.value} -> $newState)"
                    )

                    // ✅ АГРЕССИВНАЯ ОТПРАВКА команды
                    sendAggressiveCommand(bluetoothHelper, command)

                    isLightOn.value = newState

                    LogModule.logUserAction(
                        context = context,
                        bluetoothHelper = bluetoothHelper,
                        locationManager = locationManager,
                        action = "Свет ${if (newState) "включен" else "выключен"}"
                    )
                }
            }
        )
    }
}

// ✅ АГРЕССИВНАЯ функция для множественной отправки команд
private fun sendAggressiveCommand(
    bluetoothHelper: BluetoothHelper,
    command: String
) {
    Log.d("ControlPanel", "🔥 АГРЕССИВНАЯ отправка команды '$command'")

    // ✅ Первая серия: 3 команды подряд (для прочистки буфера)
    bluetoothHelper.sendCommand(command)
    bluetoothHelper.sendCommand(command)
    bluetoothHelper.sendCommand(command)
    Log.d("ControlPanel", "📤 Серия 1/3: команда '$command' отправлена 3 раза подряд")

    // ✅ Вторая серия: через 100мс еще 2 команды
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        kotlinx.coroutines.delay(100L)
        bluetoothHelper.sendCommand(command)
        bluetoothHelper.sendCommand(command)
        Log.d("ControlPanel", "📤 Серия 2/3: команда '$command' отправлена +2 раза через 100мс")

        // ✅ Третья серия: через еще 200мс финальная команда
        kotlinx.coroutines.delay(200L)
        bluetoothHelper.sendCommand(command)
        Log.d("ControlPanel", "📤 Серия 3/3: финальная команда '$command' через 300мс")
    }
}

@Composable
private fun SimpleButton(
    iconRes: Int,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {
                Log.d("ControlPanel", "🔘 Клик по кнопке: $label")
                onClick()
            },
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
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 16.sp
        )
    }
}

private fun checkConnection(context: Context, bluetoothHelper: BluetoothHelper): Boolean {
    return if (bluetoothHelper.isDeviceConnected) {
        true
    } else {
        Log.w("ControlPanel", "❌ Bluetooth не подключен")
        Toast.makeText(context, "❌ Подключите Bluetooth устройство", Toast.LENGTH_SHORT).show()
        false
    }
}