package com.example.bluetooth_andr11.ui

import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bluetooth_andr11.utils.LogHelper.filterLogEntries
import java.util.Calendar
import java.util.Locale

@Composable
fun LogScreen(navController: NavController) {
    val context = LocalContext.current
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var logEntries by remember { mutableStateOf(listOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, start = 16.dp, end = 16.dp)
    ) {
        // Кнопка "Назад"
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(text = "Назад")
        }

        // Фильтр логов
        LogFilterScreen { start, end ->
            startDate = start
            endDate = end
            logEntries = filterLogEntries(context, start, end)
        }

        // Таблица с логами
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            items(logEntries) { entry ->
                val parts = entry.split(" - ", limit = 2)
                val timestamp = parts.getOrNull(0) ?: "Неизвестная дата"
                val event =
                    parts.getOrNull(1)?.substringBefore("@")?.trim() ?: "Неизвестное событие"
                val coordinatesString = parts.getOrNull(1)?.substringAfter("@")?.trim()

                // Разделяем дату и время
                val dateParts = timestamp.split(" ")
                val date = dateParts.getOrNull(0)?.let { formatDateWithoutYear(it) } ?: ""
                val time = dateParts.getOrNull(1) ?: ""

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .border(1.dp, Color.Gray)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Дата и время на двух строках
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = date)
                        Text(text = time)
                    }

                    // Событие с паддингами
                    Text(
                        text = event,
                        modifier = Modifier
                            .weight(2f)
                            .padding(start = 16.dp),
                        color = Color.Black
                    )

                    // Кнопка карты
                    Button(
                        onClick = {
                            if (coordinatesString != null) {
                                Toast.makeText(
                                    context,
                                    "Открытие карты для: $coordinatesString",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "Координаты недоступны", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E1E2F), contentColor = Color.White
                        )
                    ) {
                        Text("Карта")
                    }
                }
            }
        }
    }
}

// Форматируем дату без года
fun formatDateWithoutYear(date: String): String {
    val parts = date.split("-")
    return if (parts.size >= 2) {
        "${parts[1]}.${parts[2]}"
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
            modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Top
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
                    onFilterApplied(startDate, endDate)
                }
            }, modifier = Modifier.align(Alignment.CenterVertically)
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
        context, { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            val formattedDate = String.format(
                Locale.getDefault(),
                "%04d-%02d-%02d",
                selectedYear,
                selectedMonth + 1,
                selectedDayOfMonth
            )
            onDateSelected(formattedDate)
        }, year, month, day
    )

    Button(onClick = { datePickerDialog.show() }) {
        Text(text = if (date.isEmpty()) label else "Дата: $date")
    }
}
