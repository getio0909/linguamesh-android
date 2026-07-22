package org.linguamesh.android.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UnavailableCoreGateway : CoreGateway {
    override val compatibility = CoreCompatibility(abiMajor = 0u, protocolVersion = 0u)

    override suspend fun saveProviderProfile(profile: ProviderProfile) {
        throw CoreGatewayException(
            kind = CoreErrorKind.IncompatibleCore,
            message = "LinguaMesh Core is unavailable",
        )
    }

    override fun translate(
        command: TranslationCommand,
        secretResolver: SecretResolver,
    ): Flow<CoreEvent> = flow {
        emit(CoreEvent.Failed(CoreErrorKind.IncompatibleCore, "Core library unavailable"))
    }

    override fun cancel() = Unit

    override fun close() = Unit
}
