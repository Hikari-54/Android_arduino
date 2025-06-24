package com.example.bluetooth_andr11.ui.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeviceStatusDisplay(
    temp1: String,
    temp2: String,
    hallState: String,
    acc: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Первый ряд
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Верхний отсек",
                value = "$temp1°C",
                color = getTemperatureColor(temp1),
                subtitle = "Горячая зона",
                modifier = Modifier.weight(1f)
            )

            StatusCard(
                title = "Нижний отсек",
                value = "$temp2°C",
                color = getTemperatureColor(temp2),
                subtitle = "Холодная зона",
                modifier = Modifier.weight(1f)
            )
        }

        // Второй ряд
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Статус сумки",
                value = if (hallState == "Закрыт") "Закрыта" else "Открыта",
                color = if (hallState == "Закрыт") Color(0xFF4CAF50) else Color(0xFFD32F2F),
                subtitle = "Датчик Холла",
                modifier = Modifier.weight(1f)
            )

            MovementStatusCard(
                title = "Движение",
                acc = acc,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MovementStatusCard(
    title: String,
    acc: String,
    modifier: Modifier = Modifier
) {
    val shakeStatus = getShakeStatus(acc)
    val shakeDetails = getShakeDetails(acc)
    val shakeColor = getShakeColor(acc)

    Card(
        modifier = modifier.height(110.dp), // Убираем fillMaxWidth, используем modifier
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )

            Text(
                text = shakeStatus,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = shakeColor
            )

            if (shakeDetails.isNotEmpty()) {
                Text(
                    text = shakeDetails,
                    fontSize = 14.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    color: Color,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color(0xFF666666),
                fontWeight = FontWeight.Medium
            )

            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

// Утилитарные функции
fun getShakeStatus(acc: String): String {
    return try {
        val shakeLevel = extractShakeLevel(acc)
        when {
            shakeLevel > 2.0 -> "Экстрим"
            shakeLevel > 1.0 -> "Сильная"
            shakeLevel > 0.5 -> "Умеренная"
            shakeLevel > 0.1 -> "Слабая"
            else -> "Покой"
        }
    } catch (e: Exception) {
        "Покой"
    }
}

fun getShakeDetails(acc: String): String {
    return try {
        val shakeLevel = extractShakeLevel(acc)
        if (shakeLevel > 0) {
            "±${String.format("%.2f", shakeLevel)}g"
        } else {
            val regex = Regex("([0-9]+\\.?[0-9]*)")
            val match = regex.find(acc)
            val value = match?.value?.toFloatOrNull() ?: 0.0f
            "±${String.format("%.2f", value)}g"
        }
    } catch (e: Exception) {
        ""
    }
}

fun getTemperatureColor(temp: String): Color {
    val tempFloat = temp.replace("°C", "").toFloatOrNull() ?: return Color(0xFF757575)
    return when {
        tempFloat > 40 -> Color(0xFFD32F2F)
        tempFloat > 30 -> Color(0xFFFF9800)
        tempFloat < 10 -> Color(0xFF1976D2)
        else -> Color(0xFF388E3C)
    }
}

fun getShakeColor(acc: String): Color {
    return try {
        val shakeLevel = extractShakeLevel(acc)
        when {
            shakeLevel > 2.0 -> Color(0xFFD32F2F)
            shakeLevel > 1.0 -> Color(0xFFFF5722)
            shakeLevel > 0.5 -> Color(0xFFFF9800)
            shakeLevel > 0.1 -> Color(0xFFFFC107)
            else -> Color(0xFF4CAF50)
        }
    } catch (e: Exception) {
        Color(0xFF4CAF50)
    }
}

fun extractShakeLevel(acc: String): Float {
    return try {
        val regex = Regex("\\(([0-9.,]+)\\)")
        val match = regex.find(acc)
        val value = match?.groupValues?.get(1)?.replace(",", ".")?.toFloatOrNull()
        value ?: 0.0f
    } catch (e: Exception) {
        0.0f
    }
}