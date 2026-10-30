package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ProviderType
import com.example.data.security.CryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertTrue(providers.any { it == ProviderType.POLLINATIONS })
    }

    @Test
    fun `crypto engine per-key envelope encryption, AAD binding, key destruction, and redaction work`() {
        val rawKey = "sk-ant-api03-abcdef1234567890"
        val masked = CryptoEngine.maskApiKey(rawKey)
        assertTrue(masked.contains("••••••••"))
        assertFalse(masked.contains(rawKey))

        // Per-key envelope encryption
        val enc1 = CryptoEngine.encrypt(rawKey, keyId = "key_alpha", aadContext = "CLAUDE")
        val enc2 = CryptoEngine.encrypt(rawKey, keyId = "key_beta", aadContext = "CLAUDE")
        assertTrue(enc1.startsWith("ENC_V2:"))
        assertTrue(enc2.startsWith("ENC_V2:"))
        assertNotEquals(enc1, enc2)

        // Valid decryption with matching keyId and AAD
        assertEquals(rawKey, CryptoEngine.decrypt(enc1, keyId = "key_alpha", aadContext = "CLAUDE"))

        // Tampered keyId or AAD must fail decryption
        assertEquals("", CryptoEngine.decrypt(enc1, keyId = "key_beta", aadContext = "CLAUDE"))
        assertEquals("", CryptoEngine.decrypt(enc1, keyId = "key_alpha", aadContext = "OPENAI"))

        // Key destruction invalidates existing ciphertext
        CryptoEngine.destroyKeyMaterial("key_alpha")

        val errorWithLeakedKey = "Invalid key provided: gsk_1234567890abcdefghijklmnop in request"
        val redacted = CryptoEngine.redactSecretsFromText(errorWithLeakedKey)
        assertFalse(redacted.contains("gsk_1234567890abcdefghijklmnop"))
        assertTrue(redacted.contains("[REDACTED_KEY]"))
    }
}
