package org.linguamesh.android.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linguamesh.android.core.CoreErrorKind
import org.linguamesh.android.core.CoreEvent
import org.linguamesh.android.core.CoreGateway
import org.linguamesh.android.core.CoreGatewayException
import org.linguamesh.android.core.ProviderProfile
import org.linguamesh.android.core.SecretResolver
import org.linguamesh.android.core.TranslationCommand
import org.linguamesh.android.security.CredentialStore

enum class TranslationStatus {
    Ready,
    Translating,
    Completed,
    Cancelled,
    Failed,
}

data class TranslationUiState(
    val sourceText: String = "",
    val outputText: String = "",
    val targetLocale: String = "zh-CN",
    val profiles: List<ProviderProfile> = emptyList(),
    val activeProfileId: String? = null,
    val status: TranslationStatus = TranslationStatus.Ready,
    val errorKind: CoreErrorKind? = null,
    val settingsVisible: Boolean = false,
    val coreAbiMajor: UInt = 0u,
    val protocolVersion: UInt = 0u,
) {
    val activeProfile: ProviderProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }

    val canTranslate: Boolean
        get() = sourceText.isNotBlank() && activeProfile != null && status != TranslationStatus.Translating
}

class TranslationViewModel(
    private val coreGateway: CoreGateway,
    private val credentialStore: CredentialStore,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        TranslationUiState(
            coreAbiMajor = coreGateway.compatibility.abiMajor,
            protocolVersion = coreGateway.compatibility.protocolVersion,
        ),
    )
    val state: StateFlow<TranslationUiState> = mutableState.asStateFlow()
    private var translationJob: Job? = null

    fun updateSourceText(value: String) {
        mutableState.update { it.copy(sourceText = value, errorKind = null) }
    }

    fun updateTargetLocale(value: String) {
        mutableState.update { it.copy(targetLocale = value) }
    }

    fun setSettingsVisible(visible: Boolean) {
        mutableState.update { it.copy(settingsVisible = visible) }
    }

    fun saveProvider(
        name: String,
        endpoint: String,
        model: String,
        credential: CharArray,
    ) {
        val credentialCopy = credential.copyOf()
        credential.fill('\u0000')
        val normalizedName = name.trim()
        val normalizedEndpoint = endpoint.trim().trimEnd('/')
        val normalizedModel = model.trim()
        if (
            normalizedName.isEmpty() ||
            normalizedModel.isEmpty() ||
            !isAllowedEndpoint(normalizedEndpoint)
        ) {
            credentialCopy.fill('\u0000')
            mutableState.update { it.copy(errorKind = CoreErrorKind.InvalidConfiguration) }
            return
        }

        viewModelScope.launch(backgroundDispatcher) {
            val profileId = UUID.randomUUID().toString()
            val secretRef = credentialCopy.takeIf { it.isNotEmpty() }?.let { "provider/$profileId" }
            try {
                if (secretRef != null) {
                    credentialStore.store(secretRef, credentialCopy)
                } else {
                    credentialCopy.fill('\u0000')
                }
                val profile = ProviderProfile(
                    id = profileId,
                    name = normalizedName,
                    endpoint = normalizedEndpoint,
                    model = normalizedModel,
                    secretRef = secretRef,
                )
                coreGateway.saveProviderProfile(profile)
                mutableState.update { current ->
                    current.copy(
                        profiles = current.profiles + profile,
                        activeProfileId = profile.id,
                        status = TranslationStatus.Ready,
                        errorKind = null,
                    )
                }
            } catch (error: CancellationException) {
                if (secretRef != null) {
                    deleteCredentialAfterFailure(secretRef)
                }
                throw error
            } catch (error: Exception) {
                if (secretRef != null) {
                    deleteCredentialAfterFailure(secretRef)
                }
                mutableState.update {
                    it.copy(
                        status = TranslationStatus.Failed,
                        errorKind = error.toCoreErrorKind(),
                    )
                }
            } finally {
                credentialCopy.fill('\u0000')
            }
        }
    }

    fun switchProfile(profileId: String) {
        mutableState.update { current ->
            if (current.profiles.none { it.id == profileId }) {
                current.copy(errorKind = CoreErrorKind.InvalidConfiguration)
            } else {
                current.copy(activeProfileId = profileId, errorKind = null)
            }
        }
    }

    fun translate() {
        val snapshot = mutableState.value
        val profile = snapshot.activeProfile ?: return
        if (snapshot.sourceText.isBlank() || translationJob?.isActive == true) {
            return
        }
        val command = TranslationCommand(
            sourceText = snapshot.sourceText,
            sourceLocale = null,
            targetLocale = snapshot.targetLocale,
            profile = profile,
        )
        mutableState.update {
            it.copy(
                outputText = "",
                status = TranslationStatus.Translating,
                errorKind = null,
            )
        }
        translationJob = viewModelScope.launch {
            try {
                coreGateway.translate(
                    command = command,
                    secretResolver = SecretResolver(credentialStore::resolve),
                )
                    .flowOn(backgroundDispatcher)
                    .collect(::handleCoreEvent)
                mutableState.update { current ->
                    if (current.status == TranslationStatus.Translating) {
                        current.copy(
                            status = TranslationStatus.Failed,
                            errorKind = CoreErrorKind.Protocol,
                        )
                    } else {
                        current
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        status = TranslationStatus.Failed,
                        errorKind = error.toCoreErrorKind(),
                    )
                }
            } finally {
                translationJob = null
            }
        }
    }

    fun cancel() {
        if (mutableState.value.status != TranslationStatus.Translating) {
            return
        }
        viewModelScope.launch(backgroundDispatcher) {
            try {
                coreGateway.cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                translationJob?.cancel()
                mutableState.update {
                    it.copy(
                        status = TranslationStatus.Failed,
                        errorKind = error.toCoreErrorKind(),
                    )
                }
            }
        }
    }

    private fun handleCoreEvent(event: CoreEvent) {
        mutableState.update { current ->
            when (event) {
                CoreEvent.Started -> current.copy(status = TranslationStatus.Translating)
                is CoreEvent.TextDelta -> current.copy(outputText = current.outputText + event.text)
                CoreEvent.Completed -> current.copy(status = TranslationStatus.Completed)
                CoreEvent.Cancelled -> current.copy(status = TranslationStatus.Cancelled)
                is CoreEvent.Failed -> current.copy(
                    status = TranslationStatus.Failed,
                    errorKind = event.kind,
                )
            }
        }
    }

    private fun isAllowedEndpoint(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val host = uri.host?.removeSurrounding("[", "]")?.lowercase() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            return false
        }
        return scheme == "https" ||
            (scheme == "http" && host in LOOPBACK_HOSTS)
    }

    private suspend fun deleteCredentialAfterFailure(secretRef: String) {
        withContext(NonCancellable) {
            runCatching { credentialStore.delete(secretRef) }
        }
    }

    private fun Throwable.toCoreErrorKind(): CoreErrorKind =
        (this as? CoreGatewayException)?.kind ?: CoreErrorKind.Unknown

    companion object {
        private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1")

        fun factory(
            coreGateway: CoreGateway,
            credentialStore: CredentialStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TranslationViewModel::class.java))
                return TranslationViewModel(coreGateway, credentialStore) as T
            }
        }
    }
}
