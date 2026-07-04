package com.dogtag.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dogtag.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "dogtag_settings")
private val BACKEND_URL_KEY = stringPreferencesKey("backend_base_url")

/**
 * The default in [BuildConfig] (10.0.2.2, the emulator's alias for the host
 * machine) is only useful in the emulator. On a real phone, point this at your
 * backend's LAN address/hostname, e.g. http://192.168.1.50:8000/
 */
class SettingsRepository(private val context: Context) {
    val backendBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BACKEND_URL_KEY] ?: BuildConfig.BACKEND_BASE_URL
    }

    suspend fun setBackendBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.dataStore.edit { it[BACKEND_URL_KEY] = normalized }
    }
}
