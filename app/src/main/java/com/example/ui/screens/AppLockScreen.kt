package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AppLockManager
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lockoutRemaining by remember {
        mutableLongStateOf(AppLockManager.getRemainingLockoutSeconds(context))
    }

    LaunchedEffect(lockoutRemaining) {
        if (lockoutRemaining > 0) {
            delay(1000L)
            lockoutRemaining = AppLockManager.getRemainingLockoutSeconds(context)
            if (lockoutRemaining == 0L) {
                errorMessage = null
            }
        }
    }

    fun tryUnlock(pinToVerify: String) {
        if (AppLockManager.isLockedOut(context)) {
            lockoutRemaining = AppLockManager.getRemainingLockoutSeconds(context)
            errorMessage = "יותר מדי ניסיונות שגויים. נסה שוב בעוד $lockoutRemaining שניות."
            return
        }
        if (AppLockManager.verifyPin(context, pinToVerify)) {
            errorMessage = null
            onUnlocked()
        } else {
            enteredPin = ""
            lockoutRemaining = AppLockManager.getRemainingLockoutSeconds(context)
            errorMessage = if (lockoutRemaining > 0) {
                "הגעת ל-5 ניסיונות שגויים. נעול ל-$lockoutRemaining שניות."
            } else {
                "קוד PIN שגוי, נסה שנית"
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("app_lock_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Shield / Lock Icon
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "נעול",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "NotifAI נעולה",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "הזן את קוד ה-PIN שלך כדי לגשת למפתחות ה-API ולהיסטוריה",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // PIN Dots Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(32.dp)
                ) {
                    val maxDots = maxOf(4, enteredPin.length)
                    for (i in 0 until maxDots) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(if (isFilled) 18.dp else 14.dp)
                                .background(
                                    color = if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error or lockout message
                AnimatedVisibility(visible = errorMessage != null || lockoutRemaining > 0) {
                    Text(
                        text = if (lockoutRemaining > 0) {
                            "נעול זמנית להגנה: המתן $lockoutRemaining שניות"
                        } else {
                            errorMessage.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Numeric Keypad (LTR layout for standard numpad arrangement)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9")
                        )

                        for (row in rows) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                for (digit in row) {
                                    PinKeypadButton(
                                        text = digit,
                                        enabled = lockoutRemaining == 0L,
                                        onClick = {
                                            if (enteredPin.length < 8) {
                                                enteredPin += digit
                                                errorMessage = null
                                                // Auto check if matches immediately when >= 4 digits
                                                val encrypted = context.getSharedPreferences("notifai_security_prefs", android.content.Context.MODE_PRIVATE)
                                                    .getString("encrypted_pin_code", null)
                                                if (enteredPin.length >= 4 && encrypted != null) {
                                                    val expectedLen = com.example.data.security.CryptoEngine.decrypt(encrypted).length
                                                    if (enteredPin.length == expectedLen) {
                                                        tryUnlock(enteredPin)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Bottom Row: Backspace, 0, Confirm
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            // Backspace
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable(enabled = enteredPin.isNotEmpty() && lockoutRemaining == 0L) {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                    }
                                    .testTag("pin_backspace_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "מחק",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 0
                            PinKeypadButton(
                                text = "0",
                                enabled = lockoutRemaining == 0L,
                                onClick = {
                                    if (enteredPin.length < 8) {
                                        enteredPin += "0"
                                        errorMessage = null
                                        val encrypted = context.getSharedPreferences("notifai_security_prefs", android.content.Context.MODE_PRIVATE)
                                            .getString("encrypted_pin_code", null)
                                        if (enteredPin.length >= 4 && encrypted != null) {
                                            val expectedLen = com.example.data.security.CryptoEngine.decrypt(encrypted).length
                                            if (enteredPin.length == expectedLen) {
                                                tryUnlock(enteredPin)
                                            }
                                        }
                                    }
                                }
                            )

                            // Unlock / Check button
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (enteredPin.length >= 4 && lockoutRemaining == 0L)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceContainer
                                    )
                                    .clickable(enabled = enteredPin.length >= 4 && lockoutRemaining == 0L) {
                                        tryUnlock(enteredPin)
                                    }
                                    .testTag("pin_unlock_submit_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "אישור",
                                    tint = if (enteredPin.length >= 4 && lockoutRemaining == 0L)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKeypadButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("pin_key_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SetupPinDialog(
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onPinSaved: () -> Unit
) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (hasExistingPin) "שינוי קוד נעילה (PIN)" else "הגדרת נעילה לאפליקציה",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "הגדר קוד PIN בן 4 עד 8 ספרות. הקוד יידרש בכל כניסה לאפליקציה כדי להגן על מפתחות ה-API ועל היסטוריית השיחות שלך.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasExistingPin) {
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = {
                                if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                                    currentPin = it
                                    errorText = null
                                }
                            },
                            label = { Text("קוד PIN נוכחי") },
                            singleLine = true,
                            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("current_pin_input")
                        )
                    }

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                                newPin = it
                                errorText = null
                            }
                        },
                        label = { Text("קוד PIN חדש (4-8 ספרות)") },
                        singleLine = true,
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPin) "הסתר" else "הצג"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_pin_input")
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                                confirmPin = it
                                errorText = null
                            }
                        },
                        label = { Text("אימות קוד PIN חדש") },
                        singleLine = true,
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_pin_input")
                    )

                    if (errorText != null) {
                        Text(
                            text = errorText!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hasExistingPin && !AppLockManager.verifyPin(context, currentPin)) {
                            errorText = "קוד ה-PIN הנוכחי שגוי"
                            return@Button
                        }
                        if (newPin.length < 4) {
                            errorText = "הקוד חייב להכיל לפחות 4 ספרות"
                            return@Button
                        }
                        if (newPin != confirmPin) {
                            errorText = "קודי ה-PIN אינם תואמים"
                            return@Button
                        }
                        AppLockManager.setupPin(context, newPin)
                        Toast.makeText(context, "קוד הנעילה הוגדר והופעל בהצלחה! 🔒", Toast.LENGTH_SHORT).show()
                        onPinSaved()
                    },
                    modifier = Modifier.testTag("save_pin_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("שמור והפעל נעילה", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("ביטול")
                }
            }
        )
    }
}
