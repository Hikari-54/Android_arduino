package com.example.bluetooth_andr11

import android.util.Log
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

    // Симулируемые состояния
    private var batteryLevel = 85
    private var upperTemp = 25.0f
    private var lowerTemp = 15.0f
    private var bagClosed = false
    private var heatActive = false
    private var coolActive = false
    private var lightActive = false
    private var shakeLevel = 0.1f

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
                onDataReceived(data)
                delay(1000) // Отправляем данные каждую секунду
            }
        }
    }

    fun stopSimulation() {
        isRunning = false
        simulationJob?.cancel()
        Log.d("ArduinoSimulator", "Симуляция Arduino остановлена")
    }

    // 🔥 ОБНОВЛЕННЫЙ метод для автоматического переключения сценария
    fun setScenario(scenario: SimulationScenario) {
        currentScenario = scenario
        scenarioStep = 0 // Сброс счетчика
        Log.d("ArduinoSimulator", "Переключен сценарий: $scenario")
    }

    // Симуляция команд от приложения
    fun handleCommand(command: String) {
        when (command) {
            "H" -> {
                heatActive = true
                Log.d("ArduinoSimulator", "Нагрев включен")
            }

            "h" -> {
                heatActive = false
                Log.d("ArduinoSimulator", "Нагрев выключен")
            }

            "C" -> {
                coolActive = true
                Log.d("ArduinoSimulator", "Охлаждение включено")
            }

            "c" -> {
                coolActive = false
                Log.d("ArduinoSimulator", "Охлаждение выключено")
            }

            "L" -> {
                lightActive = true
                Log.d("ArduinoSimulator", "Свет включен")
            }

            "l" -> {
                lightActive = false
                Log.d("ArduinoSimulator", "Свет выключен")
            }
        }
    }

    private fun generateData(): String {
        updateSimulationState()

        val battery = batteryLevel.coerceIn(0, 100)
        val temp1 =
            if (currentScenario == SimulationScenario.SENSOR_ERRORS && scenarioStep % 10 < 3) {
                "er"
            } else {
                String.format("%.1f", upperTemp)
            }
        val temp2 =
            if (currentScenario == SimulationScenario.SENSOR_ERRORS && scenarioStep % 7 < 2) {
                "er"
            } else {
                String.format("%.1f", lowerTemp)
            }
        val closed = if (bagClosed) 1 else 0
        val state = calculateState()
        val shake = String.format("%.2f", shakeLevel)

        return "$battery,$temp1,$temp2,$closed,$state,$shake"
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
        if (scenarioStep % 120 == 0) batteryLevel--

        // Небольшие колебания температуры
        upperTemp += Random.nextFloat() * 0.4f - 0.2f
        lowerTemp += Random.nextFloat() * 0.3f - 0.15f

        // Случайная тряска
        shakeLevel = Random.nextFloat() * 0.3f

        // Случайное открытие/закрытие сумки (редко)
        if (Random.nextFloat() < 0.01f) {
            bagClosed = !bagClosed
        }
    }

    // 🔥 ОБНОВЛЕННЫЕ краткие сценарии
    private fun updateBatteryDrainState() {
        // Быстрая разрядка за 30 секунд
        when {
            scenarioStep < 10 -> batteryLevel = 50 - scenarioStep * 2 // 50% -> 30%
            scenarioStep < 20 -> batteryLevel = 30 - (scenarioStep - 10) * 2 // 30% -> 10%
            scenarioStep < 30 -> batteryLevel = 10 - (scenarioStep - 20) // 10% -> 0%
            else -> {
                batteryLevel = 0
                // Автоматический возврат к нормальному режиму
                setScenario(SimulationScenario.NORMAL)
            }
        }

        upperTemp += Random.nextFloat() * 0.2f - 0.1f
        lowerTemp += Random.nextFloat() * 0.2f - 0.1f
        shakeLevel = Random.nextFloat() * 0.2f
    }

    private fun updateHeatingCycleState() {
        // Быстрый цикл нагрева за 45 секунд
        when {
            scenarioStep < 10 -> {
                heatActive = false
                upperTemp = 25.0f + scenarioStep * 0.5f // Медленный рост
            }

            scenarioStep < 25 -> {
                heatActive = true
                upperTemp = 30.0f + (scenarioStep - 10) * 1.5f // Быстрый нагрев до 52°C
            }

            scenarioStep < 35 -> {
                heatActive = true
                upperTemp = 52.0f + Random.nextFloat() * 3f - 1.5f // Стабилизация ~52°C
            }

            scenarioStep < 45 -> {
                heatActive = false
                upperTemp = (upperTemp - 1.0f).coerceAtLeast(25.0f) // Остывание
            }

            else -> {
                setScenario(SimulationScenario.NORMAL)
            }
        }

        batteryLevel = (batteryLevel - if (heatActive) 0.3f else 0.1f).toInt().coerceAtLeast(0)
        shakeLevel = Random.nextFloat() * 0.3f
    }

    private fun updateCoolingCycleState() {
        // Быстрый цикл охлаждения за 45 секунд
        when {
            scenarioStep < 10 -> {
                coolActive = false
                lowerTemp = 15.0f - scenarioStep * 0.3f // Медленное остывание
            }

            scenarioStep < 25 -> {
                coolActive = true
                lowerTemp = 12.0f - (scenarioStep - 10) * 0.5f // Быстрое охлаждение до 4°C
            }

            scenarioStep < 35 -> {
                coolActive = true
                lowerTemp = 4.0f + Random.nextFloat() * 2f - 1f // Стабилизация ~4°C
            }

            scenarioStep < 45 -> {
                coolActive = false
                lowerTemp = (lowerTemp + 0.3f).coerceAtMost(15.0f) // Нагревание
            }

            else -> {
                setScenario(SimulationScenario.NORMAL)
            }
        }

        batteryLevel = (batteryLevel - if (coolActive) 0.4f else 0.1f).toInt().coerceAtLeast(0)
        shakeLevel = Random.nextFloat() * 0.3f
    }

    private fun updateBagOpeningClosingState() {
        // Демонстрация открытия/закрытия за 40 секунд
        when {
            scenarioStep % 8 == 0 -> bagClosed = !bagClosed // Каждые 8 секунд
        }

        upperTemp += Random.nextFloat() * 0.3f - 0.15f
        lowerTemp += Random.nextFloat() * 0.3f - 0.15f
        shakeLevel = Random.nextFloat() * 0.5f

        if (scenarioStep % 60 == 0) batteryLevel--

        if (scenarioStep >= 40) {
            setScenario(SimulationScenario.NORMAL)
        }
    }

    private fun updateStrongShakingState() {
        // Демонстрация тряски за 35 секунд
        when {
            scenarioStep < 10 -> shakeLevel = Random.nextFloat() * 1.0f + 2.5f // Экстремальная
            scenarioStep < 20 -> shakeLevel = Random.nextFloat() * 0.8f + 1.2f // Сильная
            scenarioStep < 30 -> shakeLevel = Random.nextFloat() * 0.6f + 0.5f // Слабая
            scenarioStep < 35 -> shakeLevel = Random.nextFloat() * 0.2f // Затухание
            else -> {
                setScenario(SimulationScenario.NORMAL)
            }
        }

        upperTemp += Random.nextFloat() * 0.4f - 0.2f
        lowerTemp += Random.nextFloat() * 0.4f - 0.2f

        if (scenarioStep % 20 == 0) batteryLevel--
    }

    private fun updateSensorErrorsState() {
        // Демонстрация ошибок датчиков за 50 секунд
        // Ошибки уже обрабатываются в generateData()
        upperTemp += Random.nextFloat() * 0.5f - 0.25f
        lowerTemp += Random.nextFloat() * 0.5f - 0.25f
        shakeLevel = Random.nextFloat() * 0.4f

        if (scenarioStep % 30 == 0) batteryLevel--

        if (scenarioStep >= 50) {
            setScenario(SimulationScenario.NORMAL)
        }
    }

    private fun calculateState(): Int {
        var state = 0
        if (heatActive) state++
        if (coolActive) state++
        if (lightActive) state++
        return state
    }

    // Методы для ручного управления параметрами
    fun setBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
    }

    fun setTemperatures(upper: Float, lower: Float) {
        upperTemp = upper
        lowerTemp = lower
    }

    fun setBagClosed(closed: Boolean) {
        bagClosed = closed
    }

    fun triggerShake(intensity: Float) {
        shakeLevel = intensity
    }
}