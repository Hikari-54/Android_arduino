package com.example.bluetooth_andr11.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.utils.LogHelper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LoggingDiagnosticScreen(
    bluetoothHelper: BluetoothHelper,
    enhancedLocationManager: EnhancedLocationManager,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnosticResult by remember { mutableStateOf("Нажмите 'Диагностика' для проверки") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔍 Диагностика логирования",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                diagnosticResult = performCompleteDiagnostic(
                                    context,
                                    bluetoothHelper,
                                    enhancedLocationManager
                                )
                                isLoading = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        ),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "🔄" else "🔍 Диагностика")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                createTestLogs(context, bluetoothHelper, enhancedLocationManager)
                                diagnosticResult =
                                    "✅ Тестовые логи созданы!\n\nПроверьте результат через 'Диагностика'"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("🧪 Тест")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val cleared = LogHelper.clearOldLogs(context, 0) // Очистить все
                                diagnosticResult = if (cleared > 0) {
                                    "🗑️ Удалено $cleared записей"
                                } else {
                                    "🗑️ Нет записей для удаления"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5722)
                        )
                    ) {
                        Text("🗑️ Очистить")
                    }

                    Button(
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF757575)
                        )
                    ) {
                        Text("❌ Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Результаты диагностики
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = diagnosticResult,
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFFE0E0E0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

// 🔥 Функция полной диагностики
private suspend fun performCompleteDiagnostic(
    context: android.content.Context,
    bluetoothHelper: BluetoothHelper,
    enhancedLocationManager: EnhancedLocationManager
): String = buildString {
    appendLine("=== ДИАГНОСТИКА ЛОГИРОВАНИЯ ===")
    appendLine("Время: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}")
    appendLine()

    // 1. Проверка файловой системы
    appendLine("1️⃣ ФАЙЛОВАЯ СИСТЕМА:")
    val logDir = File(context.getExternalFilesDir(null), "logs")
    val logFile = File(logDir, "events_log.txt")

    appendLine("   📁 Папка логов: ${if (logDir.exists()) "✅ Существует" else "❌ Не существует"}")
    appendLine("   📄 Файл логов: ${if (logFile.exists()) "✅ Существует" else "❌ Не существует"}")

    if (logFile.exists()) {
        appendLine("   📊 Размер файла: ${logFile.length()} байт")
        appendLine("   🔒 Права чтения: ${if (logFile.canRead()) "✅" else "❌"}")
        appendLine("   ✏️ Права записи: ${if (logFile.canWrite()) "✅" else "❌"}")

        try {
            val lines = logFile.readLines()
            appendLine("   📝 Количество строк: ${lines.size}")
            if (lines.isNotEmpty()) {
                appendLine("   🕐 Последняя запись: ${lines.lastOrNull()?.take(50)}...")
            }
        } catch (e: Exception) {
            appendLine("   ❌ Ошибка чтения: ${e.message}")
        }
    }
    appendLine()

    // 2. Проверка состояния компонентов
    appendLine("2️⃣ СОСТОЯНИЕ КОМПОНЕНТОВ:")
    appendLine("   📱 Bluetooth подключен: ${if (bluetoothHelper.isDeviceConnected) "✅" else "❌"}")
    appendLine("   📍 GPS доступен: ${if (enhancedLocationManager.isLocationAvailable()) "✅" else "❌"}")

    val locationInfo = enhancedLocationManager.getLocationInfo()
    appendLine("   🌍 Координаты: ${locationInfo.coordinates}")
    appendLine("   🎯 Точность: ±${locationInfo.accuracy.toInt()}м")
    appendLine("   📡 Источник: ${locationInfo.source}")
    appendLine()

    // 3. Тест записи логов
    appendLine("3️⃣ ТЕСТ ЗАПИСИ:")
    try {
        // Простой лог
        LogModule.logEvent(context, "ДИАГНОСТИКА: Простой тест записи")
        appendLine("   ✅ Простая запись: Успешно")

        // Лог с геолокацией
        LogModule.logEventWithLocation(
            context,
            bluetoothHelper,
            enhancedLocationManager,
            "ДИАГНОСТИКА: Тест с геолокацией"
        )
        appendLine("   ✅ Запись с геолокацией: Успешно")

        // Критический лог
        LogModule.logCriticalEvent(
            context,
            bluetoothHelper,
            enhancedLocationManager,
            "ДИАГНОСТИКА: Критический тест"
        )
        appendLine("   ✅ Критическая запись: Успешно")

    } catch (e: Exception) {
        appendLine("   ❌ Ошибка записи: ${e.message}")
    }
    appendLine()

    // 4. Проверка чтения логов
    appendLine("4️⃣ ТЕСТ ЧТЕНИЯ:")
    try {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
        val filteredLogs = LogHelper.filterLogEntries(context, today, today)
        appendLine("   ✅ Чтение логов: Найдено ${filteredLogs.size} записей за сегодня")

        // Показываем последние 3 записи
        if (filteredLogs.isNotEmpty()) {
            appendLine("   📋 Последние записи:")
            filteredLogs.takeLast(3).forEach { line ->
                appendLine("     • ${line.take(80)}...")
            }
        }
    } catch (e: Exception) {
        appendLine("   ❌ Ошибка чтения: ${e.message}")
    }
    appendLine()

    // 5. Статистика
    appendLine("5️⃣ СТАТИСТИКА:")
    try {
        val stats = LogModule.getLogStatistics(context)
        appendLine("   📊 Всего событий: ${stats.totalEvents}")
        appendLine("   🚨 Критических: ${stats.criticalEvents}")
        appendLine("   🌍 GPS событий: ${stats.gpsEvents}")
        appendLine("   🕐 Последнее: ${stats.lastEventTime}")
    } catch (e: Exception) {
        appendLine("   ❌ Ошибка статистики: ${e.message}")
    }
    appendLine()

    appendLine("=== ДИАГНОСТИКА ЗАВЕРШЕНА ===")
}

// 🔥 Функция создания тестовых логов
private suspend fun createTestLogs(
    context: android.content.Context,
    bluetoothHelper: BluetoothHelper,
    enhancedLocationManager: EnhancedLocationManager
) {
    val testEvents = listOf(
        "ТЕСТ: Включение нагрева",
        "ТЕСТ: Сильная тряска (2.5)",
        "ТЕСТ: Сумка открыта",
        "ТЕСТ: Низкий заряд батареи (<25%)",
        "ТЕСТ: Температура достигла 50°C"
    )

    testEvents.forEach { event ->
        LogModule.logEventWithLocation(
            context,
            bluetoothHelper,
            enhancedLocationManager,
            event
        )
        kotlinx.coroutines.delay(100) // Небольшая задержка между записями
    }

    // Критический тест
    LogModule.logCriticalEvent(
        context,
        bluetoothHelper,
        enhancedLocationManager,
        "ТЕСТ: Критическая ошибка системы"
    )
}