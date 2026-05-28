package com.test.demospatialaudio.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "atoms_prefs")

class AppPreferences(private val context: Context) {

    private val onboardingDoneKey = booleanPreferencesKey("onboarding_done")
    private val lastPresetIdKey = stringPreferencesKey("last_preset_id")
    private val themeIndexKey = intPreferencesKey("theme_index")

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map {
        it[onboardingDoneKey] ?: false
    }

    val lastPresetId: Flow<String?> = context.dataStore.data.map { it[lastPresetIdKey] }

    val themeIndex: Flow<Int> = context.dataStore.data.map { it[themeIndexKey] ?: 0 }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[onboardingDoneKey] = done }
    }

    suspend fun setLastPresetId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(lastPresetIdKey)
            else it[lastPresetIdKey] = id
        }
    }

    suspend fun setThemeIndex(index: Int) {
        context.dataStore.edit { it[themeIndexKey] = index }
    }
}
