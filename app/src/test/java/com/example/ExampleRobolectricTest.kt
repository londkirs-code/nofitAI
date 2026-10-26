package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ProviderType
import com.example.data.security.AppLockManager
import com.example.data.security.CryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NotifAI", appName)

        val replyActionLabel = context.getString(R.string.notification_action_reply)
        assertEquals("שאל AI", replyActionLabel)
    }

    @Test
    fun `provider types are correctly configured`() {
        val providers = ProviderType.values()
        assertTrue(providers.any { it == ProviderType.GROQ })
        assertTrue(providers.any { it == ProviderType.GROK })
        assertTrue(providers.any { it == ProviderType.GEMINI })
        assertTrue(providers.any { it == ProviderType.CLAUDE })
        assertTrue(providers.any { it == ProviderType.OPENAI })
        assertTrue(providers.any { it == ProviderType.OPENROUTER })
        assertTrue(providers.any { it == ProviderType.HUGGINGFACE })
    }

    @Test
    fun `crypto engine masking works`() {
        val masked = CryptoEngine.maskApiKey("sk-ant-api03-abcdef1234567890")
        assertTrue(masked.contains("••••••••"))
        assertTrue(masked.startsWith("sk-an"))
    }

    @Test
    fun `app lock manager sets and verifies pin`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLockManager.removePin(context)
        assertFalse(AppLockManager.isLockEnabled(context))

        AppLockManager.setupPin(context, "2580")
        assertTrue(AppLockManager.hasPin(context))
        assertTrue(AppLockManager.isLockEnabled(context))
        assertTrue(AppLockManager.verifyPin(context, "2580"))
        assertFalse(AppLockManager.verifyPin(context, "0000"))
    }
}
