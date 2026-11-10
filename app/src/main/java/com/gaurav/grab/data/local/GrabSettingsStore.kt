package com.gaurav.grab.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaurav.grab.domain.model.GrabSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.grabDataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(name = "grab_settings")

@Singleton
class GrabSettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    val settings: Flow<GrabSettings> = ctx.grabDataStore.data.map { p ->
        GrabSettings(
            themeMode = p[K_THEME] ?: "DARK",
            turboThreads = p[K_THREADS] ?: 8,
            notificationsEnabled = p[K_NOTIF] ?: true,
            wifiOnly = p[K_WIFI] ?: false,
            autoWeeklyUpdate = p[K_AUTO] ?: true,
            adShield = p[K_SHIELD] ?: true
        )
    }
    suspend fun update(transform: GrabSettings.() -> GrabSettings) {
        val current = ctx.grabDataStore.data.let { p ->
            GrabSettings(p[K_THEME] ?: "DARK", p[K_THREADS] ?: 8, p[K_NOTIF] ?: true, p[K_WIFI] ?: false, p[K_AUTO] ?: true, p[K_SHIELD] ?: true)
        }
        val next = current.transform()
        ctx.grabDataStore.edit { p ->
            p[K_THEME] = next.themeMode; p[K_THREADS] = next.turboThreads
            p[K_NOTIF] = next.notificationsEnabled; p[K_WIFI] = next.wifiOnly
            p[K_AUTO] = next.autoWeeklyUpdate; p[K_SHIELD] = next.adShield
        }
    }
    private companion object {
        val K_THEME = stringPreferencesKey("theme"); val K_THREADS = intPreferencesKey("threads")
        val K_NOTIF = booleanPreferencesKey("notif"); val K_WIFI = booleanPreferencesKey("wifi")
        val K_AUTO = booleanPreferencesKey("auto"); val K_SHIELD = booleanPreferencesKey("shield")
    }
}
