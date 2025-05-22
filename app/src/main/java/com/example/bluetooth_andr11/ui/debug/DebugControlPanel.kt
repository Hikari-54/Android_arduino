package com.example.bluetooth_andr11.ui.debug

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.ArduinoSimulator
import kotlinx.coroutines.delay

@Composable
fun DebugControlPanel(
    bluetoothHelper: BluetoothHelper,
    modifier: Modifier = Modifier
) {
    // 🔥 СИНХРОНИЗИРОВАННОЕ состояние с BluetoothHelper
    var isSimulationEnabled by remember { mutableStateOf(bluetoothHelper.isSimulationEnabled()) }
    var currentScenario by remember { mutableStateOf(bluetoothHelper.getCurrentScenario()) }
    var scenarioInfo by remember { mutableStateOf(bluetoothHelper.getScenarioInfo()) }

    // Остальные состояния
    var batteryLevel by remember { mutableIntStateOf(85) }
    var upperTemp by remember { mutableFloatStateOf(25.0f) }
    var lowerTemp by remember { mutableFloatStateOf(15.0f) }
    var shakeIntensity by remember { mutableFloatStateOf(0.1f) }

    // 🔥 НОВОЕ: Прогресс выполнения сценария
    var scenarioProgress by remember { mutableFloatStateOf(0f) }
    var timeRemaining by remember { mutableIntStateOf(0) }

    // 🔥 Обновление прогресса сценария
    LaunchedEffect(currentScenario, isSimulationEnabled) {
        if (isSimulationEnabled) {
            val totalDuration = scenarioInfo.durationSeconds
            var elapsed = 0

            while (elapsed < totalDuration && isSimulationEnabled) {
                delay(1000)
                elapsed++
                scenarioProgress = elapsed.toFloat() / totalDuration
                timeRemaining = totalDuration - elapsed
            }

            // Сценарий завершился - возвращаемся к нормальному режиму
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
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2D2D2D)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔧 Панель отладки Arduino",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🔥 УЛУЧШЕННЫЙ переключатель симуляции
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Режим симуляции",
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
                    onCheckedChange = { enabled ->
                        isSimulationEnabled = enabled
                        bluetoothHelper.enableSimulationMode(enabled)
                        if (!enabled) {
                            scenarioProgress = 0f
                            timeRemaining = 0
                        }
                    }
                )
            }

            if (isSimulationEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // 🔥 НОВОЕ: Информация о текущем сценарии
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = scenarioInfo.icon,
                                fontSize = 20.sp
                            )
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

                        // Прогресс-бар сценария
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

                Spacer(modifier = Modifier.height(16.dp))

                // 🔥 КОМПАКТНЫЕ сценарии тестирования
                Text(
                    text = "Быстрые сценарии:",
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Первый ряд
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScenarioButton(
                        emoji = "⚪",
                        text = "Норма",
                        isActive = currentScenario == ArduinoSimulator.SimulationScenario.NORMAL,
                        onClick = {
                            currentScenario = ArduinoSimulator.SimulationScenario.NORMAL
                            bluetoothHelper.setSimulationScenario(currentScenario)
                            scenarioInfo = bluetoothHelper.getScenarioInfo()
                            scenarioProgress = 0f
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ScenarioButton(
                        emoji = "🔋",
                        text = "Разрядка",
                        isActive = currentScenario == ArduinoSimulator.SimulationScenario.BATTERY_DRAIN,
                        onClick = {
                            currentScenario = ArduinoSimulator.SimulationScenario.BATTERY_DRAIN
                            bluetoothHelper.setSimulationScenario(currentScenario)
                            scenarioInfo = bluetoothHelper.getScenarioInfo()
                            scenarioProgress = 0f
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ScenarioButton(
                        emoji = "🔥",
                        text = "Нагрев",
                        isActive = currentScenario == ArduinoSimulator.SimulationScenario.HEATING_CYCLE,
                        onClick = {
                            currentScenario = ArduinoSimulator.SimulationScenario.HEATING_CYCLE
                            bluetoothHelper.setSimulationScenario(currentScenario)
                            scenarioInfo = bluetoothHelper.getScenarioInfo()
                            scenarioProgress = 0f
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Второй ряд
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScenarioButton(
                        emoji = "❄️",
                        text = "Холод",
                        isActive = currentScenario == ArduinoSimulator.SimulationScenario.COOLING_CYCLE,
                        onClick = {
                            currentScenario = ArduinoSimulator.SimulationScenario.COOLING_CYCLE
                            bluetoothHelper.setSimulationScenario(currentScenario)
                            scenarioInfo = bluetoothHelper.getScenarioInfo()
                            scenarioProgress = 0f
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ScenarioButton(
                        emoji = "📳",
                        text = "Тряска",
                        isActive = currentScenario == ArduinoSimulator.SimulationScenario.STRONG_SHAKING,
                        onClick = {
                            currentScenario = ArduinoSimulator.SimulationScenario.STRONG_SHAKING
                            bluetoothHelper.setSimulationScenario(currentScenario)
                            scenarioInfo = bluetoothHelper.getScenarioInfo()
                            scenarioProgress = 0f
                        },
                        modifier = Modifier.weight(1f)
                    )

                    ScenarioButton(
                        emoji = "⚠️",
                        text = "Ошибки",
                        isActive = currentScenario == ArduinoSimulator.SimulationScenario.SENSOR_ERRORS,
                        onClick = {
                            currentScenario = ArduinoSimulator.SimulationScenario.SENSOR_ERRORS
                            bluetoothHelper.setSimulationScenario(currentScenario)
                            scenarioInfo = bluetoothHelper.getScenarioInfo()
                            scenarioProgress = 0f
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Быстрые действия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            batteryLevel = 5
                            bluetoothHelper.setSimulationBattery(5)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                    ) {
                        Text("🔋5%", fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            upperTemp = 55f
                            bluetoothHelper.setSimulationTemperatures(55f, lowerTemp)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("🔥55°C", fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            bluetoothHelper.triggerSimulationShake(3.0f)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                    ) {
                        Text("📳Тряска", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ScenarioButton(
    emoji: String,
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) Color(0xFF4CAF50) else Color(0xFF424242)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 16.sp)
            Text(text, fontSize = 10.sp)
        }
    }
}