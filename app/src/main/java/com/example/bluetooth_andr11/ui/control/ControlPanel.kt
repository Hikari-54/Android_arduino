package com.example.bluetooth_andr11.ui.control

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.R
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    SimpleProtectedButtons(
        context = context,
        bluetoothHelper = bluetoothHelper,
        locationManager = locationManager,
        isHeatOn = isHeatOn,
        isCoolOn = isCoolOn,
        isLightOn = isLightOn
    )
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
    var lastAnyCommandTime by remember { mutableLongStateOf(0L) }
    val debounceTime = 1500L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp), // Выравниваем с карточками
        horizontalArrangement = Arrangement.spacedBy(12.dp) // Как у карточек
    ) {
        // Кнопка "Нагрев"
        ControlCard(
            label = "Нагрев",
            iconRes = R.drawable.fire,
            isActive = isHeatOn.value,
            activeColor = Color(0xFFFF4500),
            modifier = Modifier.weight(1f),
            onClick = {
                handleButtonClick(
                    context = context,
                    bluetoothHelper = bluetoothHelper,
                    locationManager = locationManager,
                    lastCommandTime = lastAnyCommandTime,
                    debounceTime = debounceTime,
                    onTimeUpdate = { lastAnyCommandTime = it },
                    currentState = isHeatOn.value,
                    onStateChange = { isHeatOn.value = it },
                    command = if (isHeatOn.value) "h" else "H",
                    actionName = "Нагрев"
                )
            }
        )

        // Кнопка "Холод"
        ControlCard(
            label = "Холод",
            iconRes = R.drawable.snowflake,
            isActive = isCoolOn.value,
            activeColor = Color(0xFF1E90FF),
            modifier = Modifier.weight(1f),
            onClick = {
                handleButtonClick(
                    context = context,
                    bluetoothHelper = bluetoothHelper,
                    locationManager = locationManager,
                    lastCommandTime = lastAnyCommandTime,
                    debounceTime = debounceTime,
                    onTimeUpdate = { lastAnyCommandTime = it },
                    currentState = isCoolOn.value,
                    onStateChange = { isCoolOn.value = it },
                    command = if (isCoolOn.value) "c" else "C",
                    actionName = "Холод"
                )
            }
        )

        // Кнопка "Свет"
        ControlCard(
            label = "Свет",
            iconRes = R.drawable.light,
            isActive = isLightOn.value,
            activeColor = Color(0xFFFFC107), // Более подходящий желтый
            modifier = Modifier.weight(1f),
            onClick = {
                handleButtonClick(
                    context = context,
                    bluetoothHelper = bluetoothHelper,
                    locationManager = locationManager,
                    lastCommandTime = lastAnyCommandTime,
                    debounceTime = debounceTime,
                    onTimeUpdate = { lastAnyCommandTime = it },
                    currentState = isLightOn.value,
                    onStateChange = { isLightOn.value = it },
                    command = if (isLightOn.value) "l" else "L",
                    actionName = "Свет"
                )
            }
        )
    }
}

@Composable
private fun ControlCard(
    label: String,
    iconRes: Int,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor else Color.White // 🔥 Полная заливка цветом
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), // 🔥 Убираем тень
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) activeColor else Color(0xFFE0E0E0) // Тонкая рамка
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Иконка
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (isActive) Color.White else Color(0xFF666666), // 🔥 Белая иконка при активности
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Текст
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color(0xFF666666) // 🔥 Белый текст при активности
            )
        }
    }
}

// Универсальная функция обработки нажатий кнопок
private fun handleButtonClick(
    context: Context,
    bluetoothHelper: BluetoothHelper,
    locationManager: EnhancedLocationManager,
    lastCommandTime: Long,
    debounceTime: Long,
    onTimeUpdate: (Long) -> Unit,
    currentState: Boolean,
    onStateChange: (Boolean) -> Unit,
    command: String,
    actionName: String
) {
    val now = System.currentTimeMillis()
    if (now - lastCommandTime < debounceTime) {
        Toast.makeText(context, "⏳ Канал занят, подождите...", Toast.LENGTH_SHORT).show()
        return
    }
    onTimeUpdate(now)

    if (!bluetoothHelper.isDeviceConnected) {
        Toast.makeText(context, "❌ Подключите Bluetooth устройство", Toast.LENGTH_SHORT).show()
        return
    }

    val newState = !currentState
    sendAggressiveCommand(bluetoothHelper, command)
    onStateChange(newState)

    LogModule.logUserAction(
        context = context,
        bluetoothHelper = bluetoothHelper,
        locationManager = locationManager,
        action = "$actionName ${if (newState) "включен" else "выключен"}"
    )
}

// Агрессивная отправка команд
private fun sendAggressiveCommand(bluetoothHelper: BluetoothHelper, command: String) {
    repeat(3) { bluetoothHelper.sendCommand(command) }

    CoroutineScope(Dispatchers.IO).launch {
        delay(100L)
        repeat(2) { bluetoothHelper.sendCommand(command) }
        delay(200L)
        bluetoothHelper.sendCommand(command)
    }
}

@Composable
fun CardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF252525), // Темная как в исходном стиле
    textColor: Color = Color.White,
    fontSize: Int = 16
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // Стандартная высота кнопки
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
