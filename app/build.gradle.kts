plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // ❌ УБРАН: alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.bluetooth_andr11"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bluetooth_andr11"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 🔐 Конфигурация подписи
    signingConfigs {
        create("release") {
            // Получаем данные из gradle.properties
            val keystoreFile = project.findProperty("DELIVERY_BAG_KEYSTORE_FILE") as String?
            val keystorePassword = project.findProperty("DELIVERY_BAG_KEYSTORE_PASSWORD") as String?
            val keyAlias = project.findProperty("DELIVERY_BAG_KEY_ALIAS") as String?
            val keyPassword = project.findProperty("DELIVERY_BAG_KEY_PASSWORD") as String?

            // Проверяем наличие всех необходимых параметров
            if (keystoreFile != null && keystorePassword != null &&
                keyAlias != null && keyPassword != null
            ) {
                val keystoreFileObj = file(keystoreFile)

                // Дополнительная проверка существования файла
                if (keystoreFileObj.exists()) {
                    storeFile = keystoreFileObj
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword

                    println("✅ Release подпись настроена: $keystoreFile")
                } else {
                    println("⚠️ Keystore файл не найден: $keystoreFile")
                    println("   Проверьте путь в gradle.properties")
                    println("   Будет использована debug подпись")
                }
            } else {
                println("⚠️ Параметры подписи не найдены в gradle.properties")
                println("   Создайте gradle.properties из gradle.properties.template")
                println("   Будет использована debug подпись")
            }
        }
    }

    buildTypes {
        debug {
            // 🔧 Уникальный applicationId для debug версии
            applicationIdSuffix = ".debug"

            // 🏷️ Добавляем суффикс к имени приложения для различения
            resValue("string", "app_name", "Delivery Bag DEBUG")

            // 🔍 Debug настройки
            isDebuggable = true
            isMinifyEnabled = false
        }

        release {
            // 📱 Стандартное имя для release
            resValue("string", "app_name", "Delivery Bag")

            // Безопасное использование release подписи
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile?.exists() == true) {
                signingConfig = releaseSigningConfig
                println("🔐 Используется release подпись")
            } else {
                println("⚠️ Release keystore не найден, используется debug подпись")
                println("   Для production сборки настройте gradle.properties")
            }

            // 🛡️ Release настройки для RuStore
            isMinifyEnabled = true  // 🔄 ВКЛЮЧАЕМ обфускацию для безопасности
            isShrinkResources = true // 🗜️ ДОБАВЛЯЕМ сжатие ресурсов
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // Core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.navigation.compose)

    // Compose UI
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material)
    implementation(libs.androidx.material3)

    // Google Play Services
    implementation(libs.play.services.location)

    // Maps
    implementation(libs.osmdroid.android)

    // Network and logging
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // UI components
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.foundation)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}