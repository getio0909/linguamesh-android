package org.linguamesh.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.linguamesh.android.R
import org.linguamesh.android.core.CoreErrorKind
import org.linguamesh.android.core.ProviderProfile
import org.linguamesh.android.preferences.ThemePreference
import org.linguamesh.android.preferences.UiPreferences
import org.linguamesh.android.translation.TranslationStatus
import org.linguamesh.android.translation.TranslationUiState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LinguaMeshApp(
    state: TranslationUiState,
    uiPreferences: UiPreferences,
    onSourceChanged: (String) -> Unit,
    onTargetLocaleChanged: (String) -> Unit,
    onSaveProvider: (String, String, String, CharArray) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    onSettingsVisible: (Boolean) -> Unit,
    onThemeChanged: (ThemePreference) -> Unit,
    onLocaleChanged: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    if (state.profiles.isNotEmpty()) {
                        TextButton(onClick = { onSettingsVisible(true) }) {
                            Text(stringResource(R.string.settings_title))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.profiles.isEmpty()) {
            OnboardingScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                errorKind = state.errorKind,
                onSaveProvider = onSaveProvider,
            )
        } else {
            TranslationScreen(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                state = state,
                onSourceChanged = onSourceChanged,
                onTargetLocaleChanged = onTargetLocaleChanged,
                onSwitchProfile = onSwitchProfile,
                onTranslate = onTranslate,
                onCancel = onCancel,
            )
        }
    }

    if (state.settingsVisible) {
        ModalBottomSheet(onDismissRequest = { onSettingsVisible(false) }) {
            SettingsContent(
                state = state,
                uiPreferences = uiPreferences,
                onSaveProvider = onSaveProvider,
                onSwitchProfile = onSwitchProfile,
                onThemeChanged = onThemeChanged,
                onLocaleChanged = onLocaleChanged,
                onClose = { onSettingsVisible(false) },
            )
        }
    }
}

@Composable
private fun OnboardingScreen(
    modifier: Modifier,
    errorKind: CoreErrorKind?,
    onSaveProvider: (String, String, String, CharArray) -> Unit,
) {
    Box(
        modifier = modifier
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.onboarding_description),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                ProviderForm(
                    onSaveProvider = onSaveProvider,
                    submitLabel = stringResource(R.string.action_save_provider),
                )
            }
            errorKind?.let { kind ->
                item { ErrorMessage(kind) }
            }
        }
    }
}

@Composable
private fun TranslationScreen(
    modifier: Modifier,
    state: TranslationUiState,
    onSourceChanged: (String) -> Unit,
    onTargetLocaleChanged: (String) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
        if (wide) {
            Row(
                modifier = contentModifier,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TranslationControls(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onSourceChanged = onSourceChanged,
                    onTargetLocaleChanged = onTargetLocaleChanged,
                    onSwitchProfile = onSwitchProfile,
                    onTranslate = onTranslate,
                    onCancel = onCancel,
                )
                TranslationOutput(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    state = state,
                )
            }
        } else {
            LazyColumn(
                modifier = contentModifier.testTag("translation-screen"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    TranslationControls(
                        modifier = Modifier.fillMaxWidth(),
                        state = state,
                        onSourceChanged = onSourceChanged,
                        onTargetLocaleChanged = onTargetLocaleChanged,
                        onSwitchProfile = onSwitchProfile,
                        onTranslate = onTranslate,
                        onCancel = onCancel,
                    )
                }
                item {
                    TranslationOutput(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        state = state,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TranslationControls(
    modifier: Modifier,
    state: TranslationUiState,
    onSourceChanged: (String) -> Unit,
    onTargetLocaleChanged: (String) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
) {
    val sourceDescription = stringResource(R.string.accessibility_source_content)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileSelector(
            profiles = state.profiles,
            activeProfileId = state.activeProfileId,
            onSwitchProfile = onSwitchProfile,
        )
        state.activeProfile?.let { profile ->
            Text(
                text = stringResource(R.string.provider_active, profile.name),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(profile.model, style = MaterialTheme.typography.labelMedium)
        }
        Text(stringResource(R.string.settings_target_language), style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TARGET_LOCALES.forEach { (tag, label) ->
                FilterChip(
                    selected = state.targetLocale == tag,
                    onClick = { onTargetLocaleChanged(tag) },
                    label = { Text(label) },
                )
            }
        }
        OutlinedTextField(
            value = state.sourceText,
            onValueChange = onSourceChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("source-input")
                .semantics {
                    contentDescription = sourceDescription
                },
            label = { Text(stringResource(R.string.field_source_text)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onTranslate,
                enabled = state.canTranslate,
            ) {
                Text(stringResource(R.string.action_translate))
            }
            if (state.status == TranslationStatus.Translating) {
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
        StatusMessage(state)
    }
}

@Composable
private fun TranslationOutput(
    modifier: Modifier,
    state: TranslationUiState,
) {
    val outputDescription = stringResource(R.string.accessibility_translation_output)
    Surface(
        modifier = modifier
            .testTag("translation-output")
            .semantics {
                contentDescription = outputDescription
                liveRegion = LiveRegionMode.Polite
            },
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.field_translation),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.outputText,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun StatusMessage(state: TranslationUiState) {
    val status = when (state.status) {
        TranslationStatus.Ready,
        TranslationStatus.Completed,
        -> stringResource(R.string.status_ready)
        TranslationStatus.Translating -> stringResource(R.string.status_translating)
        TranslationStatus.Cancelled -> stringResource(R.string.status_cancelled)
        TranslationStatus.Failed -> null
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    state.errorKind?.let { ErrorMessage(it) }
}

@Composable
private fun ErrorMessage(kind: CoreErrorKind) {
    val resource = when (kind) {
        CoreErrorKind.Authentication -> R.string.error_authentication
        CoreErrorKind.Cancelled -> R.string.status_cancelled
        CoreErrorKind.IncompatibleCore,
        CoreErrorKind.Protocol,
        -> R.string.error_incompatible_core
        CoreErrorKind.InvalidConfiguration -> R.string.error_invalid_profile
        CoreErrorKind.Network -> R.string.error_network_unavailable
        CoreErrorKind.Unknown -> R.string.error_unknown
    }
    Text(
        text = stringResource(resource),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProfileSelector(
    profiles: List<ProviderProfile>,
    activeProfileId: String?,
    onSwitchProfile: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        profiles.forEach { profile ->
            FilterChip(
                selected = profile.id == activeProfileId,
                onClick = { onSwitchProfile(profile.id) },
                label = { Text("${profile.name} · ${profile.model}") },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsContent(
    state: TranslationUiState,
    uiPreferences: UiPreferences,
    onSaveProvider: (String, String, String, CharArray) -> Unit,
    onSwitchProfile: (String) -> Unit,
    onThemeChanged: (ThemePreference) -> Unit,
    onLocaleChanged: (String) -> Unit,
    onClose: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.action_close_settings))
                }
            }
        }
        item { Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium) }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePreference.entries.forEach { theme ->
                    FilterChip(
                        selected = uiPreferences.theme == theme,
                        onClick = { onThemeChanged(theme) },
                        label = { Text(theme.localizedName()) },
                    )
                }
            }
        }
        item { Text(stringResource(R.string.settings_ui_language), style = MaterialTheme.typography.titleMedium) }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UI_LOCALES.forEach { (tag, label) ->
                    FilterChip(
                        selected = uiPreferences.localeTag == tag,
                        onClick = { onLocaleChanged(tag) },
                        label = { Text(label) },
                    )
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
        }
        item {
            Text(
                stringResource(
                    R.string.diagnostics_summary,
                    state.coreAbiMajor.toInt(),
                    state.protocolVersion.toInt(),
                ),
            )
        }
        item { HorizontalDivider() }
        item {
            ProfileSelector(
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                onSwitchProfile = onSwitchProfile,
            )
        }
        item {
            ProviderForm(
                onSaveProvider = onSaveProvider,
                submitLabel = stringResource(R.string.action_save_provider),
            )
        }
    }
}

@Composable
private fun ProviderForm(
    onSaveProvider: (String, String, String, CharArray) -> Unit,
    submitLabel: String,
) {
    var providerName by remember { mutableStateOf("Local fake provider") }
    var endpoint by remember { mutableStateOf("http://127.0.0.1:40123/v1") }
    var model by remember { mutableStateOf("fake-translator") }
    var credential by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = providerName,
            onValueChange = { providerName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.provider_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_endpoint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_model)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = credential,
            onValueChange = { credential = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_api_key)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
            ),
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = {
                onSaveProvider(providerName, endpoint, model, credential.toCharArray())
                credential = ""
            },
            modifier = Modifier.testTag("save-provider"),
        ) {
            Text(submitLabel)
        }
    }
}

@Composable
private fun ThemePreference.localizedName(): String = when (this) {
    ThemePreference.System -> stringResource(R.string.theme_system)
    ThemePreference.Light -> stringResource(R.string.theme_light)
    ThemePreference.Dark -> stringResource(R.string.theme_dark)
}

private val TARGET_LOCALES = listOf(
    "zh-CN" to "简体中文",
    "en" to "English",
    "es" to "Español",
)

private val UI_LOCALES = listOf(
    "en" to "English",
    "zh-Hans" to "简体中文",
    "zh-Hant" to "繁體中文",
    "ja" to "日本語",
    "ko" to "한국어",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "pt-BR" to "Português (Brasil)",
    "ru" to "Русский",
    "ar" to "العربية",
    "hi" to "हिन्दी",
)
