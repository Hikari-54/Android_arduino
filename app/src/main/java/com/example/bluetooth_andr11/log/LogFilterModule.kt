package com.example.bluetooth_andr11.log

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun LogFilterScreen(onFilterApplied: (String, String) -> Unit) {
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(8.dp)) {
        DatePickerButton("Начальная дата", startDate) { selectedDate ->
            startDate = selectedDate
        }
        Spacer(modifier = Modifier.height(8.dp))

        DatePickerButton("Конечная дата", endDate) { selectedDate ->
            endDate = selectedDate
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
                onFilterApplied(startDate, endDate)
            }
        }) {
            Text(
                "Применить фильтр",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
            FilteredLogScreen(startDate, endDate)
        }
    }
}

@Composable
fun FilteredLogScreen(startDate: String, endDate: String) {
    val context = LocalContext.current
    var logEntries by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(startDate, endDate) {
        logEntries = filterLogEntries(context, startDate, endDate)
    }

    if (logEntries.isEmpty()) {
        Text(text = "Нет записей за выбранный период.", modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(logEntries) { entry ->
                Text(text = entry)
                Divider()
            }
        }
    }
}

fun filterLogEntries(
    context: Context,
    startDate: String,
    endDate: String
): List<String> {
    val logDir = File(context.getExternalFilesDir(null), "MyAppLogs")
    val logFile = File(logDir, "log.txt")
    if (!logFile.exists()) {
        Toast.makeText(context, "Файл лога не найден.", Toast.LENGTH_SHORT).show()
        return emptyList()
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val start: Date
    val end: Date
    try {
        start = dateFormat.parse("$startDate 00:00:00") ?: return emptyList()
        end = dateFormat.parse("$endDate 23:59:59") ?: return emptyList()
    } catch (e: ParseException) {
        Toast.makeText(context, "Ошибка парсинга даты: ${e.message}", Toast.LENGTH_SHORT).show()
        return emptyList()
    }

    val filteredEntries = mutableListOf<String>()
    logFile.forEachLine { line ->
        val timestamp = line.substringAfter("Time: ").substringBefore(",")
        val logDate = try {
            dateFormat.parse(timestamp)
        } catch (e: ParseException) {
            null
        }

        if (logDate != null && logDate >= start && logDate <= end) {
            filteredEntries.add(line)
        }
    }

    if (filteredEntries.isEmpty()) {
        Toast.makeText(context, "Записи за указанный период не найдены.", Toast.LENGTH_SHORT).show()
    }

    return filteredEntries
}

@Composable
fun DatePickerButton(label: String, date: String, onDateSelected: (String) -> Unit) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                onDateSelected(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Button(onClick = { datePickerDialog.show() }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = if (date.isNotEmpty()) date else "не выбрана",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

