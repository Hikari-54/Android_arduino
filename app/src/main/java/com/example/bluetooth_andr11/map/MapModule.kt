package com.example.bluetooth_andr11.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.bluetooth_andr11.log.LogModule
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Модуль для работы с картами OpenStreetMap с оптимизированным управлением ресурсами.
 *
 * Основные функции:
 * - Инициализация карт с настройкой кэширования тайлов
 * - Отображение местоположения пользователя с кастомным индикатором направления
 * - Управление маркерами с автоматической очисткой при превышении лимита
 * - Кастомная отрисовка GPS индикатора с кругом точности и тенью
 * - Автоматическая очистка ресурсов через Lifecycle
 * - Оптимизация производительности и управление памятью
 * - Интеграция с LogModule для записи событий местоположения
 *
 * Архитектурные принципы:
 * - Lifecycle-aware компонент для корректной очистки ресурсов
 * - Thread-safe операции с использованием ConcurrentHashMap
 * - Оптимизированная отрисовка с антиалиасингом и тенями
 * - Автоматическое управление количеством маркеров для производительности
 * - Fallback механизмы при ошибках инициализации
 */
object MapModule : DefaultLifecycleObserver {
    private const val TAG = "MapModule"

    // === НАСТРОЙКИ КАРТЫ ===

    /** Максимальное количество маркеров на карте для оптимизации производительности */
    private const val MAX_MARKERS = 50

    /** Уровень зума по умолчанию */
    private const val DEFAULT_ZOOM = 14.0

    /** Координаты Москвы для начальной позиции карты */
    private const val MOSCOW_LAT = 55.751244
    private const val MOSCOW_LON = 37.618423

    // === НАСТРОЙКИ ОТРИСОВКИ ===

    /** Размер треугольного индикатора местоположения */
    private const val LOCATION_INDICATOR_SIZE = 40f

    /** Размер оффсета для боковых углов треугольника */
    private const val DIRECTION_INDICATOR_OFFSET = 30f

    /** Цвет индикатора местоположения */
    private const val LOCATION_INDICATOR_COLOR = "#4CAF50"

    /** Радиус центральной точки индикатора */
    private const val CENTER_DOT_RADIUS = 8f

    /** Размер тени для индикатора */
    private const val SHADOW_RADIUS = 4f

    // === НАСТРОЙКИ GPS ===

    /** Интервал обновления местоположения в миллисекундах */
    private const val LOCATION_UPDATE_INTERVAL = 15000L // 15 секунд

    /** Минимальный интервал обновления в миллисекундах */
    private const val MIN_LOCATION_UPDATE_INTERVAL = 5000L // 5 секунд

    /** Максимальная задержка обновления в миллисекундах */
    private const val MAX_LOCATION_UPDATE_DELAY = 30000L // 30 секунд

    // === НАСТРОЙКИ КЭША ===

    /** Максимальный размер кэша тайлов в байтах (100MB) */
    private const val MAX_TILE_CACHE_SIZE = 100L * 1024 * 1024

    /** Размер для очистки кэша в байтах (80MB) */
    private const val TILE_CACHE_TRIM_SIZE = 80L * 1024 * 1024

    // === СОСТОЯНИЕ МОДУЛЯ ===

    /** Реестр активных карт для управления ресурсами */
    private val activeMapViews = ConcurrentHashMap<Int, MapView>()

    /** Overlay для отображения местоположения пользователя */
    private var myLocationOverlay: MyLocationNewOverlay? = null

    /** Callback для обновлений местоположения */
    private var locationCallback: LocationCallback? = null

    /** Флаг инициализации модуля */
    private var isInitialized = false

    /** Последнее направление движения для отрисовки стрелки */
    private var lastBearing = 0f

    /** Счетчик маркеров для контроля количества */
    private var markerCount = 0

    // === ПУБЛИЧНЫЕ МЕТОДЫ ИНИЦИАЛИЗАЦИИ ===

    /**
     * Инициализирует новую карту с оптимизированными настройками производительности
     *
     * @param context контекст приложения для настройки кэша и конфигурации
     * @return полностью настроенный экземпляр MapView, готовый к использованию
     */
    fun initializeMap(context: Context): MapView {
        // Инициализируем OSMDroid только один раз
        if (!isInitialized) {
            configureOsmDroid(context)
            isInitialized = true
        }

        val mapView = MapView(context).apply {
            // Настройка источника тайлов - используем стандартный Mapnik
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

            // Включаем мультитач жесты для зума и перемещения
            setMultiTouchControls(true)

            // Устанавливаем начальную позицию на Москву
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(MOSCOW_LAT, MOSCOW_LON))

            // Оптимизация производительности
            setScrollableAreaLimitDouble(null) // Убираем ограничения прокрутки
            isHorizontalMapRepetitionEnabled = false // Отключаем повтор карты
            isVerticalMapRepetitionEnabled = false

            // Дополнительные настройки производительности
            setUseDataConnection(true) // Разрешаем загрузку тайлов
            setBuiltInZoomControls(false) // Отключаем встроенные кнопки зума
            isTilesScaledToDpi = true // Масштабирование под DPI экрана
        }

        // Регистрируем карту в реестре для управления ресурсами
        val mapId = mapView.hashCode()
        activeMapViews[mapId] = mapView

        Log.d(TAG, "🗺️ Карта инициализирована с ID: $mapId")
        return mapView
    }

    /**
     * Инициализирует overlay для отображения местоположения пользователя
     * с кастомной отрисовкой направления и точности
     *
     * @param context контекст приложения для получения разрешений
     * @param mapView карта для добавления overlay местоположения
     */
    fun initializeLocationOverlay(context: Context, mapView: MapView) {
        try {
            // Удаляем предыдущий overlay во избежание дублирования
            cleanupLocationOverlay(mapView)

            val locationProvider = GpsMyLocationProvider(context)

            // Создаем кастомный overlay с переопределенной отрисовкой
            myLocationOverlay = object : MyLocationNewOverlay(locationProvider, mapView) {
                override fun drawMyLocation(
                    canvas: Canvas?,
                    projection: Projection?,
                    lastFix: Location?
                ) {
                    drawCustomLocationIndicator(canvas, projection, lastFix)
                }
            }.apply {
                enableMyLocation()
                enableFollowLocation()

                // Настройки overlay (используем стандартную стрелку направления)
                setEnableAutoStop(false) // Не останавливаем автоматически при потере сигнала
            }

            // Добавляем overlay на карту
            mapView.overlays.add(myLocationOverlay)

            // Настраиваем обновления местоположения для логирования
            setupLocationUpdates(context)

            Log.d(TAG, "📍 Location overlay успешно инициализирован")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Нет разрешений для location overlay: ${e.message}")
            Toast.makeText(
                context,
                "Нет разрешения на доступ к местоположению",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка инициализации location overlay: ${e.message}")
            Toast.makeText(
                context,
                "Ошибка инициализации GPS: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // === ПРИВАТНЫЕ МЕТОДЫ ИНИЦИАЛИЗАЦИИ ===

    /**
     * Настраивает конфигурацию OSMDroid для оптимального кэширования тайлов
     */
    private fun configureOsmDroid(context: Context) {
        try {
            val config = Configuration.getInstance()
            config.userAgentValue = context.packageName

            // Настройка путей для кэша тайлов
            val osmBasePath = context.getExternalFilesDir("osmdroid")
            if (osmBasePath != null) {
                config.osmdroidBasePath = osmBasePath
                config.osmdroidTileCache = File(osmBasePath, "tiles")

                // Создаем директории если они не существуют
                if (!config.osmdroidTileCache.exists()) {
                    config.osmdroidTileCache.mkdirs()
                }
            }

            // Настройки кэша для экономии трафика и улучшения производительности
            config.tileFileSystemCacheMaxBytes = MAX_TILE_CACHE_SIZE
            config.tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_SIZE

            Log.d(TAG, "🔧 OSMDroid сконфигурирован. Кэш: ${config.osmdroidTileCache}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка конфигурации OSMDroid: ${e.message}")
            // Продолжаем работу с настройками по умолчанию
        }
    }

    /**
     * Настраивает обновления местоположения для логирования событий
     */
    private fun setupLocationUpdates(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(MIN_LOCATION_UPDATE_INTERVAL)
            setMaxUpdateDelayMillis(MAX_LOCATION_UPDATE_DELAY)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    // Логируем только значимые изменения местоположения
                    LogModule.logLocation(context, location)
                    Log.d(
                        TAG,
                        "📍 Местоположение: ${location.latitude}, ${location.longitude} (±${location.accuracy.toInt()}м)"
                    )
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                if (!locationAvailability.isLocationAvailable) {
                    Log.w(TAG, "⚠️ Местоположение временно недоступно")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.d(TAG, "📱 GPS обновления настроены (интервал: ${LOCATION_UPDATE_INTERVAL}мс)")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Нет разрешения для GPS обновлений: ${e.message}")
            Toast.makeText(
                context,
                "Нет разрешения на доступ к местоположению",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // === КАСТОМНАЯ ОТРИСОВКА ===

    /**
     * Отрисовывает кастомный индикатор местоположения в виде треугольной стрелки
     * с направлением движения, центральной точкой и кругом точности
     */
    private fun drawCustomLocationIndicator(
        canvas: Canvas?,
        projection: Projection?,
        lastFix: Location?
    ) {
        lastFix?.let { location ->
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            val screenCoords = Point()
            projection?.toPixels(geoPoint, screenCoords)

            // Определяем направление движения
            val direction = if (location.hasBearing()) {
                location.bearing
            } else {
                lastBearing // Используем последнее известное направление
            }
            lastBearing = direction

            // Рисуем круг точности если данные доступны
            if (location.hasAccuracy()) {
                drawAccuracyCircle(canvas, projection, geoPoint, location.accuracy)
            }

            // Создаем треугольную стрелку для индикации направления
            val trianglePath = Path().apply {
                moveTo(
                    screenCoords.x.toFloat(),
                    screenCoords.y.toFloat() - LOCATION_INDICATOR_SIZE
                )
                lineTo(
                    screenCoords.x.toFloat() - DIRECTION_INDICATOR_OFFSET,
                    screenCoords.y.toFloat() + DIRECTION_INDICATOR_OFFSET
                )
                lineTo(
                    screenCoords.x.toFloat() + DIRECTION_INDICATOR_OFFSET,
                    screenCoords.y.toFloat() + DIRECTION_INDICATOR_OFFSET
                )
                close()
            }

            // Настройка кисти для треугольника с тенью
            val trianglePaint = Paint().apply {
                color = Color.parseColor(LOCATION_INDICATOR_COLOR)
                style = Paint.Style.FILL
                isAntiAlias = true
                setShadowLayer(SHADOW_RADIUS, 2f, 2f, Color.argb(100, 0, 0, 0))
            }

            // Отрисовка с поворотом по направлению движения
            canvas?.save()
            canvas?.rotate(direction, screenCoords.x.toFloat(), screenCoords.y.toFloat())
            canvas?.drawPath(trianglePath, trianglePaint)

            // Рисуем центральную белую точку
            val centerPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
                setShadowLayer(2f, 1f, 1f, Color.argb(50, 0, 0, 0))
            }
            canvas?.drawCircle(
                screenCoords.x.toFloat(),
                screenCoords.y.toFloat(),
                CENTER_DOT_RADIUS,
                centerPaint
            )

            canvas?.restore()
        }
    }

    /**
     * Рисует полупрозрачный круг точности вокруг местоположения
     */
    private fun drawAccuracyCircle(
        canvas: Canvas?,
        projection: Projection?,
        center: GeoPoint,
        accuracyMeters: Float
    ) {
        // Кисть для заливки круга точности
        val accuracyPaint = Paint().apply {
            color = Color.argb(50, 76, 175, 80) // Полупрозрачный зеленый
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Кисть для границы круга точности
        val borderPaint = Paint().apply {
            color = Color.argb(100, 76, 175, 80) // Более насыщенный зеленый
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        val screenCenter = Point()
        projection?.toPixels(center, screenCenter)

        // Преобразуем метры в пиксели (приближенно для зума 14)
        // Ограничиваем максимальный размер для производительности
        val pixelRadius = (accuracyMeters / 10).coerceAtMost(200f)

        // Рисуем заливку круга
        canvas?.drawCircle(
            screenCenter.x.toFloat(),
            screenCenter.y.toFloat(),
            pixelRadius,
            accuracyPaint
        )

        // Рисуем границу круга
        canvas?.drawCircle(
            screenCenter.x.toFloat(),
            screenCenter.y.toFloat(),
            pixelRadius,
            borderPaint
        )
    }

    // === УПРАВЛЕНИЕ МАРКЕРАМИ ===

    /**
     * Очищает все маршруты и маркеры, сохраняя overlay местоположения
     */
    fun clearRoute(mapView: MapView) {
        try {
            // Сохраняем location overlay перед очисткой
            val locationOverlay =
                mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()

            // Очищаем все overlays
            mapView.overlayManager.clear()

            // Возвращаем location overlay обратно
            locationOverlay?.let { mapView.overlays.add(it) }

            // Обновляем отображение и сбрасываем счетчик
            mapView.invalidate()
            markerCount = 0

            Log.d(TAG, "🧹 Маршрут очищен, location overlay сохранен")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка очистки маршрута: ${e.message}")
        }
    }

    /**
     * Создает стилизованный зеленый маркер для отметки событий на карте
     *
     * @param mapView карта для привязки маркера
     * @param latitude широта точки
     * @param longitude долгота точки
     * @param title заголовок маркера для отображения
     * @return полностью настроенный маркер
     */
    fun createGreenMarker(
        mapView: MapView,
        latitude: Double,
        longitude: Double,
        title: String = "Местоположение события"
    ): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title

            // Создаем и настраиваем зеленую иконку
            try {
                val drawable = ContextCompat.getDrawable(
                    mapView.context,
                    android.R.drawable.ic_menu_mylocation
                )?.mutate() // mutate() для безопасного изменения цвета

                drawable?.let {
                    // Окрашиваем в зеленый цвет
                    it.setTint(
                        ContextCompat.getColor(
                            mapView.context,
                            android.R.color.holo_green_dark
                        )
                    )
                    setIcon(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Не удалось создать зеленую иконку: ${e.message}")
                // Продолжаем с иконкой по умолчанию
            }

            // Отключаем стандартные всплывающие окна для производительности
            setInfoWindow(null)
        }
    }

    /**
     * Добавляет маркер на карту с автоматическим управлением количеством
     *
     * @param mapView карта для добавления маркера
     * @param latitude широта точки
     * @param longitude долгота точки
     * @param title заголовок маркера
     * @param snippet дополнительная информация (необязательно)
     * @return true если маркер добавлен успешно
     */
    fun addMarker(
        mapView: MapView,
        latitude: Double,
        longitude: Double,
        title: String = "Местоположение события",
        snippet: String? = null
    ): Boolean {
        return try {
            // Проверяем лимит маркеров и удаляем старые при необходимости
            if (markerCount >= MAX_MARKERS) {
                removeOldestMarker(mapView)
            }

            // Создаем и настраиваем маркер
            val marker = createGreenMarker(mapView, latitude, longitude, title).apply {
                this.snippet = snippet
            }

            // Добавляем на карту и обновляем счетчик
            mapView.overlays.add(marker)
            mapView.invalidate()
            markerCount++

            Log.d(TAG, "📍 Маркер добавлен: $title ($latitude, $longitude)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка добавления маркера: ${e.message}")
            false
        }
    }

    /**
     * Удаляет самый старый маркер для освобождения места под новые
     */
    private fun removeOldestMarker(mapView: MapView) {
        val markers = mapView.overlays.filterIsInstance<Marker>()
        if (markers.isNotEmpty()) {
            mapView.overlays.remove(markers.first())
            markerCount--
            Log.d(TAG, "🗑️ Удален старый маркер (лимит: $MAX_MARKERS)")
        }
    }

    // === УПРАВЛЕНИЕ КАРТОЙ ===

    /**
     * Включает режим автоматического следования за местоположением пользователя
     */
    fun enableFollowLocationOverlay(mapView: MapView) {
        val overlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        overlay?.apply {
            enableFollowLocation()
            myLocation?.let { location ->
                mapView.controller.animateTo(location)
                Log.d(TAG, "👁️ Включено следование за местоположением")
            }
        } ?: Log.w(TAG, "⚠️ Location overlay не найден для включения следования")
    }

    /**
     * Отключает режим автоматического следования за местоположением
     */
    fun disableFollowLocationOverlay(mapView: MapView) {
        val overlay = mapView.overlays.filterIsInstance<MyLocationNewOverlay>().firstOrNull()
        overlay?.disableFollowLocation()
        Log.d(TAG, "👁️ Отключено следование за местоположением")
    }

    /**
     * Центрирует карту на указанных координатах с плавной анимацией
     *
     * @param mapView карта для центрирования
     * @param latitude широта центра
     * @param longitude долгота центра
     * @param zoom уровень зума (по умолчанию DEFAULT_ZOOM)
     */
    fun centerMapOn(
        mapView: MapView,
        latitude: Double,
        longitude: Double,
        zoom: Double = DEFAULT_ZOOM
    ) {
        try {
            val point = GeoPoint(latitude, longitude)
            mapView.controller.setZoom(zoom)
            mapView.controller.animateTo(point) // Плавная анимация перемещения
            Log.d(TAG, "🎯 Карта центрирована на: $latitude, $longitude (зум: $zoom)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка центрирования карты: ${e.message}")
        }
    }

    /**
     * Получает текущий центр карты
     *
     * @param mapView карта для получения центра
     * @return координаты центра карты или null при ошибке
     */
    fun getMapCenter(mapView: MapView): GeoPoint? {
        return try {
            mapView.mapCenter as? GeoPoint
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения центра карты: ${e.message}")
            null
        }
    }

    // === ОЧИСТКА РЕСУРСОВ ===

    /**
     * Удаляет location overlay с карты и освобождает ресурсы
     */
    private fun cleanupLocationOverlay(mapView: MapView) {
        myLocationOverlay?.let { overlay ->
            mapView.overlays.remove(overlay)
            overlay.disableMyLocation()
            overlay.disableFollowLocation()
        }
        myLocationOverlay = null
    }

    /**
     * Останавливает обновления местоположения и освобождает callback
     */
    fun disableLocationUpdates(context: Context) {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            locationCallback?.let { callback ->
                fusedLocationClient.removeLocationUpdates(callback)
                Log.d(TAG, "🛑 GPS обновления остановлены")
            }
            locationCallback = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка остановки GPS обновлений: ${e.message}")
        }
    }

    /**
     * Полная очистка всех ресурсов модуля при закрытии приложения
     */
    fun cleanup(context: Context) {
        try {
            Log.d(TAG, "🧹 Начинаем полную очистку MapModule...")

            // Останавливаем GPS обновления
            disableLocationUpdates(context)

            // Очищаем все активные карты
            activeMapViews.values.forEach { mapView ->
                try {
                    cleanupLocationOverlay(mapView)
                    mapView.overlays.clear()
                    mapView.onDetach()
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Ошибка очистки карты: ${e.message}")
                }
            }
            activeMapViews.clear()

            // Сбрасываем все состояния
            myLocationOverlay = null
            markerCount = 0
            lastBearing = 0f
            isInitialized = false

            Log.d(TAG, "✅ Все ресурсы MapModule успешно очищены")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка полной очистки ресурсов: ${e.message}")
        }
    }

    // === LIFECYCLE МЕТОДЫ ===

    /**
     * Автоматическая очистка при уничтожении lifecycle владельца
     */
    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "🔄 Lifecycle onDestroy - очищаем ресурсы MapModule")

        // В lifecycle нет доступа к context, поэтому очищаем что можем
        activeMapViews.values.forEach { mapView ->
            try {
                mapView.overlays.clear()
                mapView.onDetach()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Ошибка очистки карты в lifecycle: ${e.message}")
            }
        }
        activeMapViews.clear()
        myLocationOverlay = null
        markerCount = 0
    }

    // === СТАТИСТИКА И МОНИТОРИНГ ===

    /**
     * Получает подробную статистику использования карт для мониторинга и отладки
     *
     * @return объект с полной статистикой работы модуля
     */
    fun getMapStatistics(): MapStatistics {
        return MapStatistics(
            activeMapViewsCount = activeMapViews.size,
            totalMarkersCount = markerCount,
            isLocationOverlayActive = myLocationOverlay != null,
            isLocationUpdatesActive = locationCallback != null,
            lastBearing = lastBearing,
            isInitialized = isInitialized
        )
    }

    /**
     * Проверяет общее состояние здоровья модуля
     *
     * @return true если модуль инициализирован и готов к работе
     */
    fun isModuleHealthy(): Boolean {
        return isInitialized && (activeMapViews.isNotEmpty() || myLocationOverlay != null)
    }

    /**
     * Получает краткую информацию о состоянии модуля для логирования
     */
    fun getModuleStatus(): String {
        val stats = getMapStatistics()
        return "MapModule: ${if (stats.isInitialized) "✅" else "❌"} | " +
                "Карт: ${stats.activeMapViewsCount} | " +
                "Маркеров: ${stats.totalMarkersCount} | " +
                "GPS: ${if (stats.isLocationUpdatesActive) "🟢" else "🔴"}"
    }

    // === DATA CLASSES ===

    /**
     * Подробная статистика работы модуля карт для мониторинга и отладки
     *
     * @param activeMapViewsCount количество активных экземпляров карт
     * @param totalMarkersCount общее количество маркеров на всех картах
     * @param isLocationOverlayActive активен ли overlay отображения местоположения
     * @param isLocationUpdatesActive активны ли GPS обновления
     * @param lastBearing последнее зафиксированное направление движения в градусах
     * @param isInitialized полностью ли инициализирован модуль
     */
    data class MapStatistics(
        val activeMapViewsCount: Int,
        val totalMarkersCount: Int,
        val isLocationOverlayActive: Boolean,
        val isLocationUpdatesActive: Boolean,
        val lastBearing: Float,
        val isInitialized: Boolean
    ) {
        /**
         * Возвращает краткую сводку статистики для отображения в UI
         */
        fun getSummary(): String {
            val gpsStatus = if (isLocationUpdatesActive) "ON" else "OFF"
            return "Карт: $activeMapViewsCount | Маркеров: $totalMarkersCount | GPS: $gpsStatus"
        }

        /**
         * Возвращает детальную информацию о состоянии модуля
         */
        fun getDetailedInfo(): String {
            return buildString {
                appendLine("🗺️ Подробная статистика MapModule:")
                appendLine("• Статус инициализации: ${if (isInitialized) "✅ Инициализирован" else "❌ Не инициализирован"}")
                appendLine("• Активных карт: $activeMapViewsCount")
                appendLine("• Маркеров на картах: $totalMarkersCount / $MAX_MARKERS")
                appendLine("• Location overlay: ${if (isLocationOverlayActive) "🟢 Активен" else "🔴 Неактивен"}")
                appendLine("• GPS обновления: ${if (isLocationUpdatesActive) "🟢 Включены" else "🔴 Выключены"}")
                appendLine(
                    "• Последнее направление: ${lastBearing.toInt()}° ${
                        getCompassDirection(
                            lastBearing
                        )
                    }"
                )
                appendLine("• Использование памяти: ${getMemoryUsageEstimate()}")
            }
        }

        /**
         * Проверяет наличие проблем в работе модуля
         */
        fun hasIssues(): Boolean {
            return !isInitialized ||
                    (activeMapViewsCount == 0 && isLocationOverlayActive) ||
                    totalMarkersCount > MAX_MARKERS
        }

        /**
         * Возвращает список выявленных проблем
         */
        fun getIssues(): List<String> {
            val issues = mutableListOf<String>()

            if (!isInitialized) {
                issues.add("Модуль не инициализирован")
            }

            if (activeMapViewsCount == 0 && isLocationOverlayActive) {
                issues.add("Location overlay активен без активных карт")
            }

            if (totalMarkersCount > MAX_MARKERS) {
                issues.add("Превышено максимальное количество маркеров")
            }

            if (activeMapViewsCount > 5) {
                issues.add("Слишком много активных карт (возможна утечка памяти)")
            }

            return issues
        }

        /**
         * Конвертирует направление в градусах в название стороны света
         */
        private fun getCompassDirection(bearing: Float): String {
            return when {
                bearing < 0 -> "Неизвестно"
                bearing < 22.5 || bearing >= 337.5 -> "С"   // Север
                bearing < 67.5 -> "СВ"   // Северо-восток
                bearing < 112.5 -> "В"   // Восток
                bearing < 157.5 -> "ЮВ"  // Юго-восток
                bearing < 202.5 -> "Ю"   // Юг
                bearing < 247.5 -> "ЮЗ"  // Юго-запад
                bearing < 292.5 -> "З"   // Запад
                bearing < 337.5 -> "СЗ"  // Северо-запад
                else -> "Неизвестно"
            }
        }

        /**
         * Примерная оценка использования памяти модулем
         */
        private fun getMemoryUsageEstimate(): String {
            val mapMemory = activeMapViewsCount * 2 // ~2MB на карту
            val markerMemory = totalMarkersCount * 0.01 // ~10KB на маркер
            val totalMB = mapMemory + markerMemory

            return when {
                totalMB < 1 -> "< 1 МБ"
                totalMB < 10 -> String.format("%.1f МБ", totalMB)
                else -> "${totalMB.toInt()} МБ"
            }
        }
    }

    /**
     * Enum для различных типов событий карты
     */
    enum class MapEventType {
        MAP_INITIALIZED,
        LOCATION_OVERLAY_ADDED,
        MARKER_ADDED,
        MARKER_REMOVED,
        ROUTE_CLEARED,
        GPS_ENABLED,
        GPS_DISABLED,
        MODULE_CLEANUP
    }

    /**
     * Data class для событий карты (для потенциального расширения функциональности)
     */
    data class MapEvent(
        val type: MapEventType,
        val timestamp: Long = System.currentTimeMillis(),
        val details: String = "",
        val coordinates: GeoPoint? = null
    ) {
        /**
         * Возвращает читаемое описание события
         */
        fun getDescription(): String {
            val coordsInfo = coordinates?.let { " (${it.latitude}, ${it.longitude})" } ?: ""
            return "${type.name}$coordsInfo: $details"
        }
    }
}