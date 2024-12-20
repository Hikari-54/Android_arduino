package com.example.bluetooth_andr11.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.R

@Composable
fun AppTopBar(
    batteryLevel: Int,
    isBluetoothConnected: Boolean,
    allPermissionsGranted: Boolean,
    onPermissionsClick: () -> Unit,
    onBluetoothClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Delivery bag",
            color = Color.White,
            fontSize = 20.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (isBluetoothConnected) R.drawable.bluetooth else R.drawable.bluetooth_off
                    ),
                    contentDescription = "Bluetooth",
                    tint = if (isBluetoothConnected) Color.Green else Color.Red
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
                    text = "$batteryLevel%",
                    color = Color.White
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
