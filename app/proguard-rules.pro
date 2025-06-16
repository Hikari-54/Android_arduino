# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# ===== GOOGLE PLAY SERVICES =====
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ===== LOCATION SERVICES =====
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.internal.location.** { *; }

# ===== FUSED LOCATION PROVIDER =====
-keep class com.google.android.gms.location.FusedLocationProviderClient { *; }
-keep class com.google.android.gms.location.LocationRequest { *; }
-keep class com.google.android.gms.location.LocationCallback { *; }
-keep class com.google.android.gms.location.LocationResult { *; }

# ===== COMPANION OBJECTS =====
-keepclassmembers class * {
    *** Companion;
}

# ===== KEEP COMPANION OBJECT METHODS =====
-keepclassmembers class **$Companion {
    <fields>;
    <methods>;
}

# ===== BLUETOOTH =====
-keep class android.bluetooth.** { *; }

# ===== KOTLIN =====
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ===== ANDROIDX =====
-keep class androidx.** { *; }
-dontwarn androidx.**

# ===== OSMDROID =====
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# ===== OKHTTP =====
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# ===== ОБЩИЕ ПРАВИЛА =====
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses