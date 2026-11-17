package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 阅读偏好：正文字号倍率。与主题共用一个 DataStore 文件。
 * 只存倍率，具体字号由 [dev.arisu.shrine.core.design.ReadingScale] 解释。
 */
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
