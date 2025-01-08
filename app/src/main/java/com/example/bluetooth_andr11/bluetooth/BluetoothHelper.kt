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
import com.example.bluetooth_andr11.MainActivity
import com.example.bluetooth_andr11.location.LocationManager
import com.example.bluetooth_andr11.log.LogModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private var isListening = false // Prevent duplicate coroutine starts
    private var isConnected = false

    private var dialogShown = false // Чтобы не показывать диалог слишком часто

    // Check if Bluetooth is enabled
    val isDeviceConnected: Boolean
        get() = isConnected

    // Получить список сопряженных устройств
    private fun getPairedDevices(): Set<BluetoothDevice>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter?.bondedDevices
                } else {
                    Log.e("BluetoothHelper", "Разрешение BLUETOOTH_CONNECT отсутствует")
                    null
                }
            } else {
                bluetoothAdapter?.bondedDevices
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Ошибка доступа к сопряженным устройствам: ${e.message}")
            null
        }
    }

    // Отключение устройства при выключении Bluetooth
    fun disconnectDevice() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
            isListening = false
            Log.d("BluetoothHelper", "Устройство отключено")
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Ошибка при отключении устройства: ${e.message}")
        } finally {
            bluetoothSocket = null
            dialogShown = false // Сбрасываем флаг
        }
    }


    fun showDeviceSelectionDialog(context: Context, onDeviceSelected: (BluetoothDevice) -> Unit) {
        val pairedDevices = try {
            getPairedDevices()
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Ошибка доступа к устройствам: ${e.message}")
            Toast.makeText(context, "Нет доступа к спаренным устройствам", Toast.LENGTH_SHORT)
                .show()
            dialogShown = false // Сбрасываем флаг, если произошла ошибка
            return
        }

        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(context, "Нет спаренных устройств", Toast.LENGTH_SHORT).show()
            dialogShown = false // Сбрасываем флаг, если устройств нет
            return
        }

        // Обрабатываем доступ к именам устройств с проверкой разрешения
        val deviceNames = pairedDevices.map { device ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(
                            context, Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        device.name ?: "Неизвестное устройство"
                    } else {
                        "Нет доступа к имени устройства"
                    }
                } else {
                    device.name ?: "Неизвестное устройство"
                }
            } catch (e: SecurityException) {
                Log.e("BluetoothHelper", "Ошибка доступа к имени устройства: ${e.message}")
                "Ошибка доступа"
            }
        }

        val deviceList = pairedDevices.toList()

        // Отображение диалогового окна
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Выберите Bluetooth-устройство")
        builder.setItems(deviceNames.toTypedArray()) { _, which ->
            onDeviceSelected(deviceList[which])
        }
        builder.setOnDismissListener {
            dialogShown = false // Сбрасываем флаг при закрытии диалога
        }
        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    fun connectToDevice(device: BluetoothDevice, onConnectionResult: (Boolean, String) -> Unit) {
        if (!hasBluetoothPermission()) {
            onConnectionResult(false, "Разрешения Bluetooth отсутствуют")
            return
        }

        val uuid = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
                } else {
                    onConnectionResult(false, "Разрешения BLUETOOTH_CONNECT отсутствуют")
                    return
                }
            } else {
                device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Ошибка доступа к UUID: ${e.message}")
            onConnectionResult(false, "Ошибка доступа к UUID")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)

                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                isConnected = true

                withContext(Dispatchers.Main) {
                    onConnectionResult(
                        true,
                        "Подключено к ${device.name ?: "Неизвестное устройство"}"
                    )
                    listenForDataSafely()
                }
            } catch (e: IOException) {
                Log.e("BluetoothHelper", "Ошибка подключения: ${e.message}")
                isConnected = false
                withContext(Dispatchers.Main) {
                    onConnectionResult(false, "Ошибка подключения: ${e.message}")
                }
                closeConnection()
            }
        }
    }

    fun listenForDataSafely() {
        if (isConnected && inputStream != null) {
            listenForData { data ->
                Log.d("BluetoothHelper", "Полученные данные: $data")
                (context as? MainActivity)?.handleReceivedData(data)
            }
        } else {
            Log.e("BluetoothHelper", "Не удалось начать прослушивание, устройство не подключено")
        }
    }


    // Подключиться к устройству
//    fun connectToDevice(device: BluetoothDevice, onConnectionResult: (Boolean, String) -> Unit) {
//        if (!hasBluetoothPermission()) {
//            onConnectionResult(false, "Разрешения Bluetooth отсутствуют")
//            return
//        }
//
//        // Проверяем наличие UUID у устройства
//        val uuid = try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                if (ContextCompat.checkSelfPermission(
//                        context, Manifest.permission.BLUETOOTH_CONNECT
//                    ) == PackageManager.PERMISSION_GRANTED
//                ) {
//                    device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
//                } else {
//                    onConnectionResult(false, "Разрешения BLUETOOTH_CONNECT отсутствуют")
//                    return
//                }
//            } else {
//                device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
//            }
//        } catch (e: SecurityException) {
//            Log.e("BluetoothHelper", "Ошибка доступа к UUID: ${e.message}")
//            onConnectionResult(false, "Ошибка доступа к UUID")
//            return
//        }
//
//        // Запускаем сопряжение в отдельной корутине
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                bluetoothSocket = try {
//                    device.createRfcommSocketToServiceRecord(uuid)
//                } catch (e: SecurityException) {
//                    Log.e("BluetoothHelper", "Ошибка создания сокета: ${e.message}")
//                    withContext(Dispatchers.Main) {
//                        onConnectionResult(false, "Ошибка создания сокета")
//                    }
//                    return@launch
//                }
//
//                // Проверка разрешения перед вызовом connect()
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                    if (ContextCompat.checkSelfPermission(
//                            context, Manifest.permission.BLUETOOTH_CONNECT
//                        ) != PackageManager.PERMISSION_GRANTED
//                    ) {
//                        withContext(Dispatchers.Main) {
//                            onConnectionResult(false, "Разрешения BLUETOOTH_CONNECT отсутствуют")
//                        }
//                        return@launch
//                    }
//                }
//
//                bluetoothSocket?.connect()
//
//                inputStream = bluetoothSocket?.inputStream
//                outputStream = bluetoothSocket?.outputStream
//                isConnected = true
//
//                withContext(Dispatchers.Main) {
//                    onConnectionResult(
//                        true, "Подключено к ${device.name ?: "Неизвестное устройство"}"
//                    )
//                    startListeningForData()
//                }
//            } catch (e: IOException) {
//                Log.e("BluetoothHelper", "Ошибка подключения: ${e.message}")
//                isConnected = false
//                withContext(Dispatchers.Main) {
//                    onConnectionResult(false, "Ошибка подключения: ${e.message}")
//                }
//                closeConnection()
//            }
//        }
//    }

    // Send command to connected device
    fun sendCommand(command: String) {
        if (!isConnected || outputStream == null) {
            Log.e("BluetoothHelper", "Not connected or output stream unavailable")
            return
        }

        try {
            outputStream?.write(command.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Error sending command: ${e.message}")
        }
    }

    // Listen for incoming data from the connected device
    fun listenForData(onDataReceived: (String) -> Unit) {
        if (!isConnected || inputStream == null || isListening) {
            Log.e("BluetoothHelper", "Прослушивание невозможно: устройство не подключено")
            return
        }

        isListening = true
        var retryCount = 0
        val maxRetries = 3 // Максимальное количество попыток переподключения

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            try {
                while (isConnected) {
                    val bytes = inputStream?.read(buffer) ?: break
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes)
                        withContext(Dispatchers.Main) {
                            onDataReceived(data)
                        }
                    }
                }
            } catch (e: IOException) {
                if (retryCount < maxRetries) {
                    retryCount++
                    Log.e(
                        "BluetoothHelper",
                        "Ошибка чтения данных: ${e.message}. Попытка ${retryCount} из $maxRetries"
                    )
                    delay(1000) // Задержка перед повторной попыткой
                    listenForData(onDataReceived) // Перезапуск прослушивания
                } else {
                    Log.e(
                        "BluetoothHelper",
                        "Превышено количество попыток перезапуска прослушивания"
                    )
                    closeConnection()
                }
            } finally {
                isListening = false
                if (isConnected) {
                    Log.d("BluetoothHelper", "Перезапуск прослушивания после разрыва")
                    listenForData(onDataReceived) // Перезапуск прослушивания
                }
            }
        }
    }


    private fun startListeningForData() {
        if (isConnected && inputStream != null && !isListening) {
            listenForData { data ->
                Log.d("BluetoothHelper", "Полученные данные: $data")
            }
        } else {
            Log.e("BluetoothHelper", "Не удалось начать прослушивание: устройство не подключено")
        }
    }


    // Close the Bluetooth connection
    fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Ошибка при закрытии соединения: ${e.message}")
        } finally {
            isConnected = false
            isListening = false
            bluetoothSocket = null
            Log.d("BluetoothHelper", "Соединение закрыто")
        }
    }

    // Check Bluetooth permissions
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = listOf(
                Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN
            )
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            true
        }
    }

    fun monitorBluetoothStatus(
        context: Context,
        locationManager: LocationManager,
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
                    // Изменение состояния Bluetooth-адаптера
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state =
                            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        val isEnabled = state == BluetoothAdapter.STATE_ON

                        if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                            disconnectDevice()
                            logBluetoothEvent(context!!, locationManager, "Bluetooth выключен")
                            dialogShown = false
                        }

                        // Обновляем состояние
                        onStatusChange(isEnabled, isConnected)

                        // Если Bluetooth включен и соединение отсутствует
                        if (isEnabled) {
                            if (!isConnected && !dialogShown) {
                                dialogShown = true
                                showDeviceSelection(context!!)
                            }
                        }
                    }

                    // Подключение устройства
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        isConnected = true
                        logBluetoothEvent(
                            context!!, locationManager, "Bluetooth соединение установлено"
                        )
                        onStatusChange(true, true)
                    }

                    // Отключение устройства
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        isConnected = false
                        logBluetoothEvent(
                            context!!, locationManager, "Bluetooth соединение потеряно"
                        )
                        onStatusChange(true, false)

                        // Если Bluetooth включен, но устройство отключено — показываем диалог подключения
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


    private fun logBluetoothEvent(
        context: Context, locationManager: LocationManager, event: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val coordinates = locationManager.getCurrentCoordinates()
            val logMessage = if (coordinates.isEmpty()) {
                "$event @ Координаты недоступны"
            } else {
                "$event @ $coordinates"
            }

            LogModule.logEvent(context, logMessage)
        }
    }


    private fun showDeviceSelection(context: Context?) {
        (context as? ComponentActivity)?.runOnUiThread {
            if (!dialogShown) {
                dialogShown = true
                showDeviceSelectionDialog(context) { device ->
                    connectToDevice(device) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        dialogShown = false
                    }
                }
            }
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
}
