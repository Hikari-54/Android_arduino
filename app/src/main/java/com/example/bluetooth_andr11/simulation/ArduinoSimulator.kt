package com.example.bluetooth_andr11.simulation

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

/**
 * Симулятор Arduino для тестирования приложения без физического устройства.
 * Генерирует реалистичные данные в том же формате, что и настоящий Arduino.
 */
class ArduinoSimulator(
    private val onDataReceived: (String) -> Unit
) {
    companion object {
        private const val TAG = "ArduinoSimulator"
        private const val DATA_INTERVAL_MS = 300L
        private const val BATTERY_MIN = 0
        private const val BATTERY_MAX = 100
        private const val TEMP_MIN = -10.0f
        private const val TEMP_MAX = 70.0f
        private const val OVERLOAD_MIN = 0.0f
        private const val OVERLOAD_MAX = 5.0f
    }

    // Состояние симулятора
    private var simulationJob: Job? = null
    private var isRunning = false

    // Симулируемые параметры устройства (соответствуют Arduino)
    private var batteryPercent = 85
    private var tempHot = 25.0f
    private var tempCold = 15.0f
    private var bagClosed = false
    private var isHeatActive = false
    private var coolActive = false
    private var lightActive = false
    private var overload = 0.1f

    // Управление сценариями
    private var currentScenario = SimulationScenario.NORMAL
    private var scenarioStep = 0

    enum class SimulationScenario {
        NORMAL,              // Обычная работа с медленными изменениями
        BATTERY_DRAIN,       // Быстрая разрядка батареи
        HEATING_CYCLE,       // Демонстрация цикла нагрева
        COOLING_CYCLE,       // Демонстрация цикла охлаждения
        BAG_OPENING_CLOSING, // Частое открытие/закрытие сумки
        STRONG_SHAKING,      // Демонстрация сильной тряски
        SENSOR_ERRORS        // Периодические ошибки датчиков
    }

    /**
     * Запускает симуляцию Arduino в отдельной корутине
     */
    fun startSimulation() {
        if (isRunning) {
            Log.w(TAG, "Симуляция уже запущена")
            return
        }

        isRunning = true
        simulationJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Симуляция Arduino запущена")

            while (isRunning) {
                try {
                    val data = generateSimulatedData()
                    onDataReceived("$data\n") // Добавляем \n как делает настоящий Arduino
                    delay(DATA_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка в симуляции: ${e.message}")
                    break
                }
            }
        }
    }

    /**
     * Останавливает симуляцию и освобождает ресурсы
     */
    fun stopSimulation() {
        isRunning = false
        simulationJob?.cancel()
        simulationJob = null
        Log.d(TAG, "Симуляция Arduino остановлена")
    }

    /**
     * Переключает сценарий симуляции
     */
    fun setScenario(scenario: SimulationScenario) {
        if (currentScenario != scenario) {
            currentScenario = scenario
            scenarioStep = 0 // Сброс счетчика шагов
            Log.d(TAG, "Переключен сценарий симуляции: $scenario")
        }
    }

    /**
     * Обрабатывает команды от приложения (точно как настоящий Arduino)
     */
    fun handleCommand(command: String) {
        when (command.trim().uppercase()) {
            "H" -> {
                Log.d(TAG, "HEAT ON")
                isHeatActive = true
            }

            "h" -> {
                Log.d(TAG, "HEAT OFF")
                isHeatActive = false
            }

            "C" -> {
                Log.d(TAG, "COOL ON")
                coolActive = true
            }

            "c" -> {
                Log.d(TAG, "COOL OFF")
                coolActive = false
            }

            "L" -> {
                if (!lightActive) {
                    Log.d(TAG, "LIGHT ON")
                    lightActive = true
                } else {
                    Log.d(TAG, "LIGHT уже включена")
                }
            }

            "l" -> {
                if (lightActive) {
                    Log.d(TAG, "LIGHT OFF")
                    lightActive = false
                } else {
                    Log.d(TAG, "LIGHT уже выключена")
                }
            }

            else -> {
                Log.w(TAG, "Неизвестная команда: $command")
            }
        }
    }

    /**
     * Генерирует строку данных в формате Arduino
     * Формат: batteryPercent,tempHot,tempCold,closedState,state,overload
     */
    private fun generateSimulatedData(): String {
        updateSimulationState()

        // 1. Уровень батареи (0-100)
        val battery = batteryPercent.coerceIn(BATTERY_MIN, BATTERY_MAX)

        // 2. Температура верхнего отсека (может быть "er" при ошибке)
        val temp1 = if (shouldSimulateError(10)) {
            "er"
        } else {
            formatTemperature(tempHot)
        }

        // 3. Температура нижнего отсека (может быть "er" при ошибке)
        val temp2 = if (shouldSimulateError(7)) {
            "er"
        } else {
            formatTemperature(tempCold)
        }

        // 4. Состояние сумки (0 = открыта, 1 = закрыта)
        val closedState = if (bagClosed) 1 else 0

        // 5. Состояние активных функций
        val currentState = calculateActiveState()

        // 6. Показания акселерометра (отклонение от 1.0g)
        val overloadValue = formatFloat(overload.coerceIn(OVERLOAD_MIN, OVERLOAD_MAX))

        return "$battery,$temp1,$temp2,$closedState,$currentState,$overloadValue"
    }

    /**
     * Обновляет состояние симулятора согласно текущему сценарию
     */
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

    /**
     * Сценарий обычной работы - медленные изменения параметров
     */
    private fun updateNormalState() {
        // Медленная разрядка батареи
        if (scenarioStep % 400 == 0) batteryPercent = (batteryPercent - 1).coerceAtLeast(0)

        // Небольшие колебания температуры
        tempHot = (tempHot + Random.nextFloat() * 0.4f - 0.2f).coerceIn(20.0f, 30.0f)
        tempCold = (tempCold + Random.nextFloat() * 0.3f - 0.15f).coerceIn(10.0f, 20.0f)

        // Слабые колебания акселерометра
        overload = Random.nextFloat() * 0.3f

        // Редкое открытие/закрытие сумки
        if (Random.nextFloat() < 0.005f) {
            bagClosed = !bagClosed
        }
    }

    /**
     * Сценарий быстрой разрядки батареи
     */
    private fun updateBatteryDrainState() {
        when {
            scenarioStep < 33 -> batteryPercent = (50 - scenarioStep).coerceAtLeast(0)
            scenarioStep < 66 -> batteryPercent = (17 - (scenarioStep - 33)).coerceAtLeast(0)
            scenarioStep < 100 -> batteryPercent = 0
            else -> {
                batteryPercent = 5 // Критический уровень
                setScenario(SimulationScenario.NORMAL)
            }
        }

        tempHot += Random.nextFloat() * 0.2f - 0.1f
        tempCold += Random.nextFloat() * 0.2f - 0.1f
        overload = Random.nextFloat() * 0.2f
    }

    /**
     * Сценарий цикла нагрева
     */
    private fun updateHeatingCycleState() {
        when {
            scenarioStep < 33 -> {
                isHeatActive = false
                tempHot = (25.0f + scenarioStep * 0.3f).coerceAtMost(35.0f)
            }

            scenarioStep < 83 -> {
                isHeatActive = true
                tempHot = (35.0f + (scenarioStep - 33) * 0.4f).coerceAtMost(55.0f)
                // Потребление батареи при нагреве
                if (scenarioStep % 10 == 0) {
                    batteryPercent = (batteryPercent - 1).coerceAtLeast(0)
                }
            }

            scenarioStep < 117 -> {
                isHeatActive = true
                tempHot = (55.0f + Random.nextFloat() * 4f - 2f).coerceIn(53.0f, 57.0f)
            }

            scenarioStep < 150 -> {
                isHeatActive = false
                tempHot = (tempHot - 0.8f).coerceAtLeast(25.0f)
            }

            else -> setScenario(SimulationScenario.NORMAL)
        }

        overload = Random.nextFloat() * 0.3f
    }

    /**
     * Сценарий цикла охлаждения
     */
    private fun updateCoolingCycleState() {
        when {
            scenarioStep < 20 -> {
                coolActive = false
                tempCold = (15.0f - scenarioStep * 0.5f).coerceAtLeast(5.0f)
            }

            scenarioStep < 50 -> {
                coolActive = true
                tempCold = (5.0f - (scenarioStep - 20) * 0.23f).coerceAtLeast(-2.0f)
                // Потребление батареи при охлаждении
                if (scenarioStep % 6 == 0) {
                    batteryPercent = (batteryPercent - 1).coerceAtLeast(0)
                }
            }

            scenarioStep < 70 -> {
                coolActive = true
                tempCold = (-2.0f + Random.nextFloat() * 2f - 1f).coerceIn(-3.0f, -1.0f)
            }

            scenarioStep < 100 -> {
                coolActive = false
                tempCold = (tempCold + 0.8f).coerceAtMost(20.0f)
            }

            else -> setScenario(SimulationScenario.NORMAL)
        }

        overload = Random.nextFloat() * 0.3f
    }

    /**
     * Сценарий частого открытия/закрытия сумки
     */
    private fun updateBagOpeningClosingState() {
        // Переключение каждые ~7.5 секунд
        if (scenarioStep % 25 == 0) bagClosed = !bagClosed

        tempHot += Random.nextFloat() * 0.3f - 0.15f
        tempCold += Random.nextFloat() * 0.3f - 0.15f
        overload = Random.nextFloat() * 0.5f

        if (scenarioStep % 200 == 0) batteryPercent = (batteryPercent - 1).coerceAtLeast(0)

        if (scenarioStep >= 133) setScenario(SimulationScenario.NORMAL)
    }

    /**
     * Сценарий сильной тряски
     */
    private fun updateStrongShakingState() {
        overload = when {
            scenarioStep < 33 -> Random.nextFloat() * 1.5f + 2.0f // Экстремальная
            scenarioStep < 66 -> Random.nextFloat() * 1.0f + 1.0f // Сильная
            scenarioStep < 100 -> Random.nextFloat() * 0.8f + 0.3f // Средняя
            scenarioStep < 117 -> Random.nextFloat() * 0.2f // Затухание
            else -> {
                setScenario(SimulationScenario.NORMAL)
                0.1f
            }
        }

        tempHot += Random.nextFloat() * 0.4f - 0.2f
        tempCold += Random.nextFloat() * 0.4f - 0.2f

        if (scenarioStep % 67 == 0) batteryPercent = (batteryPercent - 1).coerceAtLeast(0)
    }

    /**
     * Сценарий ошибок датчиков
     */
    private fun updateSensorErrorsState() {
        // Ошибки обрабатываются в generateSimulatedData()

        // Обновляем температуры только если не в состоянии ошибки
        if (!shouldSimulateError(10)) {
            tempHot += Random.nextFloat() * 0.5f - 0.25f
        }
        if (!shouldSimulateError(7)) {
            tempCold += Random.nextFloat() * 0.5f - 0.25f
        }

        overload = Random.nextFloat() * 0.4f

        if (scenarioStep % 100 == 0) batteryPercent = (batteryPercent - 1).coerceAtLeast(0)

        if (scenarioStep >= 167) setScenario(SimulationScenario.NORMAL)
    }

    /**
     * Вычисляет количество активных функций
     */
    private fun calculateActiveState(): Int {
        var state = 0
        if (isHeatActive) state++
        if (coolActive) state++
        if (lightActive) state++
        return state
    }

    /**
     * Определяет, нужно ли симулировать ошибку датчика
     */
    private fun shouldSimulateError(period: Int): Boolean {
        return currentScenario == SimulationScenario.SENSOR_ERRORS && scenarioStep % period < 3
    }

    /**
     * Форматирует температуру с использованием US локали
     */
    private fun formatTemperature(temp: Float): String {
        val constrainedTemp = temp.coerceIn(TEMP_MIN, TEMP_MAX)
        return String.format(Locale.US, "%.2f", constrainedTemp)
    }

    /**
     * Форматирует float значение с использованием US локали
     */
    private fun formatFloat(value: Float): String {
        return String.format(Locale.US, "%.2f", value)
    }

    // Методы для ручного управления параметрами (для отладки)

    /**
     * Устанавливает уровень батареи вручную
     */
    fun setBatteryLevel(level: Int) {
        batteryPercent = level.coerceIn(BATTERY_MIN, BATTERY_MAX)
    }

    /**
     * Устанавливает температуры отсеков вручную
     */
    fun setTemperatures(upper: Float, lower: Float) {
        tempHot = upper.coerceIn(TEMP_MIN, TEMP_MAX)
        tempCold = lower.coerceIn(TEMP_MIN, TEMP_MAX)
    }

    /**
     * Запускает имитацию тряски с заданной интенсивностью
     */
    fun triggerShake(intensity: Float) {
        overload = intensity.coerceIn(OVERLOAD_MIN, OVERLOAD_MAX)
    }

    /**
     * Возвращает текущее состояние симулятора
     */
    fun getSimulatorState() = SimulatorState(
        batteryPercent = batteryPercent,
        tempHot = tempHot,
        tempCold = tempCold,
        bagClosed = bagClosed,
        isHeatActive = isHeatActive,
        coolActive = coolActive,
        lightActive = lightActive,
        overload = overload,
        currentScenario = currentScenario,
        scenarioStep = scenarioStep
    )

    /**
     * Data class для состояния симулятора
     */
    data class SimulatorState(
        val batteryPercent: Int,
        val tempHot: Float,
        val tempCold: Float,
        val bagClosed: Boolean,
        val isHeatActive: Boolean,
        val coolActive: Boolean,
        val lightActive: Boolean,
        val overload: Float,
        val currentScenario: SimulationScenario,
        val scenarioStep: Int
    )
}