package org.linguamesh.android

import android.app.Application
import org.linguamesh.android.core.CoreGateway
import org.linguamesh.android.preferences.UiPreferencesRepository
import org.linguamesh.android.security.AndroidKeystoreCredentialStore
import org.linguamesh.android.security.CredentialStore

class LinguaMeshApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(
            coreGateway = CoreGatewayFactory.create(),
            credentialStore = AndroidKeystoreCredentialStore(this),
            uiPreferencesRepository = UiPreferencesRepository(this),
        )
    }
}

data class AppContainer(
    val coreGateway: CoreGateway,
    val credentialStore: CredentialStore,
    val uiPreferencesRepository: UiPreferencesRepository,
)
