package com.example.lilspeaker.features.privacy

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PrivacySettingsRepository(private val context: Context) {
    private val Context.dataStore by preferencesDataStore(name = "privacy")

    val telemetryEnabled: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[TELEMETRY_KEY] ?: false }

    val diagnosticsAllowed: Flow<Boolean> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[DIAGNOSTICS_KEY] ?: false }

    suspend fun setTelemetry(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TELEMETRY_KEY] = enabled
        }
    }

    suspend fun setDiagnostics(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DIAGNOSTICS_KEY] = enabled
        }
    }

    companion object {
        private val TELEMETRY_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("telemetry")
        private val DIAGNOSTICS_KEY: Preferences.Key<Boolean> = booleanPreferencesKey("diagnostics")
    }
}
