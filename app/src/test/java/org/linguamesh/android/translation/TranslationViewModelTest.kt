package org.linguamesh.android.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.linguamesh.android.core.CoreCompatibility
import org.linguamesh.android.core.CoreErrorKind
import org.linguamesh.android.core.CoreEvent
import org.linguamesh.android.core.CoreGateway
import org.linguamesh.android.core.CoreGatewayException
import org.linguamesh.android.core.ProviderProfile
import org.linguamesh.android.core.SecretResolver
import org.linguamesh.android.core.TranslationCommand
import org.linguamesh.android.security.CredentialStore

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun providerCredentialIsBrokeredAndNeverStoredInUiState() = runTest(dispatcher) {
        val gateway = FakeCoreGateway()
        val credentials = FakeCredentialStore()
        val viewModel = TranslationViewModel(gateway, credentials, dispatcher)
        val input = "test-secret".toCharArray()

        viewModel.saveProvider(
            name = "Local",
            endpoint = "http://127.0.0.1:8787/v1",
            model = "fake-translator",
            credential = input,
        )
        assertTrue(input.all { it == '\u0000' })
        advanceUntilIdle()

        val profile = viewModel.state.value.activeProfile
        assertEquals("test-secret", credentials.lastStoredSecret)
        assertEquals(profile, gateway.savedProfiles.single())
        assertTrue(profile?.secretRef?.startsWith("provider/") == true)
        assertFalse(viewModel.state.value.toString().contains("test-secret"))
    }

    @Test
    fun remotePlaintextEndpointIsRejectedAndInputIsCleared() = runTest(dispatcher) {
        val viewModel = TranslationViewModel(FakeCoreGateway(), FakeCredentialStore(), dispatcher)
        val input = "test-secret".toCharArray()

        viewModel.saveProvider("Remote", "http://example.com/v1", "model", input)
        advanceUntilIdle()

        assertTrue(input.all { it == '\u0000' })
        assertTrue(viewModel.state.value.profiles.isEmpty())
        assertEquals(CoreErrorKind.InvalidConfiguration, viewModel.state.value.errorKind)
    }

    @Test
    fun emulatorHostAliasRequiresSecureTransportOrAdbReverse() = runTest(dispatcher) {
        val viewModel = TranslationViewModel(FakeCoreGateway(), FakeCredentialStore(), dispatcher)

        viewModel.saveProvider("Emulator host", "http://10.0.2.2:40123/v1", "model", CharArray(0))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.profiles.isEmpty())
        assertEquals(CoreErrorKind.InvalidConfiguration, viewModel.state.value.errorKind)
    }

    @Test
    fun ipv6LoopbackEndpointIsAccepted() = runTest(dispatcher) {
        val viewModel = TranslationViewModel(FakeCoreGateway(), FakeCredentialStore(), dispatcher)

        viewModel.saveProvider("IPv6 loopback", "http://[::1]:40123/v1", "model", CharArray(0))
        advanceUntilIdle()

        assertEquals("http://[::1]:40123/v1", viewModel.state.value.activeProfile?.endpoint)
        assertEquals(null, viewModel.state.value.errorKind)
    }

    @Test
    fun streamingAppendsDeltasAndCompletes() = runTest(dispatcher) {
        val gateway = FakeCoreGateway()
        val viewModel = configuredViewModel(gateway)
        viewModel.updateSourceText("Hello")

        viewModel.translate()
        dispatcher.scheduler.runCurrent()
        gateway.emit(CoreEvent.Started)
        gateway.emit(CoreEvent.TextDelta("你"))
        gateway.emit(CoreEvent.TextDelta("好"))
        gateway.emit(CoreEvent.Completed)
        advanceUntilIdle()

        assertEquals("你好", viewModel.state.value.outputText)
        assertEquals(TranslationStatus.Completed, viewModel.state.value.status)
        assertNull(viewModel.state.value.errorKind)
    }

    @Test
    fun cancellationKeepsPartialOutput() = runTest(dispatcher) {
        val gateway = FakeCoreGateway()
        val viewModel = configuredViewModel(gateway)
        viewModel.updateSourceText("Hello")
        viewModel.translate()
        dispatcher.scheduler.runCurrent()
        gateway.emit(CoreEvent.Started)
        gateway.emit(CoreEvent.TextDelta("你"))
        dispatcher.scheduler.runCurrent()

        viewModel.cancel()
        advanceUntilIdle()

        assertEquals(1, gateway.cancelCalls)
        assertEquals("你", viewModel.state.value.outputText)
        assertEquals(TranslationStatus.Cancelled, viewModel.state.value.status)
    }

    @Test
    fun switchingProfileDoesNotCopyCredentialReference() = runTest(dispatcher) {
        val gateway = FakeCoreGateway()
        val viewModel = TranslationViewModel(gateway, FakeCredentialStore(), dispatcher)
        viewModel.saveProvider("First", "https://first.example/v1", "first", "one".toCharArray())
        viewModel.saveProvider("Second", "https://second.example/v1", "second", "two".toCharArray())
        advanceUntilIdle()

        val first = viewModel.state.value.profiles.first()
        val second = viewModel.state.value.profiles.last()
        viewModel.switchProfile(first.id)

        assertEquals(first.id, viewModel.state.value.activeProfileId)
        assertFalse(first.secretRef == second.secretRef)
    }

    @Test
    fun providerSaveFailureDeletesBrokeredCredential() = runTest(dispatcher) {
        val gateway = FakeCoreGateway().apply {
            saveFailure = CoreGatewayException(CoreErrorKind.Network, "Provider save failed")
        }
        val credentials = FakeCredentialStore()
        val viewModel = TranslationViewModel(gateway, credentials, dispatcher)

        viewModel.saveProvider(
            "Remote",
            "https://provider.example/v1",
            "model",
            "test-secret".toCharArray(),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.profiles.isEmpty())
        assertEquals(CoreErrorKind.Network, viewModel.state.value.errorKind)
        assertEquals(1, credentials.deletedRefs.size)
        assertFalse(credentials.hasStoredSecrets())
    }

    @Test
    fun streamWithoutTerminalEventFailsAsProtocolError() = runTest(dispatcher) {
        val gateway = FakeCoreGateway()
        val viewModel = configuredViewModel(gateway)
        viewModel.updateSourceText("Hello")
        viewModel.translate()
        dispatcher.scheduler.runCurrent()

        gateway.emit(CoreEvent.Started)
        gateway.completeWithoutTerminal()
        advanceUntilIdle()

        assertEquals(TranslationStatus.Failed, viewModel.state.value.status)
        assertEquals(CoreErrorKind.Protocol, viewModel.state.value.errorKind)
    }

    @Test
    fun cancellationFailureIsMappedWithoutBlockingCaller() = runTest(dispatcher) {
        val gateway = FakeCoreGateway().apply {
            cancelFailure = CoreGatewayException(CoreErrorKind.Network, "Cancellation failed")
        }
        val viewModel = configuredViewModel(gateway)
        viewModel.updateSourceText("Hello")
        viewModel.translate()
        dispatcher.scheduler.runCurrent()

        viewModel.cancel()
        dispatcher.scheduler.runCurrent()

        assertEquals(TranslationStatus.Failed, viewModel.state.value.status)
        assertEquals(CoreErrorKind.Network, viewModel.state.value.errorKind)
        gateway.completeWithoutTerminal()
        advanceUntilIdle()
    }

    @Test
    fun clearingViewModelLeavesApplicationOwnedGatewayOpen() {
        val gateway = FakeCoreGateway()
        val store = ViewModelStore()
        ViewModelProvider(
            store,
            TranslationViewModel.factory(gateway, FakeCredentialStore()),
        )[TranslationViewModel::class.java]

        store.clear()

        assertEquals(0, gateway.cancelCalls)
        assertEquals(0, gateway.closeCalls)
    }

    private suspend fun configuredViewModel(gateway: FakeCoreGateway): TranslationViewModel {
        val viewModel = TranslationViewModel(gateway, FakeCredentialStore(), dispatcher)
        viewModel.saveProvider(
            "Local",
            "http://127.0.0.1:8787/v1",
            "fake-translator",
            CharArray(0),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }
}

private class FakeCoreGateway : CoreGateway {
    override val compatibility = CoreCompatibility(abiMajor = 0u, protocolVersion = 1u)
    val savedProfiles = mutableListOf<ProviderProfile>()
    var cancelCalls = 0
    var closeCalls = 0
    var saveFailure: Exception? = null
    var cancelFailure: Exception? = null
    private var events = Channel<CoreEvent>(Channel.UNLIMITED)

    override suspend fun saveProviderProfile(profile: ProviderProfile) {
        saveFailure?.let { throw it }
        savedProfiles += profile
    }

    override fun translate(
        command: TranslationCommand,
        secretResolver: SecretResolver,
    ): Flow<CoreEvent> {
        events = Channel(Channel.UNLIMITED)
        return events.receiveAsFlow()
    }

    suspend fun emit(event: CoreEvent) {
        events.send(event)
        if (event == CoreEvent.Completed || event == CoreEvent.Cancelled || event is CoreEvent.Failed) {
            events.close()
        }
    }

    fun completeWithoutTerminal() {
        events.close()
    }

    override fun cancel() {
        cancelCalls += 1
        cancelFailure?.let { throw it }
        events.trySend(CoreEvent.Cancelled)
        events.close()
    }

    override fun close() {
        closeCalls += 1
    }
}

private class FakeCredentialStore : CredentialStore {
    var lastStoredSecret: String? = null
    val deletedRefs = mutableListOf<String>()
    private val secrets = mutableMapOf<String, ByteArray>()

    override suspend fun store(
        secretRef: String,
        secret: CharArray,
    ) {
        lastStoredSecret = secret.concatToString()
        secrets[secretRef] = secret.concatToString().encodeToByteArray()
        secret.fill('\u0000')
    }

    override suspend fun resolve(secretRef: String): ByteArray? = secrets[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        deletedRefs += secretRef
        secrets.remove(secretRef)?.fill(0)
    }

    fun hasStoredSecrets(): Boolean = secrets.isNotEmpty()
}
