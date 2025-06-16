package com.example.bluetooth_andr11.ui

import android.app.DatePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.navigation.NavController
import com.example.bluetooth_andr11.ui.auth.PasswordManager
import com.example.bluetooth_andr11.ui.auth.PasswordProtectedContent
import com.example.bluetooth_andr11.ui.map_log.MapModal
import com.example.bluetooth_andr11.utils.LogHelper.filterLogEntries
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun LogScreen(navController: NavController) {
    PasswordProtectedContent {
        LogScreenContent(navController = navController)
    }
}

@Composable
private fun LogScreenContent(navController: NavController) {
    val context = LocalContext.current
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var logEntries by remember { mutableStateOf(listOf<String>()) }
    var showMapModal by remember { mutableStateOf(false) }
    var selectedCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedEventTitle by remember { mutableStateOf("") }

    // Показ модального окна карты
    if (showMapModal && selectedCoordinates != null) {
        MapModal(
            context = context,
            coordinates = selectedCoordinates!!,
            onDismiss = { showMapModal = false },
            eventTitle = selectedEventTitle
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, start = 16.dp, end = 16.dp)
    ) {
        // Верхняя панель с кнопками
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(1f),
                colors = customButtonColors()
            ) {
                Text(text = "Назад")
            }

            Spacer(modifier = Modifier.padding(horizontal = 4.dp))

            // 🔥 ИСПРАВЛЕНО: Правильный выход с сбросом аутентификации
            Button(
                onClick = {
                    // Сбрасываем сессию аутентификации
                    PasswordManager.setSessionActive(context, false)
                    // Возвращаемся назад
                    navController.popBackStack()

                    Log.d("LogScreen", "🔒 Пользователь вышел из защищенной области")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B4513)
                )
            ) {
                Text(text = "🔒 Выйти", color = Color.White)
            }
        }

        // Фильтр логов
        LogFilterScreen { start, end ->
            startDate = start
            endDate = end
            val rawEntries = filterLogEntries(context, start, end)
            // Сортируем записи от новых к старым
            logEntries = sortLogEntriesByDateDescending(rawEntries)
            Log.d("LogScreen", "📊 Загружено и отсортировано ${logEntries.size} записей")
        }

        // Индикатор количества записей
        if (logEntries.isNotEmpty()) {
            Text(
                text = "📋 Найдено событий: ${logEntries.size}",
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
        }

        // Таблица с логами (отсортированными от новых к старым)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            items(logEntries) { entry ->
                val coordinates = parseCoordinatesFromLogEntry(entry)
                val eventInfo = parseEventFromLogEntry(entry)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .border(1.dp, Color.Gray)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Дата и время
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = eventInfo.date,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = eventInfo.time,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    // Событие
                    Text(
                        text = eventInfo.event,
                        modifier = Modifier
                            .weight(2f)
                            .padding(start = 8.dp),
                        fontSize = 13.sp
                    )

                    // Кнопка карты
                    Button(
                        onClick = {
                            if (coordinates != null) {
                                selectedCoordinates = coordinates
                                selectedEventTitle = eventInfo.event
                                showMapModal = true
                                Log.d("LogScreen", "🗺️ Открываем карту для: ${eventInfo.event}")
                            } else {
                                Toast.makeText(
                                    context,
                                    "Координаты недоступны для этого события",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = customButtonColors()
                    ) {
                        Text("Карта", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// Функция для сортировки логов от новых к старым
fun sortLogEntriesByDateDescending(logEntries: List<String>): List<String> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    return logEntries.sortedByDescending { entry ->
        try {
            // Извлекаем timestamp из начала строки
            val timestampPart = entry.substringBefore(" -").trim()
            val parsedDate = dateFormat.parse(timestampPart)

            if (parsedDate != null) {
                Log.d("LogScreen", "✅ Дата распарсена: $timestampPart -> ${parsedDate.time}")
                parsedDate.time // Возвращаем timestamp для сортировки
            } else {
                Log.w("LogScreen", "⚠️ Не удалось распарсить дату: $timestampPart")
                0L // Если не удалось распарсить, помещаем в конец
            }
        } catch (e: Exception) {
            Log.e("LogScreen", "❌ Ошибка парсинга даты в строке: $entry, ошибка: ${e.message}")
            0L // В случае ошибки помещаем в конец
        }
    }.also { sortedList ->
        if (sortedList.isNotEmpty()) {
            Log.d("LogScreen", "🔄 Сортировка завершена:")
            Log.d("LogScreen", "   📅 Самое новое: ${sortedList.first().substringBefore(" -")}")
            Log.d("LogScreen", "   📅 Самое старое: ${sortedList.last().substringBefore(" -")}")
        }
    }
}

// Функция для парсинга координат
fun parseCoordinatesFromLogEntry(logEntry: String): Pair<Double, Double>? {
    try {
        // Ищем координаты после символа @
        val coordinatesPart = logEntry.substringAfter("@", "").trim()

        if (coordinatesPart.isEmpty() || coordinatesPart.contains("Координаты недоступны") || coordinatesPart.contains(
                "Системное событие"
            )
        ) {
            return null
        }

        // Извлекаем числовую часть до скобки
        val coordsOnly = coordinatesPart.substringBefore("(").trim()

        // Надежный парсинг: Ищем все числа с плавающей точкой в строке
        val numberPattern = Regex("""(\d+[.,]\d+)""")
        val numbers = numberPattern.findAll(coordsOnly).map {
            it.value.replace(",", ".").toDoubleOrNull()
        }.filterNotNull().toList()

        if (numbers.size >= 2) {
            val latitude = numbers[0]
            val longitude = numbers[1]

            // Проверяем диапазоны
            if (latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                return Pair(latitude, longitude)
            }
        }

        return null

    } catch (e: Exception) {
        Log.e("LogScreen", "❌ Ошибка парсинга координат: ${e.message}")
        return null
    }
}

// Функция для парсинга информации о событии
data class EventInfo(
    val date: String,
    val time: String,
    val event: String
)

fun parseEventFromLogEntry(logEntry: String): EventInfo {
    val parts = logEntry.split(" - ", limit = 2)
    val timestamp = parts.getOrNull(0) ?: "Неизвестная дата"
    val eventWithCoords = parts.getOrNull(1) ?: "Неизвестное событие"

    // Извлекаем событие до символа @
    val event = eventWithCoords.substringBefore("@").trim().ifEmpty { "Неизвестное событие" }

    // Разделяем дату и время
    val dateParts = timestamp.split(" ")
    val date = dateParts.getOrNull(0)?.let { formatDateWithoutYear(it) } ?: ""
    val time = dateParts.getOrNull(1) ?: ""

    return EventInfo(date, time, event)
}

// Форматируем дату без года
fun formatDateWithoutYear(date: String): String {
    val parts = date.split("-")
    return if (parts.size >= 3) {
        "${parts[2]}.${parts[1]}"
    } else {
        date
    }
}

@Composable
fun LogFilterScreen(onFilterApplied: (String, String) -> Unit) {
    val calendar = Calendar.getInstance()
    val today = String.format(
        Locale.getDefault(),
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Top
        ) {
            DatePickerButton("Начальная дата", startDate) { selectedDate ->
                startDate = selectedDate
            }
            Spacer(modifier = Modifier.height(8.dp))
            DatePickerButton("Конечная дата", endDate) { selectedDate ->
                endDate = selectedDate
            }
        }

        Button(
            onClick = {
                if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
                    Log.d("LogScreen", "🔍 Фильтрация логов: $startDate - $endDate")
                    onFilterApplied(startDate, endDate)
                }
            },
            modifier = Modifier.align(Alignment.CenterVertically),
            colors = customButtonColors()
        ) {
            Text("Отфильтровать")
        }
    }
}

@Composable
fun DatePickerButton(label: String, date: String, onDateSelected: (String) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val year = date.substring(0, 4).toIntOrNull() ?: calendar.get(Calendar.YEAR)
    val month = date.substring(5, 7).toIntOrNull()?.minus(1) ?: calendar.get(Calendar.MONTH)
    val day = date.substring(8, 10).toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            val formattedDate = String.format(
                Locale.getDefault(),
                "%04d-%02d-%02d",
                selectedYear,
                selectedMonth + 1,
                selectedDayOfMonth
            )
            onDateSelected(formattedDate)
        },
        year, month, day
    )

    Button(
        onClick = { datePickerDialog.show() },
        colors = customButtonColors()
    ) {
        Text(text = if (date.isEmpty()) label else "Дата: $date")
    }
}

@Composable
fun customButtonColors() = ButtonDefaults.buttonColors(
    containerColor = Color(0xFF252525),
    contentColor = Color.White
)