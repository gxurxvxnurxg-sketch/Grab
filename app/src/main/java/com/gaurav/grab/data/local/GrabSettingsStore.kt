package com.gaurav.grab.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gaurav.grab.domain.model.GrabSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.grabDataStore: DataStore<Preferences> by preferencesDataStore(name = "grab_settings")

@Singleton
class GrabSettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {

    val settings: Flow<GrabSettings> = ctx.grabDataStore.data.map { it.toGrabSettings() }

    suspend fun update(transform: GrabSettings.() -> GrabSettings) {
        val next = ctx.grabDataStore.data.first().toGrabSettings().transform()
        ctx.grabDataStore.edit { p ->
            p[K_THEME] = next.themeMode
            p[K_THREADS] = next.turboThreads
            p[K_NOTIF] = next.notificationsEnabled
            p[K_WIFI] = next.wifiOnly
            p[K_AUTO] = next.autoWeeklyUpdate
            p[K_SHIELD] = next.adShield
        }
    }

    private fun Preferences.toGrabSettings() = GrabSettings(
        themeMode = this[K_THEME] ?: "DARK",
        turboThreads = this[K_THREADS] ?: 8,
        notificationsEnabled = this[K_NOTIF] ?: true,
        wifiOnly = this[K_WIFI] ?: false,
        autoWeeklyUpdate = this[K_AUTO] ?: true,
        adShield = this[K_SHIELD] ?: true
    )

    private companion object {
        val K_THEME = stringPreferencesKey("theme")
        val K_THREADS = intPreferencesKey("threads")
        val K_NOTIF = booleanPreferencesKey("notif")
        val K_WIFI = booleanPreferencesKey("wifi")
        val K_AUTO = booleanPreferencesKey("auto")
        val K_SHIELD = booleanPreferencesKey("shield")
    }
}
