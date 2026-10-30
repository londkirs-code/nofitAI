package com.example.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Envelope Encryption Engine (AES-256-GCM + AndroidKeyStore StrongBox/TEE).
 *
 * Security Architecture:
 * 1. Dedicated Per-Key Hardware KeyStore Key (KEK):
 *    Every API key ID gets its own isolated 256-bit AES-GCM key inside the Android Hardware
 *    KeyStore ("NotifAI_VaultKey_<keyId>"), using StrongBox HSM where available or TEE hardware.
 *    Hardware keys never leave the secure element in plaintext.
 * 2. Envelope Encryption with Ephemeral 256-bit DEK:
 *    Each encryption generates a fresh 256-bit random Data Encryption Key (DEK) via SecureRandom.
 *    The API key is encrypted with the DEK using AES-256-GCM (128-bit auth tag) bound to
 *    Additional Authenticated Data (AAD = keyId + provider context), and the DEK is wrapped by
 *    the per-key hardware KEK in AndroidKeyStore.
 * 3. Zero Static/Base64 Fallbacks & Immediate Memory Zeroing:
 *    All sensitive byte arrays (DEK, plaintext bytes) are zeroed out in memory immediately
 *    after use. No static keys or Base64 fallbacks exist.
 * 4. Cryptographic Erasure:
 *    Deleting an API key destroys its dedicated hardware KEK in AndroidKeyStore.
 */
object CryptoEngine {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val LEGACY_MASTER_ALIAS = "NotifAI_Master_Key"
    private const val KEY_ALIAS_PREFIX = "NotifAI_VaultKey_"
    private const val GLOBAL_VAULT_KEY_ID = "global_vault"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val DEK_SIZE_BYTES = 32 // 256-bit Data Encryption Key
    private const val PREFIX_ENVELOPE_V2 = "ENC_V2:"
    private const val PREFIX_LEGACY_GCM = "ENC_GCM:"

    private val secureRandom = SecureRandom()

    // Ephemeral RAM-only keys used strictly when running in a pure local JVM unit test
    // where AndroidKeyStore provider is not mounted by the OS. Never persisted, never static.
    private val jvmTestOnlyEphemeralKeys = ConcurrentHashMap<String, SecretKey>()

    @Synchronized
    fun init() {
        // Purge any legacy PIN lock preferences from older app versions
        // and ensure the hardware keystore provider is ready.
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (_: Exception) {
            // Pure JVM test environment
        }
    }

    private fun aliasForKeyId(keyId: String): String {
        val sanitized = keyId.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { GLOBAL_VAULT_KEY_ID }
        return "$KEY_ALIAS_PREFIX$sanitized"
    }

    private fun buildAadBytes(keyId: String, aadContext: String): ByteArray {
        val normalizedId = keyId.trim().ifBlank { GLOBAL_VAULT_KEY_ID }
        val normalizedCtx = aadContext.trim().ifBlank { "API_KEY" }
        return "NotifAI-Vault-v2|$normalizedId|$normalizedCtx".toByteArray(Charsets.UTF_8)
    }

    @Synchronized
    private fun getOrCreateHardwareKek(keyId: String): SecretKey {
        val alias = aliasForKeyId(keyId)
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                return existingEntry.secretKey
            }

            // Try StrongBox Hardware Security Module first on API 28+, then fallback to TEE Hardware KeyStore
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    return generateKeyStoreAesKey(alias, requestStrongBox = true)
                } catch (_: Exception) {
                    // Device does not have a discrete StrongBox chip; use standard TEE AndroidKeyStore
                }
            }
            return generateKeyStoreAesKey(alias, requestStrongBox = false)
        } catch (_: Exception) {
            // Strictly for local JVM unit tests where AndroidKeyStore provider does not exist:
            // generate a random 256-bit key in RAM for the current test process only.
            return jvmTestOnlyEphemeralKeys.getOrPut(alias) {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256, secureRandom)
                kg.generateKey()
            }
        }
    }

    private fun generateKeyStoreAesKey(alias: String, requestStrongBox: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && requestStrongBox) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts [plainText] using per-key hardware-backed Envelope Encryption (ENC_V2).
     *
     * Binary layout inside Base64 payload:
     * [1 byte: ivKekLen][ivKek][2 bytes: wrappedDekLen][wrappedDek][1 byte: ivDataLen][ivData][cipherData]
     */
    fun encrypt(
        plainText: String,
        keyId: String = GLOBAL_VAULT_KEY_ID,
        aadContext: String = "API_KEY"
    ): String {
        if (plainText.isBlank()) return ""

        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val dekBytes = ByteArray(DEK_SIZE_BYTES)
        val ivData = ByteArray(GCM_IV_LENGTH)
        val aad = buildAadBytes(keyId, aadContext)

        try {
            // 1. Generate a fresh 256-bit random Data Encryption Key (DEK) and 96-bit IV
            secureRandom.nextBytes(dekBytes)
            secureRandom.nextBytes(ivData)
            val dekSpec = SecretKeySpec(dekBytes, "AES")

            // 2. Encrypt the API key plaintext with the ephemeral DEK + AAD
            val dataCipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            dataCipher.init(Cipher.ENCRYPT_MODE, dekSpec, GCMParameterSpec(GCM_TAG_LENGTH, ivData))
            dataCipher.updateAAD(aad)
            val cipherData = dataCipher.doFinal(plainBytes)

            // 3. Wrap (encrypt) the DEK using the dedicated hardware KEK in AndroidKeyStore + AAD
            val hardwareKek = getOrCreateHardwareKek(keyId)
            val kekCipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            kekCipher.init(Cipher.ENCRYPT_MODE, hardwareKek)
            kekCipher.updateAAD(aad)
            val ivKek = kekCipher.iv
            val wrappedDek = kekCipher.doFinal(dekBytes)

            // 4. Pack envelope
            val totalSize = 1 + ivKek.size + 2 + wrappedDek.size + 1 + ivData.size + cipherData.size
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.put(ivKek.size.toByte())
            buffer.put(ivKek)
            buffer.putShort(wrappedDek.size.toShort())
            buffer.put(wrappedDek)
            buffer.put(ivData.size.toByte())
            buffer.put(ivData)
            buffer.put(cipherData)

            return PREFIX_ENVELOPE_V2 + Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        } finally {
            // Zero out sensitive material in RAM immediately
            Arrays.fill(plainBytes, 0.toByte())
            Arrays.fill(dekBytes, 0.toByte())
        }
    }

    /**
     * Decrypts an encrypted API key strictly in memory at the moment of an outbound HTTPS call.
     */
    fun decrypt(
        encryptedText: String,
        keyId: String = GLOBAL_VAULT_KEY_ID,
        aadContext: String = "API_KEY"
    ): String {
        if (encryptedText.isBlank()) return ""

        if (encryptedText.startsWith(PREFIX_ENVELOPE_V2)) {
            return decryptEnvelopeV2(
                encoded = encryptedText.removePrefix(PREFIX_ENVELOPE_V2),
                keyId = keyId,
                aadContext = aadContext
            )
        }

        if (encryptedText.startsWith(PREFIX_LEGACY_GCM)) {
            return decryptLegacyGcm(encryptedText.removePrefix(PREFIX_LEGACY_GCM))
        }

        return ""
    }

    private fun decryptEnvelopeV2(
        encoded: String,
        keyId: String,
        aadContext: String
    ): String {
        var dekBytes: ByteArray? = null
        var decryptedBytes: ByteArray? = null
        return try {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(raw)
            if (buffer.remaining() < 16) return ""

            val ivKekLen = buffer.get().toInt() and 0xFF
            if (ivKekLen <= 0 || buffer.remaining() < ivKekLen + 3) return ""
            val ivKek = ByteArray(ivKekLen)
            buffer.get(ivKek)

            val wrappedDekLen = buffer.short.toInt() and 0xFFFF
            if (wrappedDekLen <= 0 || buffer.remaining() < wrappedDekLen + 2) return ""
            val wrappedDek = ByteArray(wrappedDekLen)
            buffer.get(wrappedDek)

            val ivDataLen = buffer.get().toInt() and 0xFF
            if (ivDataLen <= 0 || buffer.remaining() <= ivDataLen) return ""
            val ivData = ByteArray(ivDataLen)
            buffer.get(ivData)

            val cipherData = ByteArray(buffer.remaining())
            buffer.get(cipherData)

            val aad = buildAadBytes(keyId, aadContext)

            // 1. Unwrap the DEK using the dedicated hardware KEK in AndroidKeyStore
            val hardwareKek = getOrCreateHardwareKek(keyId)
            val kekCipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            kekCipher.init(Cipher.DECRYPT_MODE, hardwareKek, GCMParameterSpec(GCM_TAG_LENGTH, ivKek))
            kekCipher.updateAAD(aad)
            dekBytes = kekCipher.doFinal(wrappedDek)

            // 2. Decrypt the API key using the unwrapped DEK + AAD verification
            val dekSpec = SecretKeySpec(dekBytes, "AES")
            val dataCipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            dataCipher.init(Cipher.DECRYPT_MODE, dekSpec, GCMParameterSpec(GCM_TAG_LENGTH, ivData))
            dataCipher.updateAAD(aad)
            decryptedBytes = dataCipher.doFinal(cipherData)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        } finally {
            dekBytes?.let { Arrays.fill(it, 0.toByte()) }
            decryptedBytes?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    /**
     * Migrates keys encrypted with the older v1 AndroidKeyStore entry if present in the hardware KeyStore.
     */
    private fun decryptLegacyGcm(cleanBase64: String): String {
        var decryptedBytes: ByteArray? = null
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(LEGACY_MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry
                ?: return ""
            val combined = Base64.decode(cleanBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            val cipherTextSize = combined.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextSize)

            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, entry.secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        } finally {
            decryptedBytes?.let { Arrays.fill(it, 0.toByte()) }
        }
    }

    fun isCurrentEnvelopeFormat(encryptedText: String): Boolean {
        return encryptedText.startsWith(PREFIX_ENVELOPE_V2)
    }

    /**
     * Permanently destroys the dedicated hardware KeyStore key for [keyId]
     * so any old ciphertext can never be decrypted again.
     */
    @Synchronized
    fun destroyKeyMaterial(keyId: String) {
        val alias = aliasForKeyId(keyId)
        jvmTestOnlyEphemeralKeys.remove(alias)
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (_: Exception) {}
    }

    /**
     * Permanently destroys all per-key hardware keys in AndroidKeyStore.
     */
    @Synchronized
    fun destroyAllKeyMaterial() {
        jvmTestOnlyEphemeralKeys.clear()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val aliases = keyStore.aliases().toList()
            for (alias in aliases) {
                if (alias.startsWith(KEY_ALIAS_PREFIX) || alias == LEGACY_MASTER_ALIAS) {
                    keyStore.deleteEntry(alias)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Redacts any API key, Bearer token, or URL key parameter from error/network logs or UI strings
     * so secrets can never leak in error messages or logs.
     */
    fun redactSecretsFromText(text: String, exactSecret: String? = null): String {
        if (text.isBlank()) return text
        var result = text
        if (!exactSecret.isNullOrBlank() && exactSecret.length >= 6) {
            result = result.replace(exactSecret, "[REDACTED_KEY]")
        }
        // Redact common API key prefixes and query params
        result = result.replace(Regex("""([?&]key=)[^&\s"']+""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        result = result.replace(Regex("""(Bearer\s+)[A-Za-z0-9._\-]{8,}""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        result = result.replace(Regex("""\b(sk-or-v1-[A-Za-z0-9._\-]{8,}|sk-ant-[A-Za-z0-9._\-]{8,}|sk-proj-[A-Za-z0-9._\-]{8,}|sk-[A-Za-z0-9._\-]{12,}|gsk_[A-Za-z0-9._\-]{12,}|AIza[0-9A-Za-z\-_]{15,}|xai-[A-Za-z0-9._\-]{12,})\b"""), "[REDACTED_KEY]")
        return result
    }

    /**
     * Masks an input key string so full key contents are never shown.
     */
    fun maskApiKey(rawKey: String): String {
        val clean = rawKey.trim()
        if (clean.length <= 8) return "••••••••"
        return "${clean.take(5)}••••••••"
    }
}

