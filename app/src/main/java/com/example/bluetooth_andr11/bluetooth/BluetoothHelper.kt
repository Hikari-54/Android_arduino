package com.example.bluetooth_andr11.bluetooth

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.MainActivity
import com.example.bluetooth_andr11.auth.AuthenticationManager
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import com.example.bluetooth_andr11.simulation.ArduinoSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Управляет Bluetooth подключениями к Arduino устройству и симуляцией.
 *
 * Основные функции:
 * - Сканирование и подключение к Bluetooth устройствам
 * - Отправка команд на Arduino (H/h - нагрев, C/c - охлаждение, L/l - свет)
 * - Прослушивание данных от Arduino в формате CSV
 * - Симуляция Arduino для тестирования (только DEBUG режим)
 * - Мониторинг состояния Bluetooth адаптера
 * - Автоматическое переподключение при разрыве связи
 *
 * Поддерживаемые команды Arduino:
 * - "H" - включить нагрев, "h" - выключить нагрев
 * - "C" - включить охлаждение, "c" - выключить охлаждение
 * - "L" - включить подсветку, "l" - выключить подсветку
 *
 * Формат данных от Arduino: "battery,tempHot,tempCold,closed,state,overload"
 */
class BluetoothHelper(private val context: Context) {

    private var authenticationManager: AuthenticationManager? = null

    // === ОЧИСТКА РЕСУРСОВ ===

    /**
     * Очищает все ресурсы BluetoothHelper при закрытии приложения
     */
    fun cleanup() {
        try {
            // Останавливаем симуляцию если она запущена
            stopArduinoSimulation()

            // Отключаем устройство и очищаем потоки
            disconnectDevice()

            // Отменяем все активные операции
            isListening = false

            Log.d(TAG, "🧹 BluetoothHelper полностью очищен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки BluetoothHelper: ${e.message}")
        }
    }

    // === ДОПОЛНИТЕЛЬНЫЕ УТИЛИТЫ ===

    /**
     * Возвращает статистику подключений для отладки
     */
    fun getConnectionStatistics(): String {
        val authStats = authenticationManager?.getAuthenticationStatistics()

        return buildString {
            appendLine("=== BLUETOOTH CONNECTION STATISTICS ===")
            appendLine("Bluetooth включен: ${isBluetoothEnabled()}")
            appendLine("Устройство подключено: $isDeviceConnected")
            appendLine("Режим симуляции: $simulationMode")
            appendLine("Текущий сценарий: $currentScenario")
            appendLine("Прослушивание: $isListening")
            appendLine("Входящий поток: ${inputStream != null}")
            appendLine("Исходящий поток: ${outputStream != null}")
            appendLine()
            appendLine("=== AUTHENTICATION STATUS ===")
            if (authStats != null) {
                appendLine("Аутентифицирован: ${authStats.isAuthenticated}")
                appendLine("ID сумки: ${authStats.currentBagId ?: "Отсутствует"}")
                appendLine("Попыток аутентификации: ${authStats.totalAttempts}")
                appendLine("Успешных: ${authStats.successfulAuthentications}")
            } else {
                appendLine("AuthenticationManager не установлен")
            }
            appendLine("=======================================")
        }
    }

    /**
     * Проверяет готовность системы к работе
     */
    fun isSystemReady(): Boolean {
        return when {
            simulationMode -> true // Симуляция всегда готова
            !isBluetoothEnabled() -> false
            !hasBluetoothPermission() -> false
            !isDeviceConnected -> false
            else -> inputStream != null && outputStream != null
        }
    }

    /**
     * Возвращает человекочитаемый статус подключения
     */
    fun getConnectionStatusDescription(): String {
        return when {
            simulationMode -> "🤖 Режим симуляции активен"
            !isBluetoothEnabled() -> "🔴 Bluetooth выключен"
            !hasBluetoothPermission() -> "🔴 Нет разрешений Bluetooth"
            !isDeviceConnected -> "🟡 Устройство не подключено"
            !isListening -> "🟡 Подключено, ожидание данных"
            else -> "🟢 Подключено и активно"
        }
    }

    // === DATA CLASSES ===

    /**
     * Информация о сценарии симуляции для отображения в UI
     *
     * @param name отображаемое имя сценария
     * @param icon иконка для UI
     * @param description описание сценария
     * @param durationSeconds примерная продолжительность в секундах
     */
    data class ScenarioInfo(
        val name: String,
        val icon: String,
        val description: String,
        val durationSeconds: Int
    )

    /**
     * Статистика подключений для отладки и мониторинга
     */
    data class ConnectionStatistics(
        val isBluetoothEnabled: Boolean,
        val isDeviceConnected: Boolean,
        val isSimulationMode: Boolean,
        val currentScenario: ArduinoSimulator.SimulationScenario,
        val isListening: Boolean,
        val hasInputStream: Boolean,
        val hasOutputStream: Boolean
    ) {
        /**
         * Возвращает краткую сводку состояния
         */
        fun getSummary(): String {
            val mode = if (isSimulationMode) "Симуляция" else "Реальное устройство"
            val status = if (isDeviceConnected) "подключено" else "отключено"
            val listening = if (isListening) "слушает" else "не слушает"
            return "$mode: $status, $listening"
        }

        /**
         * Проверяет, есть ли проблемы с подключением
         */
        fun hasIssues(): Boolean {
            return !isSimulationMode && (!isBluetoothEnabled || !isDeviceConnected || !hasInputStream || !hasOutputStream)
        }
    }

    companion object {
        private const val TAG = "BluetoothHelper"

        /** Поддерживаемые команды Arduino */
        const val COMMAND_HEAT_ON = "H"
        const val COMMAND_HEAT_OFF = "h"
        const val COMMAND_COOL_ON = "C"
        const val COMMAND_COOL_OFF = "c"
        const val COMMAND_LIGHT_ON = "L"
        const val COMMAND_LIGHT_OFF = "l"

        /** Префикс команды аутентификации от сумки */
        private const val AUTHENTICATION_PREFIX = "ID:"

        /** Формат данных от Arduino */
        const val ARDUINO_DATA_FORMAT = "battery,tempHot,tempCold,closed,state,overload"

        /** Количество ожидаемых параметров в данных Arduino */
        const val EXPECTED_PARAMETERS_COUNT = 6

        /** Размер буфера для чтения данных */
        private const val READ_BUFFER_SIZE = 1024

        /** Максимальный размер буфера накопления данных */
        private const val MAX_DATA_BUFFER_SIZE = 200

        /** Задержка между циклами чтения данных (мс) */
        private const val READ_DELAY_MS = 10L

        /** Интервал между логами акселерометра (мс) */
        private const val ACCELEROMETER_LOG_INTERVAL_MS = 2000L
    }

    //    === BLUETOOTH КОМПОНЕНТЫ ===

    /** Bluetooth адаптер устройства */
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    /** Активное Bluetooth соединение */
    private var bluetoothSocket: BluetoothSocket? = null

    /** Поток для чтения данных от Arduino */
    private var inputStream: InputStream? = null

    /** Поток для отправки команд на Arduino */
    private var outputStream: OutputStream? = null

    // === СОСТОЯНИЕ ПОДКЛЮЧЕНИЯ ===

    /** Активно ли прослушивание данных */
    private var isListening = false

    /** Подключено ли устройство */
    private var isConnected = false

    /** Показан ли диалог выбора устройства (для предотвращения дублирования) */
    private var dialogShown = false

    // === СИМУЛЯЦИЯ (DEBUG ONLY) ===

    /** Включен ли режим симуляции Arduino */
    private var simulationMode = false

    /** Экземпляр симулятора Arduino */
    private var arduinoSimulator: ArduinoSimulator? = null

    /** Текущий сценарий симуляции */
    private var currentScenario = ArduinoSimulator.SimulationScenario.NORMAL

    /** SharedPreferences для сохранения настроек симуляции */
    private val sharedPrefs =
        context.getSharedPreferences("bluetooth_helper_prefs", Context.MODE_PRIVATE)

    init {
        // Очищаем данные симуляции в RELEASE режиме для безопасности
        clearSimulationDataIfRelease()
        // Восстанавливаем состояние симуляции в DEBUG режиме
        restoreSimulationState()
    }

    // === ПУБЛИЧНЫЕ СВОЙСТВА ===

    /** Включен ли режим симуляции */
    fun isSimulationEnabled(): Boolean = simulationMode

    /** Текущий сценарий симуляции */
    fun getCurrentScenario(): ArduinoSimulator.SimulationScenario = currentScenario

    /** Подключено ли устройство (учитывает симуляцию) */
    val isDeviceConnected: Boolean
        get() = if (simulationMode) true else isConnected

    // === АУТЕНТИФИКАЦИЯ ===

    /**
     * Устанавливает менеджер аутентификации для обработки ID сообщений от сумок.
     * Вызывается из AppInitializer после создания AuthenticationManager.
     */
    fun setAuthenticationManager(authManager: AuthenticationManager) {
        this.authenticationManager = authManager
        Log.d(TAG, "🔐 AuthenticationManager подключен к BluetoothHelper")
    }

    /**
     * Определяет тип входящего сообщения и направляет его в соответствующий обработчик.
     *
     * @param message входящее сообщение от сумки
     * @return true если сообщение было обработано
     */
    private fun routeIncomingMessage(message: String): Boolean {
        Log.d(
            TAG,
            "🚏 МАРШРУТИЗАЦИЯ: '$message' | Аутентификация: ${
                message.trim().startsWith("ID:")
            } | Arduino частей: ${message.split(",").size}"
        )

        return try {
            when {
                isAuthenticationMessage(message) -> {
                    Log.d(TAG, "🔐 -> Аутентификация")
                    handleAuthenticationMessage(message)
                }

                isValidArduinoData(message) -> {
                    Log.d(TAG, "📊 -> Arduino данные")
                    handleArduinoDataMessage(message)
                }

                else -> {
                    Log.w(TAG, "❓ -> Неизвестный тип")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 ОШИБКА: ${e.message}")
            false
        }
    }

    /**
     * Проверяет, является ли сообщение командой аутентификации.
     */
    private fun isAuthenticationMessage(message: String): Boolean {
        val trimmed = message.trim()
        val result = trimmed.startsWith("ID:")
        Log.d(TAG, "🔍 AUTH CHECK: '$message' -> '$trimmed' -> $result")
        return result
    }

    /**
     * Обрабатывает сообщение аутентификации через AuthenticationManager.
     */
    private fun handleAuthenticationMessage(message: String): Boolean {
        Log.d(TAG, "🔐 AUTH MESSAGE: '$message', Manager: ${authenticationManager != null}")

        return if (authenticationManager != null) {
            Log.d(TAG, "🔐 Передача сообщения аутентификации: '$message'")
            authenticationManager!!.processAuthenticationMessage(message)
        } else {
            Log.e(TAG, "❌ AuthenticationManager не установлен, невозможно обработать: '$message'")
            false
        }
    }

    /**
     * Обрабатывает обычные данные от Arduino через существующую логику.
     */
    private fun handleArduinoDataMessage(message: String): Boolean {
        Log.d(TAG, "📊 Передача Arduino данных в MainActivity: '$message'")

        return try {
            val mainActivity = context as? MainActivity
            if (mainActivity != null) {
                Log.d(TAG, "📊 MainActivity найдена, передаю данные")
                mainActivity.handleReceivedData(message)
                Log.d(TAG, "📊 Данные успешно переданы в MainActivity")
                true
            } else {
                Log.e(TAG, "❌ Context не является MainActivity!")
                Log.e(TAG, "❌ Actual context type: ${context::class.java.simpleName}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 ОШИБКА передачи данных в MainActivity: ${e.message}")
            Log.e(TAG, "💥 Stack trace: ${e.stackTraceToString()}")
            false
        }
    }

    /**
     * Сбрасывает аутентификацию при отключении устройства.
     * Вызывается из существующих методов отключения.
     */
    private fun resetAuthenticationOnDisconnect() {
        authenticationManager?.resetAuthentication()
        Log.d(TAG, "🔄 Аутентификация сброшена при отключении BT")
    }

    // === УПРАВЛЕНИЕ УСТРОЙСТВАМИ ===

    /**
     * Показывает диалог выбора спаренного Bluetooth устройства
     *
     * @param context контекст для отображения диалога
     * @param onDeviceSelected callback с выбранным устройством
     */
    fun showDeviceSelectionDialog(context: Context, onDeviceSelected: (BluetoothDevice) -> Unit) {
        val pairedDevices = getPairedDevices()
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "Нет спаренных устройств", Toast.LENGTH_SHORT).show()
            dialogShown = false
            return
        }

        val deviceNames = pairedDevices.map { device -> getDeviceName(device) }
        val deviceList = pairedDevices.toList()

        AlertDialog.Builder(context).apply {
            setTitle("Выберите Bluetooth-устройство")
            setItems(deviceNames.toTypedArray()) { _, which ->
                onDeviceSelected(deviceList[which])
            }
            setOnDismissListener { dialogShown = false }
            setNegativeButton("Отмена", null)
            show()
        }
    }

    /**
     * Подключается к выбранному Bluetooth устройству
     *
     * @param device устройство для подключения
     * @param onConnectionResult callback с результатом подключения (success, message)
     */
    @Suppress("MissingPermission")
    fun connectToDevice(device: BluetoothDevice, onConnectionResult: (Boolean, String) -> Unit) {
        if (!hasBluetoothPermission()) {
            onConnectionResult(false, "Разрешения Bluetooth отсутствуют")
            return
        }

        val uuid = getDeviceUuid(device) ?: run {
            onConnectionResult(false, "Ошибка получения UUID устройства")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!hasBluetoothConnectPermission()) {
                    withContext(Dispatchers.Main) {
                        onConnectionResult(false, "Нет разрешения BLUETOOTH_CONNECT")
                    }
                    return@launch
                }

                // Создаем и устанавливаем соединение
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()

                // Инициализируем потоки данных
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                isConnected = true

                withContext(Dispatchers.Main) {
                    val deviceName = getDeviceName(device)
                    onConnectionResult(true, "Подключено к $deviceName")
                    startListening()
                }

                Log.i(TAG, "✅ Успешно подключено к ${getDeviceName(device)}")

            } catch (e: IOException) {
                Log.e(TAG, "❌ Ошибка подключения: ${e.message}")
                isConnected = false
                withContext(Dispatchers.Main) {
                    onConnectionResult(false, "Ошибка подключения: ${e.message}")
                }
                closeConnection()
            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Ошибка безопасности: ${e.message}")
                isConnected = false
                withContext(Dispatchers.Main) {
                    onConnectionResult(false, "Нет разрешений Bluetooth")
                }
            }
        }
    }

    // === ОБМЕН ДАННЫМИ ===

    /**
     * Начинает прослушивание данных от подключенного устройства
     *
     * @param onDataReceived callback для обработки полученных данных
     */
    fun listenForData(onDataReceived: (String) -> Unit) {
        if (isConnected && inputStream != null) {
            startListening(onDataReceived)
        }
    }

    /**
     * Отправляет команду на Arduino устройство или симулятор
     *
     * Поддерживаемые команды:
     * - H/h: управление нагревом
     * - C/c: управление охлаждением
     * - L/l: управление подсветкой
     *
     * @param command команда для отправки (одна буква)
     */
    fun sendCommand(command: String) {
        Log.d(TAG, "📤 Отправка команды: '$command'")

        // В режиме симуляции передаем команду симулятору
        if (simulationMode) {
            arduinoSimulator?.handleCommand(command)
            return
        }

        // Проверяем подключение к реальному устройству
        if (!isConnected || outputStream == null) {
            Log.w(TAG, "⚠️ Команда не отправлена: устройство не подключено")
            return
        }

        try {
            // Arduino ожидает команды с символом новой строки
            val commandWithNewline = "$command\n"
            outputStream?.write(commandWithNewline.toByteArray())
            outputStream?.flush()

            Log.d(TAG, "✅ Команда '$command' отправлена успешно")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка отправки команды '$command': ${e.message}")
            isConnected = false
            closeConnection()
        }
    }

    // === МОНИТОРИНГ BLUETOOTH ===

    /**
     * Настраивает мониторинг состояния Bluetooth адаптера и подключений
     *
     * @param context контекст приложения
     * @param locationManager менеджер местоположения для логирования
     * @param onStatusChange callback с изменениями состояния (isEnabled, isConnected)
     */
    fun monitorBluetoothStatus(
        context: Context,
        locationManager: EnhancedLocationManager,
        onStatusChange: (Boolean, Boolean) -> Unit
    ) {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        handleBluetoothStateChange(context, locationManager, intent, onStatusChange)
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        handleDeviceConnected(context, locationManager, onStatusChange)
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        handleDeviceDisconnected(context, locationManager, onStatusChange)
                    }
                }
            }
        }

        context.registerReceiver(receiver, filter)
        Log.d(TAG, "🎧 Bluetooth мониторинг настроен")
    }

    // === УПРАВЛЕНИЕ СОЕДИНЕНИЕМ ===

    /**
     * Отключает текущее устройство и очищает ресурсы
     */
    fun disconnectDevice() {
        resetAuthenticationOnDisconnect()
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "🔌 Устройство отключено")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка отключения: ${e.message}")
        } finally {
            isConnected = false
            isListening = false
            bluetoothSocket = null
            inputStream = null
            outputStream = null
            dialogShown = false
        }
    }

    /**
     * Псевдоним для disconnectDevice() для обратной совместимости
     */
    fun closeConnection() = disconnectDevice()

    /**
     * Проверяет, включен ли Bluetooth адаптер
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    // === СИМУЛЯЦИЯ ARDUINO (DEBUG ONLY) ===

    /**
     * Включает или выключает режим симуляции Arduino
     * В RELEASE режиме симуляция заблокирована для безопасности
     *
     * @param enable true для включения симуляции
     */
    fun enableSimulationMode(enable: Boolean) {
        // Блокируем симуляцию в RELEASE режиме
        if (!BuildConfig.DEBUG && enable) {
            Log.w(TAG, "🚫 RELEASE режим: попытка включить симуляцию заблокирована")
            Toast.makeText(context, "Симуляция недоступна в релизной версии", Toast.LENGTH_SHORT)
                .show()
            return
        }

        simulationMode = enable
        sharedPrefs.edit().putBoolean("simulation_enabled", enable).apply()

        if (enable) {
            startArduinoSimulation()
            Log.i(TAG, "🔧 Симуляция Arduino включена")
        } else {
            stopArduinoSimulation()
            Log.i(TAG, "🔧 Симуляция Arduino выключена")
        }
    }

    /**
     * Очищает все данные симуляции в RELEASE режиме для безопасности
     */
    fun clearSimulationDataIfRelease() {
        if (!BuildConfig.DEBUG) {
            sharedPrefs.edit()
                .remove("simulation_enabled")
                .remove("current_scenario")
                .apply()

            simulationMode = false
            stopArduinoSimulation()

            Log.i(TAG, "🧹 RELEASE режим: все данные симуляции очищены")
        }
    }

    /**
     * Устанавливает сценарий симуляции
     *
     * @param scenario новый сценарий симуляции
     */
    fun setSimulationScenario(scenario: ArduinoSimulator.SimulationScenario) {
        currentScenario = scenario
        sharedPrefs.edit().putString("current_scenario", scenario.name).apply()
        arduinoSimulator?.setScenario(scenario)
        Log.d(TAG, "🎭 Сценарий симуляции изменен на: $scenario")
    }

    /**
     * Возвращает информацию о текущем сценарии симуляции
     */
    fun getScenarioInfo(): ScenarioInfo {
        return when (currentScenario) {
            ArduinoSimulator.SimulationScenario.NORMAL ->
                ScenarioInfo("Обычная работа", "⚪", "Стабильные показатели", 60)

            ArduinoSimulator.SimulationScenario.BATTERY_DRAIN ->
                ScenarioInfo("Разрядка батареи", "🔋", "Быстрая потеря заряда", 30)

            ArduinoSimulator.SimulationScenario.HEATING_CYCLE ->
                ScenarioInfo("Цикл нагрева", "🔥", "Нагрев до 52°C", 45)

            ArduinoSimulator.SimulationScenario.COOLING_CYCLE ->
                ScenarioInfo("Цикл охлаждения", "❄️", "Охлаждение до 4°C", 45)

            ArduinoSimulator.SimulationScenario.BAG_OPENING_CLOSING ->
                ScenarioInfo("Открытие сумки", "📦", "Частые переключения", 40)

            ArduinoSimulator.SimulationScenario.STRONG_SHAKING ->
                ScenarioInfo("Сильная тряска", "📳", "Экстремальные колебания", 35)

            ArduinoSimulator.SimulationScenario.SENSOR_ERRORS ->
                ScenarioInfo("Ошибки датчиков", "⚠️", "Периодические сбои", 50)
        }
    }

    // === УПРАВЛЕНИЕ СИМУЛЯТОРОМ ===

    /**
     * Устанавливает уровень батареи в симуляторе
     */
    fun setSimulationBattery(level: Int) = arduinoSimulator?.setBatteryLevel(level)

    /**
     * Устанавливает температуры отсеков в симуляторе
     */
    fun setSimulationTemperatures(upper: Float, lower: Float) =
        arduinoSimulator?.setTemperatures(upper, lower)

    /**
     * Запускает имитацию тряски в симуляторе
     */
    fun triggerSimulationShake(intensity: Float) = arduinoSimulator?.triggerShake(intensity)

    // === ПРИВАТНЫЕ МЕТОДЫ ===

    /**
     * Получает список спаренных Bluetooth устройств
     */
    @Suppress("MissingPermission")
    private fun getPairedDevices(): Set<BluetoothDevice>? {
        return try {
            if (hasBluetoothConnectPermission()) {
                bluetoothAdapter?.bondedDevices
            } else {
                Log.w(TAG, "⚠️ Нет разрешения BLUETOOTH_CONNECT для получения устройств")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка доступа к спаренным устройствам: ${e.message}")
            null
        }
    }

    /**
     * Получает отображаемое имя Bluetooth устройства
     */
    @Suppress("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothConnectPermission()) {
                device.name ?: "Неизвестное устройство"
            } else {
                "Нет доступа к имени"
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка получения имени устройства: ${e.message}")
            "Ошибка доступа"
        }
    }

    /**
     * Получает UUID устройства для подключения
     */
    @Suppress("MissingPermission")
    private fun getDeviceUuid(device: BluetoothDevice): UUID? {
        return try {
            if (hasBluetoothConnectPermission()) {
                device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
            } else {
                Log.w(TAG, "⚠️ Нет разрешения для получения UUID")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Ошибка получения UUID: ${e.message}")
            null
        }
    }

    /**
     * Запускает прослушивание данных от Arduino в отдельной корутине
     */
    private fun startListening(onDataReceived: ((String) -> Unit)? = null) {
        if (!isConnected || inputStream == null || isListening) return

        isListening = true
        Log.d(TAG, "🎧 Начинаем прослушивание Bluetooth данных")

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            val dataBuffer = StringBuilder()

            try {
                while (isConnected && isListening) {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val newData = String(buffer, 0, bytes)
                        dataBuffer.append(newData)
                        processBufferedData(dataBuffer, onDataReceived)
                    }
                    kotlinx.coroutines.delay(READ_DELAY_MS)
                }
            } catch (e: IOException) {
                Log.e(TAG, "❌ Ошибка чтения данных: ${e.message}")
                withContext(Dispatchers.Main) {
                    isConnected = false
                    closeConnection()
                }
            } finally {
                isListening = false
                Log.d(TAG, "🔇 Прослушивание остановлено")
            }
        }
    }

    /**
     * Обрабатывает буферизованные данные и извлекает валидные строки
     */
    private suspend fun processBufferedData(
        buffer: StringBuilder,
        onDataReceived: ((String) -> Unit)?
    ) {
        val data = buffer.toString()
        val lines = data.split("\n")

        // Обрабатываем все строки кроме последней (она может быть неполной)
        for (i in 0 until lines.size - 1) {
            val line = lines[i].trim()
            if (line.isNotEmpty()) {
                Log.d(TAG, "🔍 Обрабатываем строку: '$line'")

                withContext(Dispatchers.Main) {
                    // ИСПРАВЛЕНИЕ: Используем новую логику маршрутизации
                    val wasProcessed = routeIncomingMessage(line)

                    if (!wasProcessed) {
                        Log.w(TAG, "❌ Строка не обработана через маршрутизацию: '$line'")

                        // Fallback: если есть внешний обработчик, используем его
                        if (onDataReceived != null) {
                            Log.d(TAG, "🔄 Использую fallback обработчик для: '$line'")
                            onDataReceived.invoke(line)
                        } else {
                            // Пустая else ветка
                        }
                    } else {
                        Log.d(TAG, "✅ Строка успешно обработана: '$line'")
                    }
                }
            }
        }

        // Сохраняем последнюю (возможно неполную) строку в буфере
        buffer.clear()
        buffer.append(lines.last())

        // Защита от переполнения буфера
        if (buffer.length > MAX_DATA_BUFFER_SIZE) {
            Log.w(TAG, "⚠️ Буфер переполнен (${buffer.length} символов), очищаем")
            buffer.clear()
        }
    }

    /**
     * Проверяет валидность данных от Arduino
     * Ожидаемый формат: "battery,temp1,temp2,closed,state,overload"
     */
    private fun isValidArduinoData(data: String): Boolean {
        Log.d(TAG, "🔬 Проверка Arduino данных: '$data'")

        val parts = data.split(",")
        Log.d(TAG, "🔬 Количество частей: ${parts.size} (ожидается 6)")

        if (parts.size != 6) {
            Log.w(TAG, "❌ Неверное количество параметров: ${parts.size}")
            return false
        }

        return try {
            // Проверяем каждый параметр с детальным логированием
            val battery = parts[0].trim().toIntOrNull()
            Log.d(TAG, "🔬 Battery: '${parts[0].trim()}' -> $battery")
            if (battery == null || battery !in 0..100) {
                Log.w(TAG, "❌ Неверный battery: $battery")
                return false
            }

            val temp1 = parts[1].trim()
            Log.d(TAG, "🔬 Temp1: '$temp1'")
            if (temp1 != "er" && temp1.toFloatOrNull() == null) {
                Log.w(TAG, "❌ Неверный temp1: '$temp1'")
                return false
            }

            val temp2 = parts[2].trim()
            Log.d(TAG, "🔬 Temp2: '$temp2'")
            if (temp2 != "er" && temp2.toFloatOrNull() == null) {
                Log.w(TAG, "❌ Неверный temp2: '$temp2'")
                return false
            }

            val closed = parts[3].trim().toIntOrNull()
            Log.d(TAG, "🔬 Closed: '${parts[3].trim()}' -> $closed")
            if (closed == null || closed !in 0..1) {
                Log.w(TAG, "❌ Неверный closed: $closed")
                return false
            }

            val state = parts[4].trim().toIntOrNull()
            Log.d(TAG, "🔬 State: '${parts[4].trim()}' -> $state")
            if (state == null || state < 0) {
                Log.w(TAG, "❌ Неверный state: $state")
                return false
            }

            val overload = parts[5].trim().toFloatOrNull()
            Log.d(TAG, "🔬 Overload: '${parts[5].trim()}' -> $overload")
            if (overload == null || overload < 0.0f) {
                Log.w(TAG, "❌ Неверный overload: $overload")
                return false
            }

            Log.d(TAG, "✅ Все параметры Arduino валидны")
            true

        } catch (e: Exception) {
            Log.e(TAG, "💥 Исключение при проверке Arduino данных: ${e.message}")
            false
        }
    }

    // === ОБРАБОТЧИКИ BLUETOOTH СОБЫТИЙ ===

    /**
     * Обрабатывает изменения состояния Bluetooth адаптера
     */
    private fun handleBluetoothStateChange(
        context: Context?,
        locationManager: EnhancedLocationManager,
        intent: Intent,
        onStatusChange: (Boolean, Boolean) -> Unit
    ) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        val isEnabled = state == BluetoothAdapter.STATE_ON

        if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
            disconnectDevice()
            LogModule.logEventWithLocation(
                context!!, this, locationManager, "Bluetooth выключен"
            )
            dialogShown = false
        }

        onStatusChange(isEnabled, isConnected)

        if (isEnabled && !isConnected && !dialogShown) {
            dialogShown = true
            showDeviceSelection(context!!)
        }
    }

    /**
     * Обрабатывает подключение устройства
     */
    private fun handleDeviceConnected(
        context: Context?,
        locationManager: EnhancedLocationManager,
        onStatusChange: (Boolean, Boolean) -> Unit
    ) {
        isConnected = true
        LogModule.logEventWithLocation(
            context!!, this, locationManager, "Bluetooth подключен"
        )
        onStatusChange(true, true)
    }

    /**
     * Обрабатывает отключение устройства
     */
    private fun handleDeviceDisconnected(
        context: Context?,
        locationManager: EnhancedLocationManager,
        onStatusChange: (Boolean, Boolean) -> Unit
    ) {
        // === НОВОЕ: Сброс аутентификации ===
        resetAuthenticationOnDisconnect()

        isConnected = false
        LogModule.logEventWithLocation(
            context!!, this, locationManager, "Bluetooth отключен"
        )
        onStatusChange(true, false)

        if (isBluetoothEnabled() && !dialogShown) {
            dialogShown = true
            showDeviceSelection(context)
        }
    }

    // === ПРОВЕРКА РАЗРЕШЕНИЙ ===

    /**
     * Проверяет наличие всех необходимых Bluetooth разрешений
     */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothConnectPermission() && hasBluetoothScanPermission()
        } else true
    }

    /**
     * Проверяет разрешение BLUETOOTH_CONNECT (Android 12+)
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Проверяет разрешение BLUETOOTH_SCAN (Android 12+)
     */
    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    // === УПРАВЛЕНИЕ СИМУЛЯТОРОМ ===

    /**
     * Запускает симулятор Arduino
     */
    private fun startArduinoSimulation() {
        arduinoSimulator = ArduinoSimulator { data ->
            (context as? MainActivity)?.handleReceivedData(data)
        }
        arduinoSimulator?.startSimulation()
        isConnected = true
        Log.d(TAG, "🤖 Симулятор Arduino запущен")
    }

    /**
     * Останавливает симулятор Arduino
     */
    private fun stopArduinoSimulation() {
        arduinoSimulator?.stopSimulation()
        arduinoSimulator = null
        if (simulationMode) isConnected = false
        Log.d(TAG, "🤖 Симулятор Arduino остановлен")
    }

    /**
     * Восстанавливает состояние симуляции из SharedPreferences
     */
    private fun restoreSimulationState() {
        // В RELEASE режиме принудительно отключаем симуляцию
        if (!BuildConfig.DEBUG) {
            sharedPrefs.edit().putBoolean("simulation_enabled", false).apply()
            Log.i(TAG, "🚫 RELEASE режим: симуляция принудительно отключена")
            return
        }

        // В DEBUG режиме восстанавливаем сохраненные настройки
        val savedSimulationEnabled = sharedPrefs.getBoolean("simulation_enabled", false)
        val savedScenarioName = sharedPrefs.getString(
            "current_scenario",
            ArduinoSimulator.SimulationScenario.NORMAL.name
        )

        try {
            currentScenario =
                ArduinoSimulator.SimulationScenario.valueOf(savedScenarioName ?: "NORMAL")
        } catch (e: IllegalArgumentException) {
            currentScenario = ArduinoSimulator.SimulationScenario.NORMAL
            Log.w(TAG, "⚠️ Неизвестный сценарий '$savedScenarioName', используем NORMAL")
        }

        if (savedSimulationEnabled) {
            enableSimulationMode(true)
            Log.i(TAG, "🔧 DEBUG режим: симуляция восстановлена из настроек")
        }
    }

    /**
     * Показывает диалог выбора устройства в UI потоке
     */
    private fun showDeviceSelection(context: Context) {
        (context as? ComponentActivity)?.runOnUiThread {
            if (!dialogShown) {
                dialogShown = true
                showDeviceSelectionDialog(context) { device ->
                    connectToDevice(device) { _, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        dialogShown = false
                    }
                }
            }
        }
    }
}
