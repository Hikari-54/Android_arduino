# Delivery Bag

## О проекте

**Delivery Bag** — Android-приложение для мониторинга доставочной сумки с интегрированными датчиками Arduino. Приложение отслеживает температуру, состояние замка, тряску и предоставляет возможность удаленного управления функциями сумки через Bluetooth.

## Основные возможности

### 🔌 Подключение и управление

- Подключение к Arduino через Bluetooth с автоматическим переподключением
- Управление нагревом, охлаждением и подсветкой сумки
- Симуляция Arduino для тестирования (DEBUG режим)

### 📊 Мониторинг данных

- Отслеживание температуры верхнего и нижнего отсеков
- Мониторинг состояния замка (открыт/закрыт) через датчик Холла
- Анализ уровня тряски с помощью акселерометра
- Контроль заряда батареи с предупреждениями

### 🗺️ Геолокация и картография

- Интеграция с OpenStreetMap для отображения местоположения
- Логирование событий с привязкой к GPS координатам
- Отображение маршрута движения сумки

### 📝 Система логирования

- Детальное логирование всех событий с временными метками
- Фильтрация логов по датам
- Экспорт логов для анализа
- Защищенный паролем доступ к логам

## Архитектура приложения

### Основные компоненты

**AppInitializer** — централизованная инициализация всех компонентов приложения с управлением зависимостями

**UIStateManager** — централизованное управление состояниями UI с reactive обновлениями

**DataManager** — обработка и валидация данных от Arduino с типобезопасным API

**BluetoothHelper** — управление Bluetooth соединениями, отправка команд и прием данных

**EnhancedLocationManager** — расширенное управление GPS с оптимизацией энергопотребления

**MapModule** — интеграция карт с кастомными overlays и оптимизированным кэшированием

**LogModule** — система логирования с защищенным хранением и экспортом

**TemperatureMonitor** — анализ температурных данных с алертами при критических значениях

### UI Компоненты

- **MainActivity** — основная активность с навигацией
- **MainScreen** — главный экран с картой и панелью управления
- **LogScreen** — защищенный экран просмотра логов
- **ControlPanel** — панель управления функциями сумки
- **DebugControlPanel** — отладочная панель (только DEBUG)

## Протокол обмена данными

### Формат данных от Arduino

```
battery,tempHot,tempCold,closed,state,overload
```

**Параметры:**

1. **battery** (0-100) — уровень заряда батареи в процентах
2. **tempHot** (float) — температура верхнего отсека в °C
3. **tempCold** (float) — температура нижнего отсека в °C
4. **closed** (0/1) — состояние замка (0 = открыт, 1 = закрыт)
5. **state** (int) — функциональное состояние устройства
6. **overload** (float) — данные акселерометра для определения тряски

### Команды управления

- **H/h** — включить/выключить нагрев
- **C/c** — включить/выключить охлаждение
- **L/l** — включить/выключить подсветку

### Интерпретация данных

**Температурные пороги:**

- Верхний отсек: критические события при 40°C, 50°C, 60°C
- Нижний отсек: критические события при 5°C, 10°C, 15°C

**Уровни тряски (акселерометр):**

- `>2.5` или `<-2.5` — экстремальная тряска
- `>1.0` или `<-1.0` — сильная тряска
- `>0.5` или `<-0.5` — слабая тряска
- `≤0.5` и `≥-0.5` — в покое

**Предупреждения батареи:**

- Критические уровни: 5%, 15%, 30%
- Логирование каждого изменения

## Возможности отладки

### DEBUG режим

- Симуляция Arduino с различными сценариями
- Панель отладки для тестирования GPS и Bluetooth
- Детальная диагностика всех компонентов
- Мониторинг производительности

### Система диагностики

- Проверка состояния всех модулей
- Анализ целостности логов
- Мониторинг использования памяти и батареи
- Автоматические рекомендации по оптимизации

## Безопасность и разрешения

### Требуемые разрешения Android

- `BLUETOOTH_CONNECT` — подключение к Arduino
- `BLUETOOTH_SCAN` — поиск устройств
- `ACCESS_FINE_LOCATION` — точная геолокация
- `ACCESS_COARSE_LOCATION` — приблизительная геолокация

### Защита данных

- Паролированный доступ к логам
- Шифрование чувствительных данных
- Локальное хранение без передачи в облако

## Установка и использование

1. Убедитесь, что Bluetooth включен на устройстве
2. Разрешите доступ к местоположению
3. Подключитесь к Arduino устройству через настройки Bluetooth
4. Используйте панель управления для контроля функций сумки
5. Мониторьте данные на главном экране

## Технические особенности

- **Язык:** Kotlin с Jetpack Compose
- **Минимальная версия Android:** API 21 (Android 5.0)
- **Архитектура:** MVVM с использованием современных подходов
- **Картография:** OpenStreetMap (osmdroid)
- **Геолокация:** Google Location Services

## Разработка и тестирование

Для тестирования без физического Arduino устройства используйте встроенную симуляцию:

1. Соберите приложение в DEBUG режиме
2. На главном экране появится панель отладки
3. Включите симуляцию Arduino для тестирования всех функций
