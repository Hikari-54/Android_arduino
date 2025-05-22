package com.example.bluetooth_andr11.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.R

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
    // 🔥 НОВЫЙ параметр для обработки клика по заголовку
    onTitleClick: () -> Unit = {}
) {
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
                    // 🔥 ИСПОЛЬЗУЕМ НОВЫЙ СПОСОБ без deprecated rememberRipple
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // Убираем indication или используем LocalIndication
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(getBatteryIcon(batteryLevel)),
                    contentDescription = "Battery Level",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$batteryLevel%", color = Color.White
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
