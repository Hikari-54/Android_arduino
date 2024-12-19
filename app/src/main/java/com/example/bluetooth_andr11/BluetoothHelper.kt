package com.example.bluetooth_andr11

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
    private var isConnected = false
    private var isListening = false // Флаг для предотвращения дублирования корутин

    // Подключение к спаренному устройству
    fun connectToDevice(device: BluetoothDevice, onConnectionResult: (Boolean, String) -> Unit) {
        // Проверяем разрешения
        if (!hasBluetoothPermission()) {
            onConnectionResult(false, "Недостаточно разрешений для подключения")
            return
        }

        val uuid = try {
            // Безопасно извлекаем UUID устройства
            device.uuids?.firstOrNull()?.uuid ?: UUID.randomUUID()
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Недостаточно разрешений для доступа к UUID: ${e.message}")
            onConnectionResult(false, "Недостаточно разрешений для доступа к UUID")
            return
        }

        try {
            // Создаем Bluetooth-сокет
            bluetoothSocket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothHelper",
                    "Недостаточно разрешений для создания сокета: ${e.message}"
                )
                onConnectionResult(false, "Недостаточно разрешений для создания сокета")
                return
            }

            // Подключаемся к устройству
            try {
                bluetoothSocket?.connect()
            } catch (e: SecurityException) {
                Log.e("BluetoothHelper", "Недостаточно разрешений для подключения: ${e.message}")
                onConnectionResult(false, "Недостаточно разрешений для подключения")
                return
            }

            // Устанавливаем потоки для чтения и записи
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            isConnected = true

            // Безопасно получаем имя устройства
            val deviceName = try {
                device.name // Это может выбросить SecurityException
            } catch (e: SecurityException) {
                Log.e(
                    "BluetoothHelper",
                    "Недостаточно разрешений для доступа к имени устройства: ${e.message}"
                )
                "Неизвестное устройство" // Используем безопасное значение по умолчанию
            }

            onConnectionResult(true, "Успешно подключено к $deviceName")
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Ошибка подключения: ${e.message}")
            onConnectionResult(false, "Ошибка подключения: ${e.message}")
            closeConnection() // Закрываем соединение при ошибке
        }
    }


    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }


    // Закрытие соединения
    fun closeConnection() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            isConnected = false
            isListening = false // Сбрасываем флаг при закрытии соединения
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Ошибка при закрытии соединения: ${e.message}")
        }
    }

    // Отправка команды устройству
    fun sendCommand(command: String) {
        if (!isConnected || outputStream == null) {
            Log.e("BluetoothHelper", "Соединение не установлено или поток недоступен")
            return
        }

        try {
            outputStream?.write(command.toByteArray())
        } catch (e: IOException) {
            Log.e("BluetoothHelper", "Ошибка отправки данных: ${e.message}")
        }
    }

    // Чтение данных от устройства
    fun listenForData(onDataReceived: (String) -> Unit) {
        if (!isConnected || inputStream == null || isListening) {
            Log.e("BluetoothHelper", "Соединение не установлено или поток недоступен")
            return
        }

        isListening = true
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (isConnected) {
                try {
                    if (inputStream!!.available() > 0) {
                        bytes = inputStream!!.read(buffer)
                        if (bytes > 0) {
                            val receivedMessage = String(buffer, 0, bytes)
                            withContext(Dispatchers.Main) {
                                onDataReceived(receivedMessage)
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(
                        "BluetoothHelper",
                        "Недостаточно разрешений для чтения данных: ${e.message}"
                    )
                    break
                } catch (e: IOException) {
                    Log.e("BluetoothHelper", "Ошибка чтения данных: ${e.message}")
                    break
                }
            }
            isListening = false // Сбрасываем флаг при завершении
        }
    }

    // Проверка разрешений для Bluetooth
    fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            return permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        return true
    }

    // Получение списка спаренных устройств
    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (!hasBluetoothPermission()) {
            Log.e("BluetoothHelper", "Разрешение для Bluetooth не предоставлено")
            return null
        }

        return try {
            bluetoothAdapter?.bondedDevices
        } catch (e: SecurityException) {
            Log.e("BluetoothHelper", "Недостаточно разрешений для получения спаренных устройств")
            null
        }
    }
}
