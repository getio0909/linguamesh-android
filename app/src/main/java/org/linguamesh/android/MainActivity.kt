package org.linguamesh.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linguamesh.android.preferences.UiPreferences
import org.linguamesh.android.preferences.setLocaleInLifecycle
import org.linguamesh.android.preferences.setThemeInLifecycle
import org.linguamesh.android.translation.TranslationViewModel
import org.linguamesh.android.ui.LinguaMeshApp
import org.linguamesh.android.ui.LinguaMeshTheme
import org.linguamesh.android.ui.LocalizedContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LinguaMeshApplication).container
        setContent {
            val translationViewModel: TranslationViewModel = viewModel(
                factory = TranslationViewModel.factory(
                    coreGateway = container.coreGateway,
                    credentialStore = container.credentialStore,
                    providerProfileRepository = container.providerProfileRepository,
                ),
            )
            val translationState by translationViewModel.state.collectAsStateWithLifecycle()
            val uiPreferences by container.uiPreferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = UiPreferences())

            LocalizedContent(localeTag = uiPreferences.localeTag) {
                LinguaMeshTheme(themePreference = uiPreferences.theme) {
                    LinguaMeshApp(
                        state = translationState,
                        uiPreferences = uiPreferences,
                        onSourceChanged = translationViewModel::updateSourceText,
                        onTargetLocaleChanged = translationViewModel::updateTargetLocale,
                        onSaveProvider = translationViewModel::saveProvider,
                        onSwitchProfile = translationViewModel::switchProfile,
                        onTranslate = translationViewModel::translate,
                        onCancel = translationViewModel::cancel,
                        onSettingsVisible = translationViewModel::setSettingsVisible,
                        onThemeChanged = { theme ->
                            container.uiPreferencesRepository.setThemeInLifecycle(
                                owner = this,
                                theme = theme,
                            )
                        },
                        onLocaleChanged = { localeTag ->
                            container.uiPreferencesRepository.setLocaleInLifecycle(
                                owner = this,
                                localeTag = localeTag,
                            )
                        },
                    )
                }
            }
        }
    }
}
