package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object ReadingPrefs {

    const val DEFAULT = 1f

    private val SCALE = floatPreferencesKey("reading_scale")

    fun scale(context: Context): Flow<Float> =
        context.dataStore.data.map { it[SCALE] ?: DEFAULT }

    suspend fun currentScale(context: Context): Float =
        context.dataStore.data.first()[SCALE] ?: DEFAULT

    suspend fun setScale(context: Context, value: Float) {
        context.dataStore.edit { it[SCALE] = value }
    }
}
