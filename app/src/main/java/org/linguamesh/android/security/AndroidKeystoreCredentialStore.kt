package org.linguamesh.android.security

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidKeystoreCredentialStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun store(
        secretRef: String,
        secret: CharArray,
    ) = withContext(ioDispatcher) {
        require(secretRef.isNotBlank())
        val plaintext = encodeAndClear(secret)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
            cipher.updateAAD(secretRef.toByteArray(StandardCharsets.UTF_8))
            val ciphertext = cipher.doFinal(plaintext)
            val encodedIv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encodedCiphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            ciphertext.fill(0)
            val saved = preferences.edit()
                .putString(secretRef, "$encodedIv:$encodedCiphertext")
                .commit()
            if (!saved) {
                throw CredentialStoreException("Credential ciphertext could not be saved")
            }
        } catch (error: CredentialStoreException) {
            throw error
        } catch (error: Exception) {
            throw CredentialStoreException("Credential encryption failed", error)
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun resolve(secretRef: String): ByteArray? = withContext(ioDispatcher) {
        require(secretRef.isNotBlank())
        val encoded = preferences.getString(secretRef, null) ?: return@withContext null
        try {
            val parts = encoded.split(':', limit = 2)
            if (parts.size != 2) {
                throw CredentialStoreException("Credential ciphertext is malformed")
            }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(secretRef.toByteArray(StandardCharsets.UTF_8))
            try {
                cipher.doFinal(ciphertext)
            } finally {
                iv.fill(0)
                ciphertext.fill(0)
            }
        } catch (error: CredentialStoreException) {
            throw error
        } catch (error: Exception) {
            throw CredentialStoreException("Credential decryption failed", error)
        }
    }

    @SuppressLint("UseKtx")
    override suspend fun delete(secretRef: String) = withContext(ioDispatcher) {
        require(secretRef.isNotBlank())
        if (!preferences.edit().remove(secretRef).commit()) {
            throw CredentialStoreException("Credential ciphertext could not be deleted")
        }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private fun encodeAndClear(secret: CharArray): ByteArray {
        var encoded: ByteBuffer? = null
        try {
            encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret))
            val bytes = ByteArray(encoded.remaining())
            encoded.get(bytes)
            return bytes
        } finally {
            encoded?.apply {
                clear()
                while (hasRemaining()) {
                    put(0)
                }
            }
            secret.fill('\u0000')
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "org.linguamesh.android.provider-credentials.v1"
        const val PREFERENCES_NAME = "encrypted_provider_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
