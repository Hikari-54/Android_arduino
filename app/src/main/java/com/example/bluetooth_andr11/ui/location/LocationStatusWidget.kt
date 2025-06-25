package com.example.bluetooth_andr11.ui.location

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.location.LocationMode

@Composable
fun LocationStatusWidget(
    locationManager: EnhancedLocationManager,
    modifier: Modifier = Modifier,
    onModeChange: (LocationMode) -> Unit = {}
) {
    val coordinates by locationManager.locationCoordinates
    val accuracy by locationManager.locationAccuracy
    val source by locationManager.locationSource
    val isEnabled by locationManager.isLocationEnabled

    var showModeSelector by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showModeSelector = !showModeSelector },
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) Color(0xFF1B5E20) else Color(0xFF8B0000)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Основная информация
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📍 Местоположение",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = coordinates,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = source,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    if (accuracy > 0) {
                        Text(
                            text = "±${accuracy.toInt()}м",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Панель выбора режима
            if (showModeSelector) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.White.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Режим определения:",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(LocationMode.values().size) { index ->
                        val mode = LocationMode.values()[index]
                        LocationModeChip(
                            mode = mode,
                            onClick = {
                                onModeChange(mode)
                                showModeSelector = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationModeChip(
    mode: LocationMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (emoji, text, description) = when (mode) {
        LocationMode.HIGH_ACCURACY ->
            Triple("🎯", "Точно", "GPS + Network")

        LocationMode.BALANCED ->
            Triple("⚖️", "Сбалансированно", "Оптимально")

        LocationMode.LOW_POWER ->
            Triple("🔋", "Экономно", "Только Network")

        LocationMode.PASSIVE ->
            Triple("😴", "Пассивно", "От других приложений")

        LocationMode.GPS_ONLY ->
            Triple("🛰️", "GPS", "Только спутники")

        LocationMode.NETWORK_ONLY ->
            Triple("📶", "Network", "Wi-Fi + Сотовые")
    }

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                fontSize = 16.sp
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 8.sp
            )
        }
    }
}

@Composable
fun LocationDiagnostics(
    locationManager: EnhancedLocationManager,
    modifier: Modifier = Modifier
) {
    val status = remember { locationManager.getLocationStatus() }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2D2D)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔍 Диагностика местоположения",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            DiagnosticItem(
                label = "Разрешения",
                value = if (status.hasPermission) "✅ Предоставлены" else "❌ Отсутствуют",
                isGood = status.hasPermission
            )

            DiagnosticItem(
                label = "GPS",
                value = if (status.isGpsEnabled) "✅ Включен" else "❌ Выключен",
                isGood = status.isGpsEnabled
            )

            DiagnosticItem(
                label = "Network",
                value = if (status.isNetworkEnabled) "✅ Доступен" else "❌ Недоступен",
                isGood = status.isNetworkEnabled
            )

            DiagnosticItem(
                label = "Точность",
                value = "${status.lastUpdate.accuracy.toInt()} метров",
                isGood = status.lastUpdate.accuracy < 100f
            )

            DiagnosticItem(
                label = "Режим",
                value = status.currentMode.name,
                isGood = true
            )

            // Рекомендация
            val recommendedMode = locationManager.getRecommendedMode()
            if (recommendedMode != status.currentMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 Рекомендуется режим: ${recommendedMode.name}",
                    color = Color.Yellow,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiagnosticItem(
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = if (isGood) Color.Green else Color.Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}