package com.example.bluetooth_andr11.ui

import android.app.DatePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.bluetooth_andr11.ui.auth.PasswordManager
import com.example.bluetooth_andr11.ui.auth.PasswordProtectedContent
import com.example.bluetooth_andr11.ui.control.CardButton
import com.example.bluetooth_andr11.ui.map_log.MapModal
import com.example.bluetooth_andr11.utils.LogHelper.filterLogEntries
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardButton(
                text = "◀ Назад",
                onClick = { navController.popBackStack() },
                modifier = Modifier.weight(2f),
                fontSize = 14
            )

            CardButton(
                text = "🔒 Выйти",
                onClick = {
                    PasswordManager.setSessionActive(context, false)
                    navController.popBackStack()
                },
                backgroundColor = Color(0xFF8B4513),
                modifier = Modifier.weight(1f),
                fontSize = 14
            )
        }

        // Фильтр логов
        LogFilterScreen { start, end ->
            startDate = start
            endDate = end
            scope.launch {
                val rawEntries = filterLogEntries(context, start, end)
                logEntries = sortLogEntriesByDateDescending(rawEntries)
                Log.d("LogScreen", "📊 Загружено и отсортировано ${logEntries.size} записей")
            }
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

        // 🔥 УЛУЧШЕННАЯ таблица с логами
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp) // Отступы между строками
        ) {
            items(logEntries) { entry ->
                val coordinates = parseCoordinatesFromLogEntry(entry)
                val eventInfo = parseEventFromLogEntry(entry)

                // 🔥 НОВАЯ красивая строка таблицы
                LogTableRow(
                    eventInfo = eventInfo,
                    coordinates = coordinates,
                    onMapClick = { coords, title ->
                        selectedCoordinates = coords
                        selectedEventTitle = title
                        showMapModal = true
                    },
                    onNoCoordinates = {
                        Toast.makeText(
                            context,
                            "Координаты недоступны для этого события",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}

// 🔥 НОВЫЙ компонент строки таблицы
@Composable
private fun LogTableRow(
    eventInfo: EventInfo,
    coordinates: Pair<Double, Double>?,
    onMapClick: (Pair<Double, Double>, String) -> Unit,
    onNoCoordinates: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 🔥 ДАТА - компактная колонка (15% ширины)
        DateTimeColumn(
            date = eventInfo.date,
            time = eventInfo.time,
            modifier = Modifier.width(60.dp)
        )

        // 🔥 СООБЩЕНИЕ - основная колонка (70% ширины)
        MessageColumn(
            message = eventInfo.event,
            modifier = Modifier.weight(1f)
        )

        // 🔥 КНОПКА КАРТЫ - красивая иконка (15% ширины)
        MapButton(
            hasCoordinates = coordinates != null,
            onClick = {
                if (coordinates != null) {
                    onMapClick(coordinates, eventInfo.event)
                } else {
                    onNoCoordinates()
                }
            }
        )
    }
}

@Composable
private fun DateTimeColumn(
    date: String,
    time: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Дата (крупнее)
        Text(
            text = date,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center
        )

        // Время (мельче и серым)
        Text(
            text = time.take(5), // Только HH:MM без секунд
            fontSize = 11.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageColumn(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = Color(0xFF333333),
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // 🔥 Категория события как тег (если есть)
        val category = extractCategoryFromMessage(message)
        if (category.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            CategoryTag(category = category)
        }
    }
}

@Composable
private fun CategoryTag(category: String) {
    Box(
        modifier = Modifier
            .background(
                color = getCategoryColor(category).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = category,
            fontSize = 10.sp,
            color = getCategoryColor(category),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MapButton(
    hasCoordinates: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = if (hasCoordinates) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4CAF50),
                            Color(0xFF45A049)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFE0E0E0),
                            Color(0xFFCCCCCC)
                        )
                    )
                }
            )
            .clickable(enabled = hasCoordinates) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (hasCoordinates) {
            // Иконка карты для доступных координат
            Text(
                text = "🗺️",
                fontSize = 20.sp
            )
        } else {
            // Иконка "недоступно" для событий без координат
            Text(
                text = "📍",
                fontSize = 16.sp,
                color = Color(0xFF999999)
            )
        }
    }
}

// 🔥 ВСПОМОГАТЕЛЬНЫЕ функции
private fun extractCategoryFromMessage(message: String): String {
    val regex = Regex("^([А-Яа-яA-Za-z0-9_\\s]+):")
    val match = regex.find(message)
    return match?.groupValues?.get(1)?.trim() ?: ""
}

private fun getCategoryColor(category: String): Color {
    return when (category.uppercase()) {
        "ДЕЙСТВИЕ" -> Color(0xFF2196F3)
        "СИСТЕМА" -> Color(0xFF4CAF50)
        "БАТАРЕЯ" -> Color(0xFFFF9800)
        "ТЕМПЕРАТУРА" -> Color(0xFFF44336)
        "GPS" -> Color(0xFF9C27B0)
        "ДАТЧИК_ХОЛЛА" -> Color(0xFF607D8B)
        "АКСЕЛЕРОМЕТР" -> Color(0xFFFF5722)
        "ТЕСТ" -> Color(0xFFE91E63)
        "ОТЛАДКА" -> Color(0xFF795548)
        else -> Color(0xFF757575)
    }
}

// Остальные функции остаются без изменений...
fun sortLogEntriesByDateDescending(logEntries: List<String>): List<String> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    return logEntries.sortedByDescending { entry ->
        try {
            val timestampPart = entry.substringBefore(" -").trim()
            val parsedDate = dateFormat.parse(timestampPart)

            if (parsedDate != null) {
                Log.d("LogScreen", "✅ Дата распарсена: $timestampPart -> ${parsedDate.time}")
                parsedDate.time
            } else {
                Log.w("LogScreen", "⚠️ Не удалось распарсить дату: $timestampPart")
                0L
            }
        } catch (e: Exception) {
            Log.e("LogScreen", "❌ Ошибка парсинга даты в строке: $entry, ошибка: ${e.message}")
            0L
        }
    }.also { sortedList ->
        if (sortedList.isNotEmpty()) {
            Log.d("LogScreen", "🔄 Сортировка завершена:")
            Log.d("LogScreen", "   📅 Самое новое: ${sortedList.first().substringBefore(" -")}")
            Log.d("LogScreen", "   📅 Самое старое: ${sortedList.last().substringBefore(" -")}")
        }
    }
}

fun parseCoordinatesFromLogEntry(logEntry: String): Pair<Double, Double>? {
    try {
        val coordinatesPart = logEntry.substringAfter("@", "").trim()

        if (coordinatesPart.isEmpty() || coordinatesPart.contains("Координаты недоступны") || coordinatesPart.contains(
                "Системное событие"
            )
        ) {
            return null
        }

        val coordsOnly = coordinatesPart.substringBefore("(").trim()
        val numberPattern = Regex("""(\d+[.,]\d+)""")
        val numbers = numberPattern.findAll(coordsOnly).map {
            it.value.replace(",", ".").toDoubleOrNull()
        }.filterNotNull().toList()

        if (numbers.size >= 2) {
            val latitude = numbers[0]
            val longitude = numbers[1]

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

data class EventInfo(
    val date: String,
    val time: String,
    val event: String
)

fun parseEventFromLogEntry(logEntry: String): EventInfo {
    val parts = logEntry.split(" - ", limit = 2)
    val timestamp = parts.getOrNull(0) ?: "Неизвестная дата"
    val eventWithCoords = parts.getOrNull(1) ?: "Неизвестное событие"

    val eventPart = eventWithCoords.substringBefore("@").trim()
    val cleanEvent = removeCategoryRegex(eventPart)

    val dateParts = timestamp.split(" ")
    val date = dateParts.getOrNull(0)?.let { formatDateWithoutYear(it) } ?: ""
    val time = dateParts.getOrNull(1) ?: ""

    return EventInfo(date, time, cleanEvent)
}

private fun removeCategoryRegex(eventText: String): String {
    val categoryPattern = Regex("^[А-Яа-яA-Za-z0-9_\\s]+:\\s*")
    return eventText.replaceFirst(categoryPattern, "").trim()
}

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp, 8.dp, 0.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Верхний ряд - две колонки по 50% для выбора дат
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DatePickerCardButton(
                label = "От",
                date = startDate,
                modifier = Modifier.weight(1f),
                onDateSelected = { selectedDate ->
                    startDate = selectedDate
                }
            )

            DatePickerCardButton(
                label = "До",
                date = endDate,
                modifier = Modifier.weight(1f),
                onDateSelected = { selectedDate ->
                    endDate = selectedDate
                }
            )
        }

        // Нижний ряд - кнопка "Показать" на всю ширину с акцентным цветом
        CardButton(
            text = "📋 Показать логи",
            onClick = {
                if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
                    Log.d("LogScreen", "🔍 Фильтрация логов: $startDate - $endDate")
                    onFilterApplied(startDate, endDate) // ← Просто передать даты
                }
            },
            backgroundColor = Color(0xFF4CAF50),
            modifier = Modifier.fillMaxWidth(),
            fontSize = 16
        )
    }
}

@Composable
fun DatePickerCardButton(
    label: String,
    date: String,
    modifier: Modifier = Modifier,
    onDateSelected: (String) -> Unit
) {
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

    CardButton(
        text = if (date.isEmpty()) label else "${label}: ${formatDateForDisplay(date)}",
        onClick = { datePickerDialog.show() },
        fontSize = 13,
        modifier = modifier
    )
}

fun formatDateForDisplay(date: String): String {
    return try {
        val parts = date.split("-")
        if (parts.size >= 3) {
            "${parts[2]}.${parts[1]}"
        } else {
            date
        }
    } catch (e: Exception) {
        date
    }
}
