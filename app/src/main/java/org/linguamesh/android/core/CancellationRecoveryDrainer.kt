package org.linguamesh.android.core

internal data class CancellationRecoveryEvent(
    val operationId: String,
    val correlationId: String,
    val terminal: Boolean,
)

internal enum class CancellationRecoveryResult(val diagnostic: String?) {
    Recovered(null),
    CancelFailed("Core cancellation request failed during recovery"),
    IdentityMismatch("Core cancellation recovery received a mismatched event"),
    PollFailed("Core cancellation recovery polling failed"),
    TimedOut("Core cancellation recovery timed out"),
}

internal class CancellationRecoveryDrainer(
    private val timeoutNanos: Long = DEFAULT_TIMEOUT_NANOS,
    private val nanoTime: () -> Long = System::nanoTime,
    private val maxPolls: Int = DEFAULT_MAX_POLLS,
) {
    init {
        require(timeoutNanos > 0)
        require(maxPolls > 0)
    }

    fun drain(
        operationId: String,
        correlationId: String,
        cancel: () -> Unit,
        poll: () -> CancellationRecoveryEvent?,
    ): CancellationRecoveryResult {
        var cancelFailed = false
        try {
            cancel()
        } catch (_: Exception) {
            cancelFailed = true
        }

        val startedAt = nanoTime()
        repeat(maxPolls) {
            if (nanoTime() - startedAt >= timeoutNanos) {
                return CancellationRecoveryResult.TimedOut
            }
            val event = try {
                poll()
            } catch (_: Exception) {
                return CancellationRecoveryResult.PollFailed
            } ?: return@repeat
            if (event.operationId != operationId || event.correlationId != correlationId) {
                return CancellationRecoveryResult.IdentityMismatch
            }
            if (event.terminal) {
                return CancellationRecoveryResult.Recovered
            }
        }
        return if (cancelFailed) {
            CancellationRecoveryResult.CancelFailed
        } else {
            CancellationRecoveryResult.TimedOut
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_NANOS = 2_000_000_000L
        const val DEFAULT_MAX_POLLS = 4_096
    }
}
