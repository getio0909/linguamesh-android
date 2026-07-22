package org.linguamesh.android.core

import com.google.protobuf.InvalidProtocolBufferException
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
import org.linguamesh.core.CoreException
import org.linguamesh.core.CoreResult
import org.linguamesh.core.LinguaMeshEngine
import org.linguamesh.core.protocol.FailureEvent
import org.linguamesh.core.protocol.TextDeltaEvent

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
        if (command.profile.secretRef != null) {
            emit(
                CoreEvent.Failed(
                    kind = CoreErrorKind.InvalidConfiguration,
                    safeDiagnostic = "Credential host responses are not supported by this core prerelease",
                ),
            )
            return@flow
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
            )
            while (!terminal) {
                currentCoroutineContext().ensureActive()
                val envelope = engine.pollEnvelope(POLL_TIMEOUT_MILLIS) ?: continue
                if (envelope.operationId != operationId || envelope.correlationId != correlationId) {
                    throw isolatedProtocolFailure("Core event identity mismatch")
                }
                val sequence = envelope.sequence.toULong()
                lastSequence?.let { previousSequence ->
                    if (sequence <= previousSequence) {
                        throw isolatedProtocolFailure("Core event sequence is not increasing")
                    }
                }
                lastSequence = sequence
                when (envelope.messageType) {
                    MESSAGE_STARTED -> emit(CoreEvent.Started)
                    MESSAGE_TEXT_DELTA -> emit(
                        CoreEvent.TextDelta(TextDeltaEvent.parseFrom(envelope.payload).text),
                    )
                    MESSAGE_COMPLETED -> {
                        terminal = true
                        emit(CoreEvent.Completed)
                    }
                    MESSAGE_CANCELLED -> {
                        terminal = true
                        emit(CoreEvent.Cancelled)
                    }
                    MESSAGE_FAILED -> {
                        val failure = FailureEvent.parseFrom(envelope.payload)
                        terminal = true
                        emit(
                            CoreEvent.Failed(
                                kind = failure.errorKind.toCoreErrorKind(),
                                safeDiagnostic = failure.message,
                            ),
                        )
                    }
                    else -> throw isolatedProtocolFailure("Core emitted an unsupported event type")
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
        } catch (error: InvalidProtocolBufferException) {
            val failure = isolatedProtocolFailure("Core emitted a malformed event payload")
            try {
                engine.cancel()
            } catch (_: CoreException) {}
            throw CoreGatewayException(failure.kind, failure.message.orEmpty(), error)
        } catch (error: CoreException) {
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

    private companion object {
        const val POLL_TIMEOUT_MILLIS = 100u
        const val MESSAGE_STARTED = "started"
        const val MESSAGE_TEXT_DELTA = "text_delta"
        const val MESSAGE_COMPLETED = "completed"
        const val MESSAGE_CANCELLED = "cancelled"
        const val MESSAGE_FAILED = "failed"
        val TERMINAL_MESSAGE_TYPES = setOf(MESSAGE_COMPLETED, MESSAGE_CANCELLED, MESSAGE_FAILED)
    }
}
