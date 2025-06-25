package com.example.bluetooth_andr11.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.R
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import kotlinx.coroutines.delay

@Composable
fun AppTopBar(
    batteryLevel: Int,
    isBluetoothEnabled: Boolean,
    isDeviceConnected: Boolean,
    allPermissionsGranted: Boolean,
    onPermissionsClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    showDebugButton: Boolean = false,
    onTitleClick: () -> Unit = {},
    bluetoothHelper: BluetoothHelper? = null
) {
    // 🔥 ЛОГИКА СКРЫТИЯ: скрываем Bluetooth если DEBUG + симуляция включена
    val shouldShowBluetoothButton = remember(bluetoothHelper) {
        if (BuildConfig.DEBUG && bluetoothHelper?.isSimulationEnabled() == true) {
            false // Скрываем в debug режиме с активной симуляцией
        } else {
            true // Показываем в остальных случаях
        }
    }

    // 🔋 Состояние tooltip батареи
    var showBatteryTooltip by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 📱 Основная панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252525))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = onTitleClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delivery bag",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Кнопка отладки (если включена)
                if (showDebugButton) {
                    IconButton(onClick = onDebugClick) {
                        Text(
                            text = "🔧", fontSize = 20.sp, color = Color.Yellow
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Кнопка разрешений (всегда показываем)
                IconButton(onClick = onPermissionsClick) {
                    Icon(
                        painter = painterResource(
                            if (allPermissionsGranted) R.drawable.lock_check else R.drawable.key_alert
                        ),
                        contentDescription = "Permissions",
                        tint = if (allPermissionsGranted) Color.Green else Color.Red
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 🔥 УСЛОВНОЕ ОТОБРАЖЕНИЕ кнопки Bluetooth
                if (shouldShowBluetoothButton) {
                    IconButton(onClick = onBluetoothClick) {
                        Icon(
                            painter = painterResource(
                                if (isBluetoothEnabled && isDeviceConnected) R.drawable.bluetooth else R.drawable.bluetooth_off
                            ),
                            contentDescription = "Bluetooth",
                            tint = if (isBluetoothEnabled && isDeviceConnected) Color.Green else Color.Red
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // 🔋 Батарея (обычная, без встроенного tooltip)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        showBatteryTooltip = !showBatteryTooltip
                    }
                ) {
                    Icon(
                        painter = painterResource(getBatteryIcon(batteryLevel)),
                        contentDescription = "Smart Bag Battery",
                        tint = when {
                            batteryLevel > 50 -> Color.Green
                            batteryLevel > 20 -> Color.Yellow
                            else -> Color.Red
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$batteryLevel%",
                        color = Color.White,
                        fontWeight = if (batteryLevel < 20) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 💬 Popup tooltip - абсолютно позиционируется поверх всего
        if (showBatteryTooltip) {
            BatteryTooltipPopup(
                batteryLevel = batteryLevel,
                isDeviceConnected = isDeviceConnected, // 🔥 ДОБАВЛЕНО: передаем реальный статус
                bluetoothHelper = bluetoothHelper, // 🔥 ДОБАВЛЕНО: передаем helper для проверки симуляции
                onDismiss = { showBatteryTooltip = false }
            )
        }

        // 🔄 Автоскрытие tooltip
        LaunchedEffect(showBatteryTooltip) {
            if (showBatteryTooltip) {
                delay(3000)
                showBatteryTooltip = false
            }
        }
    }
}

/**
 * 💬 Popup tooltip для батареи с актуальным статусом подключения
 */
@Composable
private fun BatteryTooltipPopup(
    batteryLevel: Int,
    isDeviceConnected: Boolean, // 🔥 НОВЫЙ параметр
    bluetoothHelper: BluetoothHelper?, // 🔥 НОВЫЙ параметр
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current

    // 🔥 ОПРЕДЕЛЯЕМ РЕАЛЬНЫЙ СТАТУС ПОДКЛЮЧЕНИЯ
    val actualConnectionStatus = remember(isDeviceConnected, bluetoothHelper) {
        when {
            bluetoothHelper?.isSimulationEnabled() == true -> "🤖 Режим симуляции"
            isDeviceConnected -> "📡 Подключена"
            else -> "📵 Отключена"
        }
    }

    val connectionColor = remember(isDeviceConnected, bluetoothHelper) {
        when {
            bluetoothHelper?.isSimulationEnabled() == true -> Color.Cyan
            isDeviceConnected -> Color.Green
            else -> Color.Red
        }
    }

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(
            x = with(density) { (-20).dp.roundToPx() },
            y = with(density) { 60.dp.roundToPx() }
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier.width(180.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Умная сумка",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Заряд батареи: $batteryLevel%",
                    color = Color.White,
                    fontSize = 12.sp
                )

                // 📊 Прогресс-бар
                LinearProgressIndicator(
                    progress = batteryLevel / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .padding(vertical = 4.dp),
                    color = when {
                        batteryLevel > 50 -> Color.Green
                        batteryLevel > 20 -> Color.Yellow
                        else -> Color.Red
                    }
                )

                // ⚠️ Предупреждение при низком заряде
                if (batteryLevel < 20) {
                    Text(
                        text = "⚠️ Низкий заряд!",
                        color = Color.Red,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 💡 АКТУАЛЬНЫЙ статус подключения
                Text(
                    text = actualConnectionStatus, // 🔥 ИСПРАВЛЕНО: показываем реальный статус
                    color = connectionColor, // 🔥 ИСПРАВЛЕНО: правильный цвет
                    fontSize = 10.sp
                )
            }
        }
    }
}

// Функция для определения ресурса батареи
fun getBatteryIcon(batteryLevel: Int): Int {
    return when {
        batteryLevel <= 10 -> R.drawable.battery_10
        batteryLevel <= 30 -> R.drawable.battery_30
        batteryLevel <= 50 -> R.drawable.battery_50
        batteryLevel <= 70 -> R.drawable.battery_70
        else -> R.drawable.battery_90
    }
}