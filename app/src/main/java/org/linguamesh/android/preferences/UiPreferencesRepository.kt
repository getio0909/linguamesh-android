package org.linguamesh.android.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemePreference {
    System,
    Light,
    Dark,
}

data class UiPreferences(
    val theme: ThemePreference = ThemePreference.System,
    val localeTag: String = "en",
)

private val Context.uiPreferencesDataStore by preferencesDataStore(name = "ui_preferences")

class UiPreferencesRepository(private val context: Context) {
    val preferences: Flow<UiPreferences> = context.uiPreferencesDataStore.data
        .map { values ->
            UiPreferences(
                theme = values[THEME_KEY]
                    ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                    ?: ThemePreference.System,
                localeTag = values[LOCALE_KEY]?.takeIf(SUPPORTED_LOCALES::contains) ?: "en",
            )
        }
        .catch {
            emit(UiPreferences())
        }

    suspend fun setTheme(theme: ThemePreference) {
        context.uiPreferencesDataStore.edit { values ->
            values[THEME_KEY] = theme.name
        }
    }

    suspend fun setLocale(localeTag: String) {
        require(localeTag in SUPPORTED_LOCALES)
        context.uiPreferencesDataStore.edit { values ->
            values[LOCALE_KEY] = localeTag
        }
    }

    companion object {
        val SUPPORTED_LOCALES = setOf(
            "en",
            "zh-Hans",
            "zh-Hant",
            "ja",
            "ko",
            "es",
            "fr",
            "de",
            "pt-BR",
            "ru",
            "ar",
            "hi",
        )
        private val THEME_KEY = stringPreferencesKey("theme")
        private val LOCALE_KEY = stringPreferencesKey("locale")
    }
}
