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
import com.example.bluetooth_andr11.ArduinoSimulator
import com.example.bluetooth_andr11.BuildConfig
import com.example.bluetooth_andr11.MainActivity
import com.example.bluetooth_andr11.location.EnhancedLocationManager
import com.example.bluetooth_andr11.log.LogModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothHelper(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var isListening = false
    private var isConnected = false
    private var dialogShown = false

    // Поля для симуляции
    private var simulationMode = false
    private var arduinoSimulator: ArduinoSimulator? = null
    private var currentScenario = ArduinoSimulator.SimulationScenario.NORMAL

    private val sharedPrefs =
        context.getSharedPreferences("bluetooth_helper_prefs", Context.MODE_PRIVATE)

    init {
        clearSimulationDataIfRelease() // Сначала очищаем если RELEASE
        restoreSimulationState()       // Потом восстанавливаем если DEBUG
    }

    // === ПУБЛИЧНЫЕ МЕТОДЫ ===

    fun isSimulationEnabled(): Boolean = simulationMode
    fun getCurrentScenario(): ArduinoSimulator.SimulationScenario = currentScenario
    val isDeviceConnected: Boolean get() = if (simulationMode) true else isConnected

    fun showDeviceSelectionDialog(context: Context, onDeviceSelected: (BluetoothDevice) -> Unit) {
        val pairedDevices = getPairedDevices()
        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "Нет спаренных устройств", Toast.LENGTH_SHORT).show()
            dialogShown = false
            return
        }

        val deviceNames = pairedDevices.map { device ->
            getDeviceName(device)
        }
        val deviceList = pairedDevices.toList()

        val builder = AlertDialog.Builder(context)
        builder.setTitle("Выберите Bluetooth-устройство")
        builder.setItems(deviceNames.toTypedArray()) { _, which ->
            onDeviceSelected(deviceList[which])
        }
        builder.setOnDismissListener { dialogShown = false }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    // ✅ ИСПРАВЛЕНО: Добавлена аннотация и дополнительные проверки
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
                // ✅ ДОПОЛНИТЕЛЬНАЯ проверка перед вызовом
                if (!hasBluetoothConnectPermission()) {
                    withContext(Dispatchers.Main) {
                        onConnectionResult(false, "Нет разрешения BLUETOOTH_CONNECT")
                    }
                    return@launch
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                isConnected = true

                withContext(Dispatchers.Main) {
                    val deviceName = getDeviceName(device)
                    onConnectionResult(true, "Подключено к $deviceName")
                    startListening()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка подключения: ${e.message}")
                isConnected = false
                withContext(Dispatchers.Main) {
                    onConnectionResult(false, "Ошибка подключения: ${e.message}")
                }
                closeConnection()
            } catch (e: SecurityException) {
                Log.e(TAG, "Ошибка безопасности: ${e.message}")
                isConnected = false
                withContext(Dispatchers.Main) {
                    onConnectionResult(false, "Нет разрешений Bluetooth")
                }
            }
        }
    }

    fun listenForData(onDataReceived: (String) -> Unit) {
        if (isConnected && inputStream != null) {
            startListening(onDataReceived) // 🔥 ИСПРАВЛЕНО: Передаем параметр
        }
    }

    fun sendCommand(command: String) {
        if (simulationMode) {
            arduinoSimulator?.handleCommand(command)
            return
        }

        if (!isConnected || outputStream == null) {
            Log.w(TAG, "Команда не отправлена: устройство не подключено")
            return
        }

        try {
            outputStream?.write(command.toByteArray())
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка отправки команды: ${e.message}")
        }
    }

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
                        val state =
                            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        val isEnabled = state == BluetoothAdapter.STATE_ON

                        if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                            disconnectDevice()
                            LogModule.logEventWithLocation(
                                context!!,
                                this@BluetoothHelper,
                                locationManager,
                                "Bluetooth выключен"
                            )
                            dialogShown = false
                        }

                        onStatusChange(isEnabled, isConnected)

                        if (isEnabled && !isConnected && !dialogShown) {
                            dialogShown = true
                            showDeviceSelection(context!!)
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        isConnected = true
                        LogModule.logEventWithLocation(
                            context!!, this@BluetoothHelper, locationManager, "Bluetooth подключен"
                        )
                        onStatusChange(true, true)
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        isConnected = false
                        LogModule.logEventWithLocation(
                            context!!, this@BluetoothHelper, locationManager, "Bluetooth отключен"
                        )
                        onStatusChange(true, false)

                        if (isBluetoothEnabled() && !dialogShown) {
                            dialogShown = true
                            showDeviceSelection(context)
                        }
                    }
                }
            }
        }

        context.registerReceiver(receiver, filter)
    }

    fun disconnectDevice() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка отключения: ${e.message}")
        } finally {
            isConnected = false
            isListening = false
            bluetoothSocket = null
            dialogShown = false
        }
    }

    fun closeConnection() = disconnectDevice()

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    // === МЕТОДЫ СИМУЛЯЦИИ ===

    fun enableSimulationMode(enable: Boolean) {
        // 🔥 ИСПРАВЛЕНИЕ: Запрещаем включение симуляции в RELEASE
        if (!BuildConfig.DEBUG && enable) {
            Log.w(TAG, "RELEASE режим: попытка включить симуляцию заблокирована")
            Toast.makeText(context, "Симуляция недоступна в релизной версии", Toast.LENGTH_SHORT)
                .show()
            return
        }

        simulationMode = enable
        sharedPrefs.edit().putBoolean("simulation_enabled", enable).apply()

        if (enable) {
            startArduinoSimulation()
            Log.i(TAG, "Симуляция Arduino включена")
        } else {
            stopArduinoSimulation()
            Log.i(TAG, "Симуляция Arduino выключена")
        }
    }

    fun clearSimulationDataIfRelease() {
        if (!BuildConfig.DEBUG) {
            sharedPrefs.edit()
                .remove("simulation_enabled")
                .remove("current_scenario")
                .apply()

            simulationMode = false
            stopArduinoSimulation()

            Log.i(TAG, "RELEASE режим: все данные симуляции очищены")
        }
    }

    fun setSimulationScenario(scenario: ArduinoSimulator.SimulationScenario) {
        currentScenario = scenario
        sharedPrefs.edit().putString("current_scenario", scenario.name).apply()
        arduinoSimulator?.setScenario(scenario)
    }

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

    // Методы управления симулятором
    fun setSimulationBattery(level: Int) = arduinoSimulator?.setBatteryLevel(level)
    fun setSimulationTemperatures(upper: Float, lower: Float) =
        arduinoSimulator?.setTemperatures(upper, lower)

    fun triggerSimulationShake(intensity: Float) = arduinoSimulator?.triggerShake(intensity)

    // === ПРИВАТНЫЕ МЕТОДЫ ===

    // ✅ ИСПРАВЛЕНО: Добавлена аннотация
    @Suppress("MissingPermission")
    private fun getPairedDevices(): Set<BluetoothDevice>? {
        return try {
            if (hasBluetoothConnectPermission()) {
                bluetoothAdapter?.bondedDevices
            } else {
                Log.w(TAG, "Нет разрешения BLUETOOTH_CONNECT для получения сопряженных устройств")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка доступа к сопряженным устройствам: ${e.message}")
            null
        }
    }

    // ✅ ИСПРАВЛЕНО: Добавлена аннотация
    @Suppress("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String {
        return try {
            if (hasBluetoothConnectPermission()) {
                device.name ?: "Неизвестное устройство"
            } else {
                "Нет доступа к имени"
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка получения имени устройства: ${e.message}")
            "Ошибка доступа"
        }
    }

    // ✅ ИСПРАВЛЕНО: Добавлена аннотация
    @Suppress("MissingPermission")
    private fun getDeviceUuid(device: BluetoothDevice): UUID? {
        return try {
            if (hasBluetoothConnectPermission()) {
                device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
            } else {
                Log.w(TAG, "Нет разрешения для получения UUID")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Ошибка получения UUID: ${e.message}")
            null
        }
    }

    private fun startListening(onDataReceived: ((String) -> Unit)? = null) {
        if (!isConnected || inputStream == null || isListening) return

        isListening = true
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            val dataBuffer = StringBuilder()

            try {
                while (isConnected) {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        dataBuffer.append(String(buffer, 0, bytes))
                        processBufferedData(dataBuffer, onDataReceived)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Ошибка чтения данных: ${e.message}")
                closeConnection()
            } finally {
                isListening = false
            }
        }
    }

    private suspend fun processBufferedData(
        buffer: StringBuilder,
        onDataReceived: ((String) -> Unit)?
    ) {
        val data = buffer.toString()
        val lines = data.split("\n")

        // Обрабатываем все полные строки кроме последней
        for (i in 0 until lines.size - 1) {
            val line = lines[i].trim()
            if (line.isNotEmpty() && isValidArduinoData(line)) {
                withContext(Dispatchers.Main) {
                    onDataReceived?.invoke(line) ?: (context as? MainActivity)?.handleReceivedData(
                        line
                    )
                }
            }
        }

        // Сохраняем неполную строку
        buffer.clear()
        buffer.append(lines.last())

        // Защита от переполнения буфера
        if (buffer.length > 200) {
            buffer.clear()
        }
    }

    private fun isValidArduinoData(data: String): Boolean {
        val parts = data.split(",")
        if (parts.size != 6) return false

        return try {
            val battery = parts[0].trim().toIntOrNull() ?: return false
            val temp1 = parts[1].trim()
            val temp2 = parts[2].trim()
            val closed = parts[3].trim().toIntOrNull() ?: return false
            val state = parts[4].trim().toIntOrNull() ?: return false
            val overload = parts[5].trim().toFloatOrNull()
                ?: return false // 🔥 ИСПРАВЛЕНО: Используем переменную

            battery in 0..100 &&
                    (temp1 == "er" || temp1.toFloatOrNull() != null) &&
                    (temp2 == "er" || temp2.toFloatOrNull() != null) &&
                    closed in 0..1 &&
                    state >= 0 &&
                    overload >= 0.0f // 🔥 ДОБАВЛЕНО: Проверяем overload
        } catch (e: Exception) {
            false
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothConnectPermission() && hasBluetoothScanPermission()
        } else true
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun startArduinoSimulation() {
        arduinoSimulator = ArduinoSimulator { data ->
            (context as? MainActivity)?.handleReceivedData(data)
        }
        arduinoSimulator?.startSimulation()
        isConnected = true
    }

    private fun stopArduinoSimulation() {
        arduinoSimulator?.stopSimulation()
        arduinoSimulator = null
        if (simulationMode) isConnected = false
    }

    private fun restoreSimulationState() {
        // 🔥 ИСПРАВЛЕНИЕ: Симуляция доступна только в DEBUG режиме
        if (!BuildConfig.DEBUG) {
            // В RELEASE режиме принудительно отключаем симуляцию
            sharedPrefs.edit()
                .putBoolean("simulation_enabled", false)
                .apply()
            Log.i(TAG, "RELEASE режим: симуляция принудительно отключена")
            return
        }

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
        }

        if (savedSimulationEnabled) {
            enableSimulationMode(true)
            Log.i(TAG, "DEBUG режим: симуляция восстановлена из настроек")
        }
    }

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

    data class ScenarioInfo(
        val name: String,
        val icon: String,
        val description: String,
        val durationSeconds: Int
    )

    companion object {
        private const val TAG = "BluetoothHelper"
    }
}