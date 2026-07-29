package org.linguamesh.android.core

import kotlinx.coroutines.flow.Flow

data class CoreCompatibility(
    val abiMajor: UInt,
    val protocolVersion: UInt,
)

val requiredCoreCompatibility = CoreCompatibility(abiMajor = 1u, protocolVersion = 1u)

fun CoreCompatibility.incompatibilityAgainst(
    expected: CoreCompatibility = requiredCoreCompatibility,
): CoreGatewayException? {
    if (this == expected) {
        return null
    }
    return CoreGatewayException(
        kind = CoreErrorKind.IncompatibleCore,
        message = "The embedded Core ABI or protocol is incompatible with this client.",
    )
}

data class ProviderProfile(
    val id: String,
    val name: String,
    val endpoint: String,
    val model: String,
    val secretRef: String?,
)

data class TranslationCommand(
    val sourceText: String,
    val sourceLocale: String?,
    val targetLocale: String,
    val profile: ProviderProfile,
)

enum class CoreErrorKind {
    Authentication,
    Cancelled,
    IncompatibleCore,
    InvalidConfiguration,
    Network,
    Protocol,
    Unknown,
}

sealed interface CoreEvent {
    data object Started : CoreEvent

    data class TextDelta(val text: String) : CoreEvent

    data object Completed : CoreEvent

    data object Cancelled : CoreEvent

    data class Failed(
        val kind: CoreErrorKind,
        val safeDiagnostic: String?,
    ) : CoreEvent
}

fun interface SecretResolver {
    suspend fun resolve(secretRef: String): ByteArray?
}

interface CoreGateway : AutoCloseable {
    val compatibility: CoreCompatibility

    suspend fun saveProviderProfile(profile: ProviderProfile)

    fun translate(
        command: TranslationCommand,
        secretResolver: SecretResolver,
    ): Flow<CoreEvent>

    fun cancel()
}

class CoreGatewayException(
    val kind: CoreErrorKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
