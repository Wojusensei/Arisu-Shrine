package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object ThemePrefs {

    private val NIGHT = booleanPreferencesKey("theme_night")

    fun nightOverride(context: Context): Flow<Boolean?> =
        context.dataStore.data.map { it[NIGHT] }

    suspend fun currentNightOverride(context: Context): Boolean? =
        context.dataStore.data.first()[NIGHT]

    suspend fun setNight(context: Context, value: Boolean) {
        context.dataStore.edit { it[NIGHT] = value }
    }

    suspend fun clearNight(context: Context) {
        context.dataStore.edit { it.remove(NIGHT) }
    }
}
