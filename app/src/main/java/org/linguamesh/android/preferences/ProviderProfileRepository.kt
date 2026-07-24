package org.linguamesh.android.preferences

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.linguamesh.android.core.ProviderProfile

data class PersistedProviderProfiles(
    val profiles: List<ProviderProfile>,
    val activeProfileId: String?,
)

interface ProviderProfileRepository {
    val state: Flow<PersistedProviderProfiles>

    suspend fun upsert(profile: ProviderProfile)

    suspend fun setActiveProfile(profileId: String)

    companion object {
        fun inMemory(): ProviderProfileRepository = InMemoryProviderProfileRepository()
    }
}

private val Context.providerProfilesDataStore by preferencesDataStore(name = "provider_profiles")

class DataStoreProviderProfileRepository(private val context: Context) : ProviderProfileRepository {
    override val state: Flow<PersistedProviderProfiles> = context.providerProfilesDataStore.data
        .map { values ->
            val profiles = decodeProfiles(values[PROFILES_KEY].orEmpty())
            val activeId = values[ACTIVE_PROFILE_KEY]
                ?.takeIf { id -> profiles.any { it.id == id } }
            PersistedProviderProfiles(profiles, activeId)
        }
        .catch {
            emit(PersistedProviderProfiles(emptyList(), null))
        }

    override suspend fun upsert(profile: ProviderProfile) {
        profile.requireBounded()
        context.providerProfilesDataStore.edit { values ->
            val current = decodeProfiles(values[PROFILES_KEY].orEmpty())
            val next = (current.filterNot { it.id == profile.id } + profile)
                .takeLast(MAX_PROFILES)
            values[PROFILES_KEY] = encodeProfiles(next)
            values[ACTIVE_PROFILE_KEY] = profile.id
        }
    }

    override suspend fun setActiveProfile(profileId: String) {
        context.providerProfilesDataStore.edit { values ->
            val profiles = decodeProfiles(values[PROFILES_KEY].orEmpty())
            if (profiles.any { it.id == profileId }) {
                values[ACTIVE_PROFILE_KEY] = profileId
            }
        }
    }
}

class InMemoryProviderProfileRepository : ProviderProfileRepository {
    private val mutableState = MutableStateFlow(PersistedProviderProfiles(emptyList(), null))
    override val state: Flow<PersistedProviderProfiles> = mutableState

    override suspend fun upsert(profile: ProviderProfile) {
        profile.requireBounded()
        val next = (mutableState.value.profiles.filterNot { it.id == profile.id } + profile)
            .takeLast(MAX_PROFILES)
        mutableState.value = PersistedProviderProfiles(next, profile.id)
    }

    override suspend fun setActiveProfile(profileId: String) {
        if (mutableState.value.profiles.any { it.id == profileId }) {
            mutableState.value = mutableState.value.copy(activeProfileId = profileId)
        }
    }
}

private fun encodeProfiles(profiles: List<ProviderProfile>): String = profiles.joinToString("\n") { profile ->
    listOf(profile.id, profile.name, profile.endpoint, profile.model, profile.secretRef.orEmpty())
        .joinToString("|") { encodeField(it) }
}

private fun ProviderProfile.requireBounded() {
    require(id.isNotBlank())
    require(name.isNotBlank() && name.length <= MAX_FIELD_LENGTH)
    require(endpoint.isNotBlank() && endpoint.length <= MAX_FIELD_LENGTH)
    require(model.isNotBlank() && model.length <= MAX_FIELD_LENGTH)
    require(secretRef == null || (secretRef.isNotBlank() && secretRef.length <= MAX_FIELD_LENGTH))
}

private fun decodeProfiles(serialized: String): List<ProviderProfile> = serialized
    .lineSequence()
    .mapNotNull { line ->
        val fields = line.split('|')
        if (fields.size != PROFILE_FIELD_COUNT) return@mapNotNull null
        val decoded = fields.mapNotNull(::decodeField)
        if (decoded.size != PROFILE_FIELD_COUNT) return@mapNotNull null
        val (id, name, endpoint, model, secretRef) = decoded
        if (id.isBlank() || name.isBlank() || endpoint.isBlank() || model.isBlank()) {
            return@mapNotNull null
        }
        ProviderProfile(id, name, endpoint, model, secretRef.ifEmpty { null })
    }
    .distinctBy { it.id }
    .take(MAX_PROFILES)
    .toList()

private fun encodeField(value: String): String = Base64.encodeToString(
    value.toByteArray(StandardCharsets.UTF_8),
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
)

private fun decodeField(value: String): String? = runCatching {
    Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        .toString(StandardCharsets.UTF_8)
        .takeIf { it.length <= MAX_FIELD_LENGTH }
}.getOrNull()

private const val MAX_PROFILES = 32
private const val MAX_FIELD_LENGTH = 2048
private const val PROFILE_FIELD_COUNT = 5
private val PROFILES_KEY = stringPreferencesKey("profiles")
private val ACTIVE_PROFILE_KEY = stringPreferencesKey("active_profile_id")
