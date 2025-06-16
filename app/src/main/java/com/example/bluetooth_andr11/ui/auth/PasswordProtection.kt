package com.example.bluetooth_andr11.ui.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PasswordManager {
    private const val PREFS_NAME = "app_security"
    private const val PASSWORD_KEY = "access_password"
    private const val SESSION_KEY = "session_active"

    // Захардкоженный пароль
    private const val DEFAULT_PASSWORD = "admin123"

    fun checkPassword(context: Context, inputPassword: String): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedPassword = sharedPrefs.getString(PASSWORD_KEY, DEFAULT_PASSWORD)
        return inputPassword == storedPassword
    }

    fun setSessionActive(context: Context, active: Boolean) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(SESSION_KEY, active).apply()
    }

    fun isSessionActive(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean(SESSION_KEY, false)
    }
}

@Composable
fun PasswordDialog(
    onPasswordCorrect: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🔒 Доступ к отчётам",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Введите пароль для просмотра логов событий",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (PasswordManager.checkPassword(context, password)) {
                                PasswordManager.setSessionActive(context, true)
                                onPasswordCorrect()
                            } else {
                                showError = true
                                attempts++
                                password = ""
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(
                                    id = if (passwordVisible) {
                                        android.R.drawable.ic_menu_view
                                    } else {
                                        android.R.drawable.ic_secure
                                    }
                                ),
                                contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль"
                            )
                        }
                    },
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text(
                        text = "❌ Неверный пароль (попытка $attempts/3)",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (attempts >= 3) {
                    Text(
                        text = "⚠️ Слишком много неудачных попыток",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (PasswordManager.checkPassword(context, password)) {
                        PasswordManager.setSessionActive(context, true)
                        onPasswordCorrect()
                    } else {
                        showError = true
                        attempts++
                        password = ""
                    }
                },
                enabled = password.isNotEmpty() && attempts < 3,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF252525)
                )
            ) {
                Text("Войти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.Gray)
            }
        }
    )
}

@Composable
fun PasswordProtectedContent(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showPasswordDialog by remember { mutableStateOf(!PasswordManager.isSessionActive(context)) }
    var isAuthenticated by remember { mutableStateOf(PasswordManager.isSessionActive(context)) }

    if (isAuthenticated) {
        content()
    } else {
        // Показываем экран с запросом пароля
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 64.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Защищённая область",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Требуется авторизация для доступа к отчётам",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = { showPasswordDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF252525)
                    )
                ) {
                    Text("Ввести пароль")
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            onPasswordCorrect = {
                isAuthenticated = true
                showPasswordDialog = false
            },
            onDismiss = {
                showPasswordDialog = false
            }
        )
    }
}