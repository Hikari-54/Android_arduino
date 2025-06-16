package com.example.bluetooth_andr11

import android.util.Log
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ArduinoSimulator(
    private val onDataReceived: (String) -> Unit
) {
    private var simulationJob: Job? = null
    private var isRunning = false

    // Симулируемые состояния (соответствуют Arduino)
    private var batteryPercent = 85          // batteryPercent в Arduino
    private var tempHot = 25.0f              // tempHot (верхний отсек)
    private var tempCold = 15.0f             // tempCold (нижний отсек)
    private var bagClosed = false            // состояние сумки
    private var isHeatActive = false         // состояние нагрева
    private var coolActive = false           // состояние охлаждения
    private var lightActive = false          // состояние света
    private var overload = 0.1f              // overload (акселерометр)

    // Сценарии для тестирования
    private var currentScenario = SimulationScenario.NORMAL
    private var scenarioStep = 0

    enum class SimulationScenario {
        NORMAL,              // Обычная работа
        BATTERY_DRAIN,       // Разрядка батареи
        HEATING_CYCLE,       // Цикл нагрева
        COOLING_CYCLE,       // Цикл охлаждения
        BAG_OPENING_CLOSING, // Открытие/закрытие сумки
        STRONG_SHAKING,      // Сильная тряска
        SENSOR_ERRORS        // Ошибки датчиков
    }

    fun startSimulation() {
        if (isRunning) return

        isRunning = true
        simulationJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d("ArduinoSimulator", "Симуляция Arduino запущена")

            while (isRunning) {
                val data = generateData()
                // 🔥 ВАЖНО: Добавляем \n как делает Arduino через println
                onDataReceived("$data\n")
                delay(300) // Отправляем данные каждые 300мс как в Arduino
            }
        }
    }

    fun stopSimulation() {
        isRunning = false
        simulationJob?.cancel()
        Log.d("ArduinoSimulator", "Симуляция Arduino остановлена")
    }

    fun setScenario(scenario: SimulationScenario) {
        currentScenario = scenario
        scenarioStep = 0 // Сброс счетчика
        Log.d("ArduinoSimulator", "Переключен сценарий: $scenario")
    }

    // Симуляция команд от приложения (точно как в Arduino)
    fun handleCommand(command: String) {
        when (command) {
            "H" -> {
                Log.d("ArduinoSimulator", "HEAT ON")
                isHeatActive = true
            }

            "h" -> {
                Log.d("ArduinoSimulator", "HEAT OFF")
                isHeatActive = false
            }

            "C" -> {
                Log.d("ArduinoSimulator", "COOL ON")
                coolActive = true
            }

            "c" -> {
                Log.d("ArduinoSimulator", "COOL OFF")
                coolActive = false
            }

            "L" -> {
                if (!lightActive) {
                    Log.d("ArduinoSimulator", "LIGHT ON")
                    lightActive = true
                } else {
                    Log.d("ArduinoSimulator", "LIGHT уже включена")
                }
            }

            "l" -> {
                if (lightActive) {
                    Log.d("ArduinoSimulator", "LIGHT OFF")
                    lightActive = false
                } else {
                    Log.d("ArduinoSimulator", "LIGHT уже выключена")
                }
            }

            else -> {
                Log.d("ArduinoSimulator", "Unknown command: $command")
            }
        }
    }

    private fun generateData(): String {
        updateSimulationState()

        // 1. Battery percent (0-100)
        val battery = batteryPercent.coerceIn(0, 100)

        // 2. Hot temperature (upper compartment) - может быть "er" при ошибке
        val temp1 = if (currentScenario == SimulationScenario.SENSOR_ERRORS && scenarioStep % 10 < 3) {
            "er"
        } else {
            // 🔥 ИСПРАВЛЕНИЕ: Принудительно используем US локаль (точка как разделитель)
            String.format(Locale.US, "%.2f", tempHot)
        }

        // 3. Cold temperature (lower compartment) - может быть "er" при ошибке
        val temp2 = if (currentScenario == SimulationScenario.SENSOR_ERRORS && scenarioStep % 7 < 2) {
            "er"
        } else {
            // 🔥 ИСПРАВЛЕНИЕ: Принудительно используем US локаль (точка как разделитель)
            String.format(Locale.US, "%.2f", tempCold)
        }

        // 4. Closed state (0 = open, 1 = closed)
        val closedState = if (bagClosed) 1 else 0

        // 5. State (количество активных функций)
        val currentState = calculateState()

        // 6. Overload (акселерометр - разница от 1.0g)
        // 🔥 ИСПРАВЛЕНИЕ: Принудительно используем US локаль (точка как разделитель)
        val overloadValue = String.format(Locale.US, "%.2f", overload)

        // Формат точно как в Arduino: batteryPercent,tempHot,tempCold,closedState,state,overload
        val result = "$battery,$temp1,$temp2,$closedState,$currentState,$overloadValue"

        Log.d("ArduinoSimulator", "Генерируем данные: $result")
        Log.d("ArduinoSimulator", "🎯 ТОЧНЫЙ РЕЗУЛЬТАТ: '$result' (параметров: ${result.split(",").size})")

        return result
    }

    private fun updateSimulationState() {
        when (currentScenario) {
            SimulationScenario.NORMAL -> updateNormalState()
            SimulationScenario.BATTERY_DRAIN -> updateBatteryDrainState()
            SimulationScenario.HEATING_CYCLE -> updateHeatingCycleState()
            SimulationScenario.COOLING_CYCLE -> updateCoolingCycleState()
            SimulationScenario.BAG_OPENING_CLOSING -> updateBagOpeningClosingState()
            SimulationScenario.STRONG_SHAKING -> updateStrongShakingState()
            SimulationScenario.SENSOR_ERRORS -> updateSensorErrorsState()
        }

        scenarioStep++
    }

    private fun updateNormalState() {
        // Медленная разрядка батареи
        if (scenarioStep % 400 == 0) batteryPercent-- // Разряжается медленнее

        // Небольшие колебания температуры
        tempHot += Random.nextFloat() * 0.4f - 0.2f
        tempCold += Random.nextFloat() * 0.3f - 0.15f

        // Ограничиваем температуры в разумных пределах
        tempHot = tempHot.coerceIn(20.0f, 30.0f)
        tempCold = tempCold.coerceIn(10.0f, 20.0f)

        // Случайный overload (небольшой)
        overload = Random.nextFloat() * 0.3f

        // Случайное открытие/закрытие сумки (очень редко)
        if (Random.nextFloat() < 0.005f) {
            bagClosed = !bagClosed
        }
    }

    private fun updateBatteryDrainState() {
        // Быстрая разрядка за 30 секунд (30000мс / 300мс = 100 шагов)
        when {
            scenarioStep < 33 -> batteryPercent = 50 - scenarioStep * 1 // 50% -> 17%
            scenarioStep < 66 -> batteryPercent =
                17 - (scenarioStep - 33) // 17% -> -16% (ограничится 0)
            scenarioStep < 100 -> batteryPercent = 0
            else -> {
                batteryPercent = 5 // Критический уровень
                setScenario(SimulationScenario.NORMAL)
            }
        }

        batteryPercent = batteryPercent.coerceIn(0, 100)

        tempHot += Random.nextFloat() * 0.2f - 0.1f
        tempCold += Random.nextFloat() * 0.2f - 0.1f
        overload = Random.nextFloat() * 0.2f
    }

    private fun updateHeatingCycleState() {
        // Цикл нагрева за 45 секунд (150 шагов)
        when {
            scenarioStep < 33 -> {
                isHeatActive = false
                tempHot = 25.0f + scenarioStep * 0.3f // Медленный рост 25°C -> 35°C
            }

            scenarioStep < 83 -> {
                isHeatActive = true
                // Быстрый нагрев до 55°C
                tempHot = 35.0f + (scenarioStep - 33) * 0.4f
            }

            scenarioStep < 117 -> {
                isHeatActive = true
                // Стабилизация около 55°C
                tempHot = 55.0f + Random.nextFloat() * 4f - 2f
            }

            scenarioStep < 150 -> {
                isHeatActive = false
                // Остывание
                tempHot = (tempHot - 0.8f).coerceAtLeast(25.0f)
            }

            else -> {
                setScenario(SimulationScenario.NORMAL)
            }
        }

        // Потребление батареи при нагреве
        if (isHeatActive && scenarioStep % 10 == 0) {
            batteryPercent = (batteryPercent - 1).coerceAtLeast(0)
        }

        overload = Random.nextFloat() * 0.3f
    }

    private fun updateCoolingCycleState() {
        // Цикл охлаждения за 45 секунд (150 шагов)
        when {
            scenarioStep < 33 -> {
                coolActive = false
                tempCold = 15.0f - scenarioStep * 0.2f // Медленное остывание 15°C -> 8°C
            }

            scenarioStep < 83 -> {
                coolActive = true
                // Быстрое охлаждение до 2°C
                tempCold = 8.0f - (scenarioStep - 33) * 0.12f
            }

            scenarioStep < 117 -> {
                coolActive = true
                // Стабилизация около 2°C
                tempCold = 2.0f + Random.nextFloat() * 3f - 1.5f
            }

            scenarioStep < 150 -> {
                coolActive = false
                // Нагревание
                tempCold = (tempCold + 0.3f).coerceAtMost(15.0f)
            }

            else -> {
                setScenario(SimulationScenario.NORMAL)
            }
        }

        // Потребление батареи при охлаждении
        if (coolActive && scenarioStep % 8 == 0) {
            batteryPercent = (batteryPercent - 1).coerceAtLeast(0)
        }

        overload = Random.nextFloat() * 0.3f
    }

    private fun updateBagOpeningClosingState() {
        // Демонстрация открытия/закрытия за 40 секунд (133 шага)
        when {
            scenarioStep % 25 == 0 -> bagClosed = !bagClosed // Каждые ~7.5 секунд
        }

        tempHot += Random.nextFloat() * 0.3f - 0.15f
        tempCold += Random.nextFloat() * 0.3f - 0.15f
        overload = Random.nextFloat() * 0.5f

        if (scenarioStep % 200 == 0) batteryPercent--

        if (scenarioStep >= 133) {
            setScenario(SimulationScenario.NORMAL)
        }
    }

    private fun updateStrongShakingState() {
        // Демонстрация тряски за 35 секунд (117 шагов)
        when {
            scenarioStep < 33 -> overload =
                Random.nextFloat() * 1.5f + 2.0f // Экстремальная 2.0-3.5
            scenarioStep < 66 -> overload = Random.nextFloat() * 1.0f + 1.0f // Сильная 1.0-2.0
            scenarioStep < 100 -> overload = Random.nextFloat() * 0.8f + 0.3f // Средняя 0.3-1.1
            scenarioStep < 117 -> overload = Random.nextFloat() * 0.2f // Затухание 0.0-0.2
            else -> {
                setScenario(SimulationScenario.NORMAL)
            }
        }

        tempHot += Random.nextFloat() * 0.4f - 0.2f
        tempCold += Random.nextFloat() * 0.4f - 0.2f

        if (scenarioStep % 67 == 0) batteryPercent-- // Батарея разряжается при тряске
    }

    private fun updateSensorErrorsState() {
        // Демонстрация ошибок датчиков за 50 секунд (167 шагов)
        // Ошибки уже обрабатываются в generateData()

        // Для температур, которые не в состоянии ошибки
        if (!(scenarioStep % 10 < 3)) {
            tempHot += Random.nextFloat() * 0.5f - 0.25f
        }
        if (!(scenarioStep % 7 < 2)) {
            tempCold += Random.nextFloat() * 0.5f - 0.25f
        }

        overload = Random.nextFloat() * 0.4f

        if (scenarioStep % 100 == 0) batteryPercent--

        if (scenarioStep >= 167) {
            setScenario(SimulationScenario.NORMAL)
        }
    }

    private fun calculateState(): Int {
        var currentState = 0
        if (isHeatActive) currentState++
        if (coolActive) currentState++
        if (lightActive) currentState++
        return currentState
    }

    // Методы для ручного управления параметрами
    fun setBatteryLevel(level: Int) {
        batteryPercent = level.coerceIn(0, 100)
    }

    fun setTemperatures(upper: Float, lower: Float) {
        tempHot = upper
        tempCold = lower
    }

    fun setBagClosed(closed: Boolean) {
        bagClosed = closed
    }

    fun triggerShake(intensity: Float) {
        overload = intensity
    }
}