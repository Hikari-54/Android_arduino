package com.example.bluetooth_andr11.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class PermissionHelper(
    private val context: Context,
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>>
) {
    // Список всех необходимых разрешений
    private val requiredPermissions = buildPermissionList()

    // Создание списка разрешений в зависимости от версии Android
    private fun buildPermissionList(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION // Для доступа к местоположению
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions
    }

    // Проверка, все ли разрешения предоставлены
    fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { hasPermission(it) }
    }

    // Проверка наличия конкретного разрешения
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Запрос недостающих разрешений
    fun requestPermissions() {
        val notGrantedPermissions = requiredPermissions.filter {
            !hasPermission(it)
        }
        if (notGrantedPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
        }
    }

    // Проверка, есть ли конкретное разрешение, с возвратом его отсутствующего списка
    fun getMissingPermissions(): List<String> {
        return requiredPermissions.filter {
            !hasPermission(it)
        }
    }

    // Упрощенный запрос конкретного разрешения
    fun requestSpecificPermission(permission: String) {
        if (!hasPermission(permission)) {
            requestPermissionLauncher.launch(arrayOf(permission))
        }
    }
}
