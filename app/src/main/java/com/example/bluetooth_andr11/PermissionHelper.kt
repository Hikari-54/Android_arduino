package com.example.bluetooth_andr11

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class PermissionHelper(
    private val context: Context,
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>>
) {
    private val requiredPermissions = buildPermissionList()

    private fun buildPermissionList(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH, // Для Android 11 и ниже
            Manifest.permission.BLUETOOTH_ADMIN, // Для управления Bluetooth
            Manifest.permission.ACCESS_FINE_LOCATION // Для Android 11 и ниже
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return permissions
    }

    fun hasAllPermissions(): Boolean {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) logMissingPermissions()
        return allGranted
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions() {
        val notGrantedPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGrantedPermissions.isNotEmpty()) {
            Log.i("PermissionHelper", "Запрашиваем разрешения: $notGrantedPermissions")
            requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
        } else {
            Log.i("PermissionHelper", "Все разрешения уже предоставлены")
        }
    }

    private fun logMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        Log.w("PermissionHelper", "Недостающие разрешения: $missing")
    }
}
