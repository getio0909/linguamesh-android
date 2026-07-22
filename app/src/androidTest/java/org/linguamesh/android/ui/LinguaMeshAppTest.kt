package org.linguamesh.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.linguamesh.android.core.ProviderProfile
import org.linguamesh.android.preferences.UiPreferences
import org.linguamesh.android.translation.TranslationStatus
import org.linguamesh.android.translation.TranslationUiState

class LinguaMeshAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun translationWorkspaceDisplaysSourceAndStreamedOutput() {
        val profile = ProviderProfile(
            id = "local",
            name = "Local",
            endpoint = "http://127.0.0.1:8787/v1",
            model = "fake-translator",
            secretRef = null,
        )
        composeRule.setContent {
            LinguaMeshTheme(themePreference = UiPreferences().theme) {
                LinguaMeshApp(
                    state = TranslationUiState(
                        sourceText = "Hello",
                        outputText = "你好",
                        profiles = listOf(profile),
                        activeProfileId = profile.id,
                        status = TranslationStatus.Completed,
                    ),
                    uiPreferences = UiPreferences(),
                    onSourceChanged = {},
                    onTargetLocaleChanged = {},
                    onSaveProvider = { _, _, _, secret -> secret.fill('\u0000') },
                    onSwitchProfile = {},
                    onTranslate = {},
                    onCancel = {},
                    onSettingsVisible = {},
                    onThemeChanged = {},
                    onLocaleChanged = {},
                )
            }
        }

        composeRule.onNodeWithTag("source-input").assertIsDisplayed()
        composeRule.onNodeWithTag("translation-output").assertIsDisplayed()
        composeRule.onNodeWithText("你好").assertIsDisplayed()
    }
}
