package com.example.bluetooth_andr11.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .padding(16.dp)
    ) {
        // Кастомный верхний бар
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { navController.popBackStack() }) {
                Text(text = "Назад")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "Логи", style = androidx.compose.ui.text.TextStyle.Default)
        }

        // Экран фильтрации логов
        LogFilterScreen { start, end ->
            startDate = start
            endDate = end
            logEntries = filterLogEntries(context, start, end)
        }

        // Отображение списка логов
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            items(logEntries) { entry ->
                Text(text = entry, modifier = Modifier.padding(8.dp))
                HorizontalDivider()
            }
        }

    }
}

@Composable
fun LogFilterScreen(onFilterApplied: (String, String) -> Unit) {
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Левая часть с выбором начальной и конечной дат
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

        // Кнопка подтверждения фильтрации
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

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate =
                String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            onDateSelected(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Button(onClick = { datePickerDialog.show() }) {
        Text(text = if (date.isEmpty()) label else "Дата: $date")
    }
}


