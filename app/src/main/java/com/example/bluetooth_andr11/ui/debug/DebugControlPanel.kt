package com.example.bluetooth_andr11.ui.debug

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.simulation.ArduinoSimulator
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.location.LocationMode
import com.example.bluetooth_andr11.ui.location.LocationDiagnostics
import com.example.bluetooth_andr11.ui.location.LocationStatusWidget
import kotlinx.coroutines.delay

/**
 * 🔧 Панель отладки для разработчиков
 *
 * Функциональность:
 * - 📍 Управление GPS/Location режимами
 * - 🤖 Симуляция Arduino данных
 * - 📊 Диагностика местоположения
 * - 🧪 Тестовые сценарии
 *
 * ⚠️ Отображается только в DEBUG режиме
 */
@Composable
fun DebugControlPanel(
    bluetoothHelper: BluetoothHelper,
    locationManager: EnhancedLocationManager,
    modifier: Modifier = Modifier
) {
    // 🔒 Защита: скрываем в RELEASE
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current

    // 🤖 Состояния симуляции Arduino
    var isSimulationEnabled by remember { mutableStateOf(bluetoothHelper.isSimulationEnabled()) }
    var currentScenario by remember { mutableStateOf(bluetoothHelper.getCurrentScenario()) }
    var scenarioInfo by remember { mutableStateOf(bluetoothHelper.getScenarioInfo()) }

    // 📍 Состояния местоположения
    var showLocationDiagnostics by remember { mutableStateOf(false) }

    // ⏱️ Прогресс выполнения сценария
    var scenarioProgress by remember { mutableFloatStateOf(0f) }
    var timeRemaining by remember { mutableIntStateOf(0) }

    // 🔄 Автоматическое обновление прогресса сценария
    LaunchedEffect(currentScenario, isSimulationEnabled) {
        if (isSimulationEnabled && currentScenario != ArduinoSimulator.SimulationScenario.NORMAL) {
            val totalDuration = scenarioInfo.durationSeconds
            var elapsed = 0

            while (elapsed < totalDuration && isSimulationEnabled) {
                delay(1000)
                elapsed++
                scenarioProgress = elapsed.toFloat() / totalDuration
                timeRemaining = totalDuration - elapsed
            }

            // ✅ Автовозврат к нормальному режиму
            if (elapsed >= totalDuration) {
                currentScenario = ArduinoSimulator.SimulationScenario.NORMAL
                bluetoothHelper.setSimulationScenario(currentScenario)
                scenarioInfo = bluetoothHelper.getScenarioInfo()
                scenarioProgress = 0f
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 📋 Заголовок панели
            Text(
                text = "🔧 Панель отладки",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 📍 СЕКЦИЯ: Управление местоположением
            LocationControlSection(
                locationManager = locationManager,
                showDiagnostics = showLocationDiagnostics,
                onToggleDiagnostics = { showLocationDiagnostics = !showLocationDiagnostics }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🤖 СЕКЦИЯ: Симуляция Arduino
            ArduinoSimulationSection(
                bluetoothHelper = bluetoothHelper,
                isSimulationEnabled = isSimulationEnabled,
                currentScenario = currentScenario,
                scenarioInfo = scenarioInfo,
                scenarioProgress = scenarioProgress,
                timeRemaining = timeRemaining,
                onToggleSimulation = { enabled ->
                    isSimulationEnabled = enabled
                    bluetoothHelper.enableSimulationMode(enabled)
                    if (!enabled) {
                        scenarioProgress = 0f
                        timeRemaining = 0
                    }
                },
                onScenarioChange = { scenario ->
                    currentScenario = scenario
                    bluetoothHelper.setSimulationScenario(scenario)
                    scenarioInfo = bluetoothHelper.getScenarioInfo()
                    scenarioProgress = 0f
                }
            )
        }
    }
}

/**
 * 📍 Секция управления местоположением
 */
@Composable
private fun LocationControlSection(
    locationManager: EnhancedLocationManager,
    showDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit
) {
    val context = LocalContext.current

    // 🎯 Виджет статуса местоположения
    LocationStatusWidget(
        locationManager = locationManager,
        onModeChange = { mode: LocationMode ->
            locationManager.setLocationMode(mode)
            Toast.makeText(context, "Режим GPS: ${mode.name}", Toast.LENGTH_SHORT).show()
        }
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 🎛️ Кнопки управления GPS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GpsActionButton(
            emoji = "🎯",
            label = "GPS",
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f),
            onClick = {
                locationManager.forceLocationUpdate(LocationMode.HIGH_ACCURACY)
                Toast.makeText(context, "Принудительный GPS запрос", Toast.LENGTH_SHORT).show()
            }
        )

        GpsActionButton(
            emoji = "📶",
            label = "Network",
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f),
            onClick = {
                locationManager.forceLocationUpdate(LocationMode.NETWORK_ONLY)
                Toast.makeText(context, "Принудительный Network запрос", Toast.LENGTH_SHORT).show()
            }
        )

        GpsActionButton(
            emoji = "📡",
            label = "Wi-Fi",
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f),
            onClick = {
                locationManager.setLocationMode(LocationMode.LOW_POWER)
                locationManager.forceLocationUpdate(LocationMode.LOW_POWER)
                Toast.makeText(context, "Wi-Fi позиционирование", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 🔍 Дополнительные действия
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GpsActionButton(
            emoji = "🔍",
            label = "Диагностика",
            color = if (showDiagnostics) Color(0xFFFF9800) else Color(0xFF757575),
            modifier = Modifier.weight(1f),
            onClick = onToggleDiagnostics
        )

        GpsActionButton(
            emoji = "🔄",
            label = "Сброс",
            color = Color(0xFF9C27B0),
            modifier = Modifier.weight(1f),
            onClick = {
                locationManager.setLocationMode(LocationMode.BALANCED)
                Toast.makeText(context, "Сброс к BALANCED режиму", Toast.LENGTH_SHORT).show()
            }
        )

        GpsActionButton(
            emoji = "ℹ️",
            label = "Статус",
            color = Color(0xFF607D8B),
            modifier = Modifier.weight(1f),
            onClick = {
                val status = locationManager.getLocationStatus()
                val message = buildString {
                    append("GPS: ${if (status.isGpsEnabled) "✅" else "❌"}")
                    append(", Network: ${if (status.isNetworkEnabled) "✅" else "❌"}")
                    append(", Права: ${if (status.hasPermission) "✅" else "❌"}")
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        )
    }

    // 🔍 Раскрывающаяся диагностика
    if (showDiagnostics) {
        Spacer(modifier = Modifier.height(12.dp))
        LocationDiagnostics(locationManager = locationManager)
    }
}

/**
 * 🤖 Секция симуляции Arduino
 */
@Composable
private fun ArduinoSimulationSection(
    bluetoothHelper: BluetoothHelper,
    isSimulationEnabled: Boolean,
    currentScenario: ArduinoSimulator.SimulationScenario,
    scenarioInfo: BluetoothHelper.ScenarioInfo,
    scenarioProgress: Float,
    timeRemaining: Int,
    onToggleSimulation: (Boolean) -> Unit,
    onScenarioChange: (ArduinoSimulator.SimulationScenario) -> Unit
) {
    // 🔘 Переключатель режима симуляции
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Режим симуляции Arduino",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isSimulationEnabled) "✅ Активен" else "⭕ Выключен",
                color = if (isSimulationEnabled) Color.Green else Color.Gray,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = isSimulationEnabled,
            onCheckedChange = onToggleSimulation
        )
    }

    // 📊 Детали симуляции (только если включена)
    if (isSimulationEnabled) {
        Spacer(modifier = Modifier.height(16.dp))

        // 📋 Информация о текущем сценарии
        ScenarioInfoCard(
            scenarioInfo = scenarioInfo,
            scenarioProgress = scenarioProgress,
            timeRemaining = timeRemaining,
            currentScenario = currentScenario
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 🎭 Выбор сценариев
        ScenarioSelectionGrid(
            currentScenario = currentScenario,
            onScenarioChange = onScenarioChange
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 🎯 Компонент кнопки GPS действия
 */
@Composable
private fun GpsActionButton(
    emoji: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 16.sp)
            Text(label, fontSize = 10.sp)
        }
    }
}

/**
 * 📋 Карточка информации о сценарии
 */
@Composable
private fun ScenarioInfoCard(
    scenarioInfo: BluetoothHelper.ScenarioInfo,
    scenarioProgress: Float,
    timeRemaining: Int,
    currentScenario: ArduinoSimulator.SimulationScenario
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = scenarioInfo.icon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = scenarioInfo.name,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = scenarioInfo.description,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            // ⏱️ Прогресс-бар (только для временных сценариев)
            if (scenarioProgress > 0f && currentScenario != ArduinoSimulator.SimulationScenario.NORMAL) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = scenarioProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Cyan
                )
                Text(
                    text = "Осталось: ${timeRemaining}с",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * 🎭 Сетка выбора сценариев
 */
@Composable
private fun ScenarioSelectionGrid(
    currentScenario: ArduinoSimulator.SimulationScenario,
    onScenarioChange: (ArduinoSimulator.SimulationScenario) -> Unit
) {
    Text(
        text = "Быстрые сценарии:",
        color = Color.White,
        fontWeight = FontWeight.Medium
    )

    Spacer(modifier = Modifier.height(8.dp))

    // 🎪 Первый ряд сценариев
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScenarioButton(
            emoji = "⚪", text = "Норма",
            scenario = ArduinoSimulator.SimulationScenario.NORMAL,
            isActive = currentScenario == ArduinoSimulator.SimulationScenario.NORMAL,
            onScenarioChange = onScenarioChange,
            modifier = Modifier.weight(1f)
        )
        ScenarioButton(
            emoji = "🔥", text = "Нагрев",
            scenario = ArduinoSimulator.SimulationScenario.HEATING_CYCLE,
            isActive = currentScenario == ArduinoSimulator.SimulationScenario.HEATING_CYCLE,
            onScenarioChange = onScenarioChange,
            modifier = Modifier.weight(1f)
        )
        ScenarioButton(
            emoji = "🔋", text = "Разрядка",
            scenario = ArduinoSimulator.SimulationScenario.BATTERY_DRAIN,
            isActive = currentScenario == ArduinoSimulator.SimulationScenario.BATTERY_DRAIN,
            onScenarioChange = onScenarioChange,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 🎪 Второй ряд сценариев
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScenarioButton(
            emoji = "📳", text = "Тряска",
            scenario = ArduinoSimulator.SimulationScenario.STRONG_SHAKING,
            isActive = currentScenario == ArduinoSimulator.SimulationScenario.STRONG_SHAKING,
            onScenarioChange = onScenarioChange,
            modifier = Modifier.weight(1f)
        )
        ScenarioButton(
            emoji = "❄️", text = "Холод",
            scenario = ArduinoSimulator.SimulationScenario.COOLING_CYCLE,
            isActive = currentScenario == ArduinoSimulator.SimulationScenario.COOLING_CYCLE,
            onScenarioChange = onScenarioChange,
            modifier = Modifier.weight(1f)
        )
        ScenarioButton(
            emoji = "⚠️", text = "Ошибки",
            scenario = ArduinoSimulator.SimulationScenario.SENSOR_ERRORS,
            isActive = currentScenario == ArduinoSimulator.SimulationScenario.SENSOR_ERRORS,
            onScenarioChange = onScenarioChange,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 🎭 Кнопка выбора сценария
 */
@Composable
private fun ScenarioButton(
    emoji: String,
    text: String,
    scenario: ArduinoSimulator.SimulationScenario,
    isActive: Boolean,
    onScenarioChange: (ArduinoSimulator.SimulationScenario) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onScenarioChange(scenario) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) Color(0xFF4CAF50) else Color(0xFF424242)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 16.sp)
            Text(text, fontSize = 10.sp)
        }
    }
}