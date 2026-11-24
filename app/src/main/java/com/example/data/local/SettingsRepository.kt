package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AiModelOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.tomeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tomescanner_settings"
)

data class AppSettings(
    val selectedModel: AiModelOption = AiModelOption.FAST_FLASH,
    val customModelId: String = "gemini-4-argon",
    val customApiKey: String = "",
    val useCustomApiKey: Boolean = false,
    val groups: List<String> = SettingsRepository.DEFAULT_GROUPS,
    val defaultGroup: String = "Home Library"
) {
    val isUsingPaidKey: Boolean
        get() = useCustomApiKey && customApiKey.isNotBlank()

    val effectiveModelId: String
        get() = selectedModel.resolveModelId(customModelId)
}

class SettingsRepository(private val context: Context) {

    companion object {
        val DEFAULT_GROUPS = listOf(
            "Home Library",
            "Fiction",
            "Non-Fiction",
            "Reference",
            "Wishlist"
        )
        private val KEY_MODEL = stringPreferencesKey("selected_model")
        private val KEY_CUSTOM_MODEL_ID = stringPreferencesKey("custom_model_id")
        private val KEY_CUSTOM_API_KEY = stringPreferencesKey("custom_api_key")
        private val KEY_USE_CUSTOM_API_KEY = booleanPreferencesKey("use_custom_api_key")
        private val KEY_GROUPS = stringPreferencesKey("shelf_groups_json")
        private val KEY_DEFAULT_GROUP = stringPreferencesKey("default_shelf_group")
    }

    val settings: Flow<AppSettings> = context.tomeSettingsDataStore.data.map { prefs ->
        val model = AiModelOption.fromModelId(prefs[KEY_MODEL])
        val customModelId = prefs[KEY_CUSTOM_MODEL_ID]?.trim().orEmpty().ifEmpty { "gemini-4-argon" }
        val customApiKey = prefs[KEY_CUSTOM_API_KEY]?.trim().orEmpty()
        val useCustomApiKey = prefs[KEY_USE_CUSTOM_API_KEY] ?: false
        val groups = parseGroups(prefs[KEY_GROUPS])
        val savedDefault = prefs[KEY_DEFAULT_GROUP]
        val defaultGroup = if (savedDefault != null && groups.contains(savedDefault)) {
            savedDefault
        } else {
            groups.first()
        }
        AppSettings(
            selectedModel = model,
            customModelId = customModelId,
            customApiKey = customApiKey,
            useCustomApiKey = useCustomApiKey && customApiKey.isNotBlank(),
            groups = groups,
            defaultGroup = defaultGroup
        )
    }

    suspend fun setModel(model: AiModelOption) {
        context.tomeSettingsDataStore.edit { prefs ->
            prefs[KEY_MODEL] = model.modelId
        }
    }

    suspend fun setCustomModelId(modelId: String) {
        val trimmed = modelId.trim()
        context.tomeSettingsDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_MODEL_ID] = trimmed
        }
    }

    suspend fun saveCustomApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        context.tomeSettingsDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_API_KEY] = trimmed
            prefs[KEY_USE_CUSTOM_API_KEY] = trimmed.isNotEmpty()
        }
    }

    suspend fun clearCustomApiKey() {
        context.tomeSettingsDataStore.edit { prefs ->
            prefs.remove(KEY_CUSTOM_API_KEY)
            prefs[KEY_USE_CUSTOM_API_KEY] = false
        }
    }

    suspend fun setUseCustomApiKey(enabled: Boolean) {
        context.tomeSettingsDataStore.edit { prefs ->
            val hasKey = !prefs[KEY_CUSTOM_API_KEY].isNullOrBlank()
            prefs[KEY_USE_CUSTOM_API_KEY] = enabled && hasKey
        }
    }

    suspend fun setDefaultGroup(group: String) {
        val trimmed = group.trim()
        if (trimmed.isEmpty()) return
        context.tomeSettingsDataStore.edit { prefs ->
            val current = parseGroups(prefs[KEY_GROUPS]).toMutableList()
            if (!current.contains(trimmed)) {
                current.add(trimmed)
                prefs[KEY_GROUPS] = encodeGroups(current)
            }
            prefs[KEY_DEFAULT_GROUP] = trimmed
        }
    }

    suspend fun addGroup(groupName: String) {
        val trimmed = groupName.trim()
        if (trimmed.isEmpty()) return
        context.tomeSettingsDataStore.edit { prefs ->
            val current = parseGroups(prefs[KEY_GROUPS]).toMutableList()
            if (current.none { it.equals(trimmed, ignoreCase = true) }) {
                current.add(trimmed)
                prefs[KEY_GROUPS] = encodeGroups(current)
            }
        }
    }

    suspend fun mergeGroups(incoming: List<String>) {
        if (incoming.isEmpty()) return
        context.tomeSettingsDataStore.edit { prefs ->
            val current = parseGroups(prefs[KEY_GROUPS]).toMutableList()
            incoming.map { it.trim() }.filter { it.isNotEmpty() }.forEach { candidate ->
                if (current.none { it.equals(candidate, ignoreCase = true) }) {
                    current.add(candidate)
                }
            }
            prefs[KEY_GROUPS] = encodeGroups(current)
        }
    }

    suspend fun removeGroup(groupName: String) {
        context.tomeSettingsDataStore.edit { prefs ->
            val current = parseGroups(prefs[KEY_GROUPS]).filterNot { it == groupName }
            val updated = current.ifEmpty { DEFAULT_GROUPS }
            prefs[KEY_GROUPS] = encodeGroups(updated)
            val def = prefs[KEY_DEFAULT_GROUP]
            if (def == groupName || def !in updated) {
                prefs[KEY_DEFAULT_GROUP] = updated.first()
            }
        }
    }

    private fun parseGroups(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return DEFAULT_GROUPS
        return try {
            val arr = JSONArray(rawJson)
            val result = buildList<String> {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i).trim()
                    if (v.isNotEmpty() && none { it.equals(v, ignoreCase = true) }) {
                        add(v)
                    }
                }
            }
            result.ifEmpty { DEFAULT_GROUPS }
        } catch (_: Exception) {
            DEFAULT_GROUPS
        }
    }

    private fun encodeGroups(groups: List<String>): String {
        val arr = JSONArray()
        groups.forEach { arr.put(it) }
        return arr.toString()
    }
}
