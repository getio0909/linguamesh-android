package org.linguamesh.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CancellationRecoveryDrainerTest {
    @Test
    fun drainsMatchingOperationUntilTerminalEvent() {
        val events = ArrayDeque(
            listOf(
                CancellationRecoveryEvent("operation", "correlation", terminal = false),
                CancellationRecoveryEvent("operation", "correlation", terminal = true),
            ),
        )
        var cancelCalls = 0
        val drainer = CancellationRecoveryDrainer()

        val result = drainer.drain(
            operationId = "operation",
            correlationId = "correlation",
            cancel = { cancelCalls += 1 },
            poll = { events.removeFirstOrNull() },
        )

        assertEquals(1, cancelCalls)
        assertEquals(CancellationRecoveryResult.Recovered, result)
    }

    @Test
    fun rejectsEventFromAnotherOperation() {
        val drainer = CancellationRecoveryDrainer()

        val result = drainer.drain(
            operationId = "operation",
            correlationId = "correlation",
            cancel = {},
            poll = { CancellationRecoveryEvent("old-operation", "correlation", terminal = true) },
        )

        assertEquals(CancellationRecoveryResult.IdentityMismatch, result)
    }

    @Test
    fun boundsDrainWhenTerminalEventNeverArrives() {
        var now = 0L
        var pollCalls = 0
        val drainer = CancellationRecoveryDrainer(
            timeoutNanos = 10L,
            nanoTime = { now },
        )

        val result = drainer.drain(
            operationId = "operation",
            correlationId = "correlation",
            cancel = {},
            poll = {
                pollCalls += 1
                now += 10L
                null
            },
        )

        assertEquals(1, pollCalls)
        assertEquals(CancellationRecoveryResult.TimedOut, result)
    }

    @Test
    fun recoversWhenTerminalEventExistsAfterCancellationRequestFailure() {
        var pollCalls = 0
        val drainer = CancellationRecoveryDrainer()

        val result = drainer.drain(
            operationId = "operation",
            correlationId = "correlation",
            cancel = { error("cancel failed") },
            poll = {
                pollCalls += 1
                CancellationRecoveryEvent("operation", "correlation", terminal = true)
            },
        )

        assertEquals(1, pollCalls)
        assertEquals(CancellationRecoveryResult.Recovered, result)
    }

    @Test
    fun reportsCancellationFailureWhenDrainCannotRecover() {
        val drainer = CancellationRecoveryDrainer(maxPolls = 1)

        val result = drainer.drain(
            operationId = "operation",
            correlationId = "correlation",
            cancel = { error("cancel failed") },
            poll = { null },
        )

        assertEquals(CancellationRecoveryResult.CancelFailed, result)
    }
}
