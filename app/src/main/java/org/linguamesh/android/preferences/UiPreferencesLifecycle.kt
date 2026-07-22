package org.linguamesh.android.preferences

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

fun UiPreferencesRepository.setThemeInLifecycle(
    owner: LifecycleOwner,
    theme: ThemePreference,
) {
    owner.lifecycleScope.launch {
        setTheme(theme)
    }
}

fun UiPreferencesRepository.setLocaleInLifecycle(
    owner: LifecycleOwner,
    localeTag: String,
) {
    owner.lifecycleScope.launch {
        setLocale(localeTag)
    }
}
