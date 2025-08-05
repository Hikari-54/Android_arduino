package com.example.bluetooth_andr11.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * API клиент для отправки данных на Django сервер
 * Используется для интеграции с веб-панелью мониторинга
 */
class DjangoApiClient(private val context: Context) {

    companion object {
        private const val TAG = "DjangoApiClient"

        // Конфигурация сервера
        private const val SERVER_IP = "192.168.0.230"
        private const val SERVER_PORT = "8000"
        private const val BASE_URL = "http://$SERVER_IP:$SERVER_PORT"

        // TODO ДЛЯ ПРОДАКШЕНА (потом заменить):
        // private const val BASE_URL = "https://yourdomain.com"

        // Токен компании (временно хардкод)
        private const val COMPANY_TOKEN = "46e37066dad427209d8ee48c717c8316563fc96d"

        // Серийный номер сумки
        private const val BAG_SERIAL = "SB001236"

        // Таймауты
        private const val CONNECT_TIMEOUT = 10000 // 10 секунд
        private const val READ_TIMEOUT = 15000    // 15 секунд
    }

    /**
     * Отправляет данные датчиков на Django сервер
     * @param sensorData CSV строка с данными датчиков
     * @param buttonStates состояния кнопок управления
     * @param gpsLat широта GPS
     * @param gpsLon долгота GPS
     * @param gpsAccuracy точность GPS
     * @return true если успешно отправлено
     */
    suspend fun sendSensorData(
        sensorData: String,
        buttonStates: Map<String, Boolean> = emptyMap(),
        gpsLat: Double? = null,
        gpsLon: Double? = null,
        gpsAccuracy: Float? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📤 Отправка данных: $sensorData")

            // Создаем JSON payload
            val jsonData = JSONObject().apply {
                put("bag_serial", BAG_SERIAL)
                put("sensor_data", sensorData)

                // Состояния кнопок
                if (buttonStates.isNotEmpty()) {
                    put("button_states", JSONObject().apply {
                        buttonStates.forEach { (key, value) ->
                            put(key, value)
                        }
                    })
                }

                // GPS данные
                if (gpsLat != null && gpsLon != null) {
                    put("gps_data", JSONObject().apply {
                        put("latitude", gpsLat)
                        put("longitude", gpsLon)
                        put("accuracy", gpsAccuracy ?: 0.0)
                    })
                }
            }

            // Отправляем HTTP POST запрос
            val success = sendHttpRequest(jsonData.toString())

            if (success) {
                Log.d(TAG, "✅ Данные успешно отправлены на Django")
            } else {
                Log.e(TAG, "❌ Ошибка отправки данных на Django")
            }

            success

        } catch (e: Exception) {
            Log.e(TAG, "💥 Исключение при отправке данных: ${e.message}")
            false
        }
    }

    /**
     * Выполняет HTTP POST запрос к Django API
     * @param jsonData JSON строка с данными
     * @return true если запрос успешен
     */
    private suspend fun sendHttpRequest(jsonData: String): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            // Создаем подключение
            val url = URL("$BASE_URL/api/sensor-data/")
            connection = url.openConnection() as HttpURLConnection

            // Настраиваем запрос
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $COMPANY_TOKEN")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            // Отправляем данные
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonData)
                writer.flush()
            }

            // Проверяем ответ
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Читаем ответ для отладки
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "📨 Ответ сервера: $response")
                return@withContext true
            } else {
                // Читаем ошибку
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "❌ HTTP ошибка $responseCode: $errorResponse")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Ошибка HTTP запроса: ${e.message}")
            return@withContext false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Проверяет доступность Django сервера
     * @return true если сервер доступен
     */
    suspend fun checkServerHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/dashboard/")
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Authorization", "Bearer $COMPANY_TOKEN")
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            val isHealthy = responseCode == HttpURLConnection.HTTP_OK
            Log.d(TAG, if (isHealthy) "✅ Django сервер доступен" else "❌ Django сервер недоступен")

            isHealthy

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка проверки сервера: ${e.message}")
            false
        }
    }

    /**
     * Получает информацию о конфигурации для отладки
     */
    fun getConfigInfo(): String {
        return """
            Django API Configuration:
            - Server: $BASE_URL
            - Bag Serial: $BAG_SERIAL
            - Token: ${COMPANY_TOKEN.take(10)}...
            - Timeout: ${CONNECT_TIMEOUT}ms
        """.trimIndent()
    }
}