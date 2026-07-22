package org.linguamesh.android.security

interface CredentialStore {
    suspend fun store(
        secretRef: String,
        secret: CharArray,
    )

    suspend fun resolve(secretRef: String): ByteArray?

    suspend fun delete(secretRef: String)
}

class CredentialStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
