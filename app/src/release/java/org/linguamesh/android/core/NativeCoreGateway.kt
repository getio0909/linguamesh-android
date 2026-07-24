package org.linguamesh.android.core

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.linguamesh.core.CoreEvent as NativeCoreEvent
import org.linguamesh.core.CoreException
import org.linguamesh.core.CoreResult
import org.linguamesh.core.HostSecretResolution
import org.linguamesh.core.LinguaMeshEngine

class NativeCoreGateway(
    private val engine: LinguaMeshEngine = LinguaMeshEngine.create(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoreGateway {
    override val compatibility = CoreCompatibility(
        abiMajor = engine.compatibility.abiMajor,
        protocolVersion = engine.compatibility.protocolVersion,
    )
    private val profiles = ConcurrentHashMap<String, ProviderProfile>()
    private val active = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val recoveryFailure = AtomicReference<String?>(null)
    private val cancellationRecovery = CancellationRecoveryDrainer()

    override suspend fun saveProviderProfile(profile: ProviderProfile) {
        checkOpen()
        profiles[profile.id] = profile
    }

    override fun translate(
        command: TranslationCommand,
        secretResolver: SecretResolver,
    ): Flow<CoreEvent> = flow {
        checkOpen()
        if (profiles[command.profile.id] != command.profile) {
            throw CoreGatewayException(
                kind = CoreErrorKind.InvalidConfiguration,
                message = "Provider profile is not registered for this session",
            )
        }
        if (!active.compareAndSet(false, true)) {
            throw CoreGatewayException(CoreErrorKind.Protocol, "Core operation is already active")
        }
        val operationId = UUID.randomUUID().toString()
        val correlationId = UUID.randomUUID().toString()
        var lastSequence: ULong? = null
        var terminal = false
        try {
            engine.translateText(
                operationId = operationId,
                correlationId = correlationId,
                endpoint = command.profile.endpoint,
                modelId = command.profile.model,
                sourceText = command.sourceText,
                targetLocale = command.targetLocale,
                secretRef = command.profile.secretRef,
            )
            while (!terminal) {
                currentCoroutineContext().ensureActive()
                val event = engine.pollDecodedEvent(POLL_TIMEOUT_MILLIS) ?: continue
                if (event.operationId != operationId || event.correlationId != correlationId) {
                    throw isolatedProtocolFailure("Core event identity mismatch")
                }
                val sequence = event.sequence
                lastSequence?.let { previousSequence ->
                    if (sequence <= previousSequence) {
                        throw isolatedProtocolFailure("Core event sequence is not increasing")
                    }
                }
                lastSequence = sequence
                when (event) {
                    is NativeCoreEvent.SecretRequired -> {
                        val expectedSecretRef = command.profile.secretRef
                        if (expectedSecretRef == null || event.secretRef != expectedSecretRef) {
                            throw isolatedProtocolFailure("Core requested an unexpected secret reference")
                        }
                        sendHostSecretResponse(
                            operationId = event.operationId,
                            correlationId = event.correlationId,
                            requestId = event.requestId,
                            secretRef = event.secretRef,
                            secretResolver = secretResolver,
                        )
                    }
                    is NativeCoreEvent.Started -> emit(CoreEvent.Started)
                    is NativeCoreEvent.TextDelta -> emit(CoreEvent.TextDelta(event.text))
                    is NativeCoreEvent.Completed -> {
                        terminal = true
                        emit(CoreEvent.Completed)
                    }
                    is NativeCoreEvent.Cancelled -> {
                        terminal = true
                        emit(CoreEvent.Cancelled)
                    }
                    is NativeCoreEvent.Failed -> {
                        terminal = true
                        emit(
                            CoreEvent.Failed(
                                kind = event.errorKind.toCoreErrorKind(),
                                safeDiagnostic = event.message,
                            ),
                        )
                    }
                    is NativeCoreEvent.Unknown ->
                        throw isolatedProtocolFailure("Core emitted an unsupported event type")
                }
            }
        } catch (error: CancellationException) {
            if (!terminal) {
                val recoveryResult = withContext(NonCancellable + ioDispatcher) {
                    cancellationRecovery.drain(
                        operationId = operationId,
                        correlationId = correlationId,
                        cancel = engine::cancel,
                        poll = {
                            engine.pollEnvelope(POLL_TIMEOUT_MILLIS)?.let { envelope ->
                                CancellationRecoveryEvent(
                                    operationId = envelope.operationId,
                                    correlationId = envelope.correlationId,
                                    terminal = envelope.messageType in TERMINAL_MESSAGE_TYPES,
                                )
                            }
                        },
                    )
                }
                recoveryResult.diagnostic?.let { diagnostic ->
                    recoveryFailure.compareAndSet(null, diagnostic)
                }
            }
            throw error
        } catch (error: CoreException) {
            if (error.result == CoreResult.MALFORMED_MESSAGE) {
                runCatching { engine.cancel() }
            }
            throw error.toGatewayException()
        } finally {
            active.set(false)
        }
    }.flowOn(ioDispatcher)

    override fun cancel() {
        if (active.get() && !closed.get()) {
            try {
                engine.cancel()
            } catch (error: CoreException) {
                throw error.toGatewayException()
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            engine.close()
            profiles.clear()
        }
    }

    private fun checkOpen() {
        if (closed.get()) {
            throw CoreGatewayException(CoreErrorKind.Protocol, "Core gateway is closed")
        }
        recoveryFailure.get()?.let { diagnostic ->
            throw CoreGatewayException(CoreErrorKind.Protocol, diagnostic)
        }
    }

    private fun isolatedProtocolFailure(diagnostic: String): CoreGatewayException {
        recoveryFailure.compareAndSet(null, diagnostic)
        return CoreGatewayException(CoreErrorKind.Protocol, diagnostic)
    }

    private fun CoreException.toGatewayException(): CoreGatewayException {
        val kind = when (result) {
            CoreResult.PROTOCOL_INCOMPATIBLE -> CoreErrorKind.IncompatibleCore
            CoreResult.MALFORMED_MESSAGE,
            CoreResult.UNSUPPORTED_MESSAGE,
            -> CoreErrorKind.Protocol
            CoreResult.INVALID_ARGUMENT -> CoreErrorKind.InvalidConfiguration
            CoreResult.SHUTDOWN,
            CoreResult.BUSY,
            CoreResult.RESOURCE_EXHAUSTED,
            -> CoreErrorKind.Protocol
            CoreResult.PANIC,
            CoreResult.INTERNAL,
            CoreResult.UNKNOWN,
            CoreResult.OK,
            -> CoreErrorKind.Unknown
        }
        return CoreGatewayException(kind, message ?: "Core operation failed", this)
    }

    private fun String.toCoreErrorKind(): CoreErrorKind = when (this) {
        "authentication" -> CoreErrorKind.Authentication
        "cancelled" -> CoreErrorKind.Cancelled
        "invalid_endpoint", "model_unavailable" -> CoreErrorKind.InvalidConfiguration
        "network", "timeout" -> CoreErrorKind.Network
        "protocol_incompatible", "malformed_response" -> CoreErrorKind.Protocol
        else -> CoreErrorKind.Unknown
    }

    private suspend fun sendHostSecretResponse(
        operationId: String,
        correlationId: String,
        requestId: String,
        secretRef: String,
        secretResolver: SecretResolver,
    ) {
        if (requestId.isBlank() || requestId.length > MAX_HOST_REQUEST_ID_LENGTH) {
            throw isolatedProtocolFailure("Core emitted an invalid host secret request")
        }
        var secretBytes: ByteArray? = null
        var resolution = HostSecretResolution.UNAVAILABLE
        var secret: String? = null
        try {
            try {
                secretBytes = secretResolver.resolve(secretRef)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                resolution = HostSecretResolution.SECURE_STORAGE_UNAVAILABLE
            }
            if (resolution == HostSecretResolution.UNAVAILABLE && secretBytes != null) {
                if (secretBytes!!.size <= MAX_HOST_SECRET_BYTES) {
                    secret = secretBytes!!.decodeSecret()
                    if (secret.isNullOrEmpty()) {
                        secret = null
                    } else {
                        resolution = HostSecretResolution.PROVIDED
                    }
                }
            }
            engine.sendHostResponse(
                operationId = operationId,
                correlationId = correlationId,
                requestId = requestId,
                resolution = resolution,
                secret = secret,
            )
        } finally {
            secretBytes?.fill(0)
        }
    }

    private fun ByteArray.decodeSecret(): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(this))
            .toString()
    }.getOrNull()

    private companion object {
        const val POLL_TIMEOUT_MILLIS = 100u
        const val MESSAGE_COMPLETED = "completed"
        const val MESSAGE_CANCELLED = "cancelled"
        const val MESSAGE_FAILED = "failed"
        const val MAX_HOST_REQUEST_ID_LENGTH = 128
        const val MAX_HOST_SECRET_BYTES = 64 * 1024
        val TERMINAL_MESSAGE_TYPES = setOf(MESSAGE_COMPLETED, MESSAGE_CANCELLED, MESSAGE_FAILED)
    }
}
