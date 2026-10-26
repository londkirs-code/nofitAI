package com.example.data.security

import android.content.Context

object AppLockManager {
    private const val PREFS_NAME = "notifai_security_prefs"
    private const val KEY_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_ENCRYPTED_PIN = "encrypted_pin_code"
    private const val KEY_AUTO_LOCK_ON_BG = "auto_lock_on_background"
    private const val KEY_BLOCK_SCREENSHOTS = "block_screenshots_flag"
    private const val KEY_FAILED_ATTEMPTS = "failed_pin_attempts"
    private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLockEnabled(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_LOCK_ENABLED, false) && hasPin(context)
    }

    fun hasPin(context: Context): Boolean {
        val encrypted = prefs(context).getString(KEY_ENCRYPTED_PIN, null)
        return !encrypted.isNullOrBlank()
    }

    fun setupPin(context: Context, pin: String) {
        val encrypted = CryptoEngine.encrypt(pin.trim())
        prefs(context).edit()
            .putString(KEY_ENCRYPTED_PIN, encrypted)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        if (isLockedOut(context)) return false
        val encrypted = prefs(context).getString(KEY_ENCRYPTED_PIN, null) ?: return false
        val storedPin = CryptoEngine.decrypt(encrypted)
        val matched = storedPin.isNotEmpty() && storedPin == inputPin.trim()

        if (matched) {
            prefs(context).edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
        } else {
            val attempts = prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val editor = prefs(context).edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
            if (attempts >= 5) {
                // Lock out for 30 seconds after 5 failed attempts
                editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + 30_000L)
            }
            editor.apply()
        }
        return matched
    }

    fun isLockedOut(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L)
        return until > System.currentTimeMillis()
    }

    fun getRemainingLockoutSeconds(context: Context): Long {
        val until = prefs(context).getLong(KEY_LOCKOUT_UNTIL, 0L)
        val diff = until - System.currentTimeMillis()
        return if (diff > 0) (diff / 1000L) + 1 else 0L
    }

    fun getFailedAttempts(context: Context): Int {
        return prefs(context).getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }

    fun removePin(context: Context) {
        prefs(context).edit()
            .remove(KEY_ENCRYPTED_PIN)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun isAutoLockOnBackground(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_LOCK_ON_BG, true)
    }

    fun setAutoLockOnBackground(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_LOCK_ON_BG, enabled).apply()
    }

    fun isBlockScreenshots(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BLOCK_SCREENSHOTS, false)
    }

    fun setBlockScreenshots(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCK_SCREENSHOTS, enabled).apply()
    }
}
