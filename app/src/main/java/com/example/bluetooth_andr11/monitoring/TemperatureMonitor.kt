package com.example.bluetooth_andr11.monitoring

import android.content.Context
import android.util.Log
import com.example.bluetooth_andr11.bluetooth.BluetoothHelper
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import java.io.File

class TemperatureMonitor(
    private val context: Context,
    private val bluetoothHelper: BluetoothHelper,
    private val locationManager: EnhancedLocationManager
) {
    // Отслеживание состояния
    private var lastUpperTemp: Int? = null
    private var lastLowerTemp: Int? = null
    private val upperThresholds = mutableSetOf<Int>()
    private val lowerThresholds = mutableSetOf<Int>()

    // Простые пороги для доставки еды
    companion object {
        private const val TAG = "TemperatureMonitor"

        // Пороги для горячего отсека (еда должна быть теплой)
        private val HOT_FOOD_THRESHOLDS = listOf(40, 50, 60) // Комфорт, горячо, очень горячо
        private val HOT_COOLING_THRESHOLDS = listOf(45, 35, 25) // Остывание горячей еды

        // Пороги для холодного отсека (еда должна быть прохладной)
        private val COLD_FOOD_THRESHOLDS = listOf(15, 10, 5) // Прохладно, холодно, очень холодно
        private val COLD_WARMING_THRESHOLDS = listOf(10, 15, 20) // Нагрев холодной еды
    }

    // Основная функция мониторинга
    fun processTemperatures(upperTemp: Float?, lowerTemp: Float?) {
        upperTemp?.let { processHotCompartment(it) }
        lowerTemp?.let { processColdCompartment(it) }
    }

    // 🔥 Мониторинг ГОРЯЧЕГО отсека
    private fun processHotCompartment(temp: Float) {
        val tempInt = temp.toInt()
        val previous = lastUpperTemp
        lastUpperTemp = tempInt

        Log.d(TAG, "🔥 Горячий отсек: ${previous}°C → ${tempInt}°C")

        if (previous != null) {
            when {
                tempInt > previous -> checkHotFoodHeating(tempInt, previous)
                tempInt < previous -> checkHotFoodCooling(tempInt, previous)
            }
        }
    }

    // ❄️ Мониторинг ХОЛОДНОГО отсека  
    private fun processColdCompartment(temp: Float) {
        val tempInt = temp.toInt()
        val previous = lastLowerTemp
        lastLowerTemp = tempInt

        Log.d(TAG, "❄️ Холодный отсек: ${previous}°C → ${tempInt}°C")

        if (previous != null) {
            when {
                tempInt < previous -> checkColdFoodCooling(tempInt, previous)
                tempInt > previous -> checkColdFoodWarming(tempInt, previous)
            }
        }
    }

    // 🔥 Горячая еда: нагрев
    private fun checkHotFoodHeating(current: Int, previous: Int) {
        HOT_FOOD_THRESHOLDS.forEach { threshold ->
            if (current >= threshold && !upperThresholds.contains(threshold)) {
                upperThresholds.add(threshold)
                val event = createHotFoodEvent(threshold, previous, current, isHeating = true)
                logTemperatureEvent(event)
            }
        }
    }

    // 🔥 Горячая еда: остывание
    private fun checkHotFoodCooling(current: Int, previous: Int) {
        HOT_COOLING_THRESHOLDS.forEach { threshold ->
            val negativeKey = -threshold
            if (current <= threshold && previous > threshold && !upperThresholds.contains(
                    negativeKey
                )
            ) {
                upperThresholds.add(negativeKey)
                val event = createHotFoodEvent(threshold, previous, current, isHeating = false)
                logTemperatureEvent(event)
            }
        }
    }

    // ❄️ Холодная еда: охлаждение
    private fun checkColdFoodCooling(current: Int, previous: Int) {
        COLD_FOOD_THRESHOLDS.forEach { threshold ->
            if (current <= threshold && !lowerThresholds.contains(threshold)) {
                lowerThresholds.add(threshold)
                val event = createColdFoodEvent(threshold, previous, current, isCooling = true)
                logTemperatureEvent(event)
            }
        }
    }

    // ❄️ Холодная еда: нагрев
    private fun checkColdFoodWarming(current: Int, previous: Int) {
        COLD_WARMING_THRESHOLDS.forEach { threshold ->
            val negativeKey = -100 - threshold
            if (current >= threshold && previous < threshold && !lowerThresholds.contains(
                    negativeKey
                )
            ) {
                lowerThresholds.add(negativeKey)
                val event = createColdFoodEvent(threshold, previous, current, isCooling = false)
                logTemperatureEvent(event)
            }
        }
    }

    // 🔥 Создание событий для горячей еды
    private fun createHotFoodEvent(
        threshold: Int,
        previous: Int,
        current: Int,
        isHeating: Boolean
    ): TemperatureEvent {
        return if (isHeating) {
            when (threshold) {
                40 -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬆️",
                    message = "Еда нагрелась до ${threshold}°C - комфортная температура",
                    details = "было ${previous}°C"
                )

                50 -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬆️",
                    message = "Еда горячая ${threshold}°C - оптимальная температура подачи",
                    details = "было ${previous}°C"
                )

                60 -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬆️",
                    message = "Еда очень горячая ${threshold}°C - осторожно при подаче",
                    details = "было ${previous}°C"
                )

                else -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬆️",
                    message = "Температура ${current}°C",
                    details = "было ${previous}°C"
                )
            }
        } else {
            when (threshold) {
                45 -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬇️",
                    message = "Еда остыла до ${threshold}°C - еще теплая",
                    details = "было ${previous}°C"
                )

                35 -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬇️",
                    message = "Еда остыла до ${threshold}°C - умеренная температура",
                    details = "было ${previous}°C"
                )

                25 -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬇️",
                    message = "Еда остыла до ${threshold}°C - комнатная температура",
                    details = "было ${previous}°C"
                )

                else -> TemperatureEvent(
                    compartment = "ГОРЯЧИЙ",
                    icon = "🔥",
                    direction = "⬇️",
                    message = "Остыла до ${current}°C",
                    details = "было ${previous}°C"
                )
            }
        }
    }

    // ❄️ Создание событий для холодной еды
    private fun createColdFoodEvent(
        threshold: Int,
        previous: Int,
        current: Int,
        isCooling: Boolean
    ): TemperatureEvent {
        return if (isCooling) {
            when (threshold) {
                15 -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬇️",
                    message = "Еда охладилась до ${threshold}°C - прохладная",
                    details = "было ${previous}°C"
                )

                10 -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬇️",
                    message = "Еда холодная ${threshold}°C - оптимальная температура",
                    details = "было ${previous}°C"
                )

                5 -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬇️",
                    message = "Еда очень холодная ${threshold}°C - хорошо охлаждена",
                    details = "было ${previous}°C"
                )

                else -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬇️",
                    message = "Температура ${current}°C",
                    details = "было ${previous}°C"
                )
            }
        } else {
            when (threshold) {
                10 -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬆️",
                    message = "Еда нагрелась до ${threshold}°C - уже не холодная",
                    details = "было ${previous}°C"
                )

                15 -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬆️",
                    message = "Еда нагрелась до ${threshold}°C - прохладная",
                    details = "было ${previous}°C"
                )

                20 -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬆️",
                    message = "Еда нагрелась до ${threshold}°C - комнатная температура",
                    details = "было ${previous}°C"
                )

                else -> TemperatureEvent(
                    compartment = "ХОЛОДНЫЙ",
                    icon = "❄️",
                    direction = "⬆️",
                    message = "Нагрелась до ${current}°C",
                    details = "было ${previous}°C"
                )
            }
        }
    }

    // 📝 Логирование события
    private fun logTemperatureEvent(event: TemperatureEvent) {
        val formattedMessage = formatEventMessage(event)
        Log.d(TAG, "🌡️ СОБЫТИЕ: $formattedMessage")

        try {
            writeToLogFile(formattedMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи: ${e.message}")
            LogModule.logEvent(context, "ТЕМПЕРАТУРА: $formattedMessage")
        }
    }

    // 📝 Форматирование сообщения (упрощенное, без severity)
    private fun formatEventMessage(event: TemperatureEvent): String {
        return "${event.icon} ${event.compartment} ОТСЕК ${event.direction} ${event.message} (${event.details})"
    }

    // 📁 Прямая запись в лог-файл
    private fun writeToLogFile(message: String) {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) logDir.mkdirs()

        val logFile = File(logDir, "events_log.txt")
        val timestamp =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())

        val locationInfo = locationManager.getLocationInfo()
        val coordinates = if (locationInfo.coordinates != "Неизвестно") {
            "${locationInfo.coordinates} (${locationInfo.source}, ±${locationInfo.accuracy.toInt()}м)"
        } else {
            "Координаты недоступны"
        }

        val logEntry = "$timestamp - ТЕМПЕРАТУРА: $message @ $coordinates\n"
        logFile.appendText(logEntry)

        Log.d(TAG, "✅ Записано в лог: $message")
    }

    // 🔄 Сброс состояния
    fun reset() {
        upperThresholds.clear()
        lowerThresholds.clear()
        lastUpperTemp = null
        lastLowerTemp = null
        Log.d(TAG, "🔄 Состояние мониторинга сброшено")
    }

    // 📊 Получение статистики
    fun getStatistics(): TemperatureStatistics {
        return TemperatureStatistics(
            hotCompartmentTemp = lastUpperTemp,
            coldCompartmentTemp = lastLowerTemp,
            hotEventsCount = upperThresholds.size,
            coldEventsCount = lowerThresholds.size
        )
    }
}

// 📋 Упрощенные data классы (убрали severity)
data class TemperatureEvent(
    val compartment: String,    // ГОРЯЧИЙ/ХОЛОДНЫЙ
    val icon: String,          // 🔥/❄️
    val direction: String,     // ⬆️/⬇️
    val message: String,       // Понятное сообщение о состоянии еды
    val details: String        // Дополнительные детали
)

data class TemperatureStatistics(
    val hotCompartmentTemp: Int?,
    val coldCompartmentTemp: Int?,
    val hotEventsCount: Int,
    val coldEventsCount: Int
)
