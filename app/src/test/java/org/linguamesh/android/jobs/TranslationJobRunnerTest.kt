package org.linguamesh.android.jobs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.linguamesh.android.core.CoreCompatibility
import org.linguamesh.android.core.CoreErrorKind
import org.linguamesh.android.core.CoreEvent
import org.linguamesh.android.core.CoreGateway
import org.linguamesh.android.core.ProviderProfile
import org.linguamesh.android.core.SecretResolver
import org.linguamesh.android.core.TranslationCommand
import org.linguamesh.android.preferences.InMemoryProviderProfileRepository
import org.linguamesh.android.security.CredentialStore

class TranslationJobRunnerTest {
    @Test
    fun restoresProfileAndCompletesStreamingTranslation() = runTest {
        val profile = profile()
        val repository = InMemoryProviderProfileRepository().also { it.upsert(profile) }
        val gateway = FakeGateway(
            flow {
                emit(CoreEvent.Started)
                emit(CoreEvent.TextDelta("你"))
                emit(CoreEvent.TextDelta("好"))
                emit(CoreEvent.Completed)
            },
        )

        val result = TranslationJobRunner(gateway, FakeCredentialStore(), repository).run(job())

        assertEquals(TranslationJobOutcome.Completed("你好"), result)
        assertEquals(listOf(profile), gateway.savedProfiles)
    }

    @Test
    fun missingProfileFailsWithoutStartingCore() = runTest {
        val gateway = FakeGateway(flow { emit(CoreEvent.Completed) })

        val result = TranslationJobRunner(
            gateway,
            FakeCredentialStore(),
            InMemoryProviderProfileRepository(),
        ).run(job())

        assertEquals(TranslationJobOutcome.Failed("profile_not_found"), result)
        assertTrue(gateway.savedProfiles.isEmpty())
    }

    @Test
    fun networkFailureIsRetriedByWorkManager() = runTest {
        val repository = InMemoryProviderProfileRepository().also { it.upsert(profile()) }
        val gateway = FakeGateway(flow { emit(CoreEvent.Failed(CoreErrorKind.Network, null)) })

        val result = TranslationJobRunner(gateway, FakeCredentialStore(), repository).run(job())

        assertEquals(TranslationJobOutcome.Retry("network"), result)
    }

    @Test
    fun streamWithoutTerminalEventFailsAsProtocol() = runTest {
        val repository = InMemoryProviderProfileRepository().also { it.upsert(profile()) }
        val gateway = FakeGateway(flow { emit(CoreEvent.Started) })

        val result = TranslationJobRunner(gateway, FakeCredentialStore(), repository).run(job())

        assertEquals(TranslationJobOutcome.Failed("protocol"), result)
    }

    private fun profile() = ProviderProfile("profile", "Local", "http://127.0.0.1:8787/v1", "model", null)

    private fun job() = TranslationJob("job", "profile", "Hello", null, "zh-CN")
}

private class FakeGateway(private val events: Flow<CoreEvent>) : CoreGateway {
    override val compatibility = CoreCompatibility(0u, 1u)
    val savedProfiles = mutableListOf<ProviderProfile>()

    override suspend fun saveProviderProfile(profile: ProviderProfile) {
        savedProfiles += profile
    }

    override fun translate(command: TranslationCommand, secretResolver: SecretResolver): Flow<CoreEvent> = events

    override fun cancel() = Unit

    override fun close() = Unit
}

private class FakeCredentialStore : CredentialStore {
    override suspend fun store(secretRef: String, secret: CharArray) = Unit

    override suspend fun resolve(secretRef: String): ByteArray? = null

    override suspend fun delete(secretRef: String) = Unit
}
