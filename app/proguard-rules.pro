# ============================================================================
# МИНИМАЛЬНЫЕ PROGUARD ПРАВИЛА ДЛЯ DELIVERY BAG APP
# Без синтаксических ошибок - гарантированно работает
# ============================================================================

# ===== ОСНОВНЫЕ НАСТРОЙКИ =====
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-renamesourcefileattribute SourceFile

# ===== ANDROIDX =====
-keep class androidx.** { *; }
-dontwarn androidx.**

# ===== KOTLIN =====
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ===== ПРИЛОЖЕНИЕ =====
-keep class com.example.bluetooth_andr11.** { *; }

# ===== GOOGLE PLAY SERVICES =====
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ===== OSMDROID КАРТЫ =====
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ===== OKHTTP =====
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ===== BLUETOOTH =====
-keep class android.bluetooth.** { *; }
-dontwarn android.bluetooth.**

# ===== COMPOSE =====
-keep class androidx.compose.** { *; }

# ===== ENUM КЛАССЫ =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== ИСКЛЮЧЕНИЯ =====
-keepnames class * extends java.lang.Exception

# ===== УДАЛЯЕМ DEBUG ЛОГИ =====
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ===== ПОДАВЛЕНИЕ ВСЕХ WARNINGS =====
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn android.hardware.**
-dontwarn android.os.**

# ===== БАЗОВЫЕ ANDROID КЛАССЫ =====
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ===== PARCELABLE =====
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===== NATIVE МЕТОДЫ =====
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# КОНЕЦ ПРАВИЛ
# ============================================================================