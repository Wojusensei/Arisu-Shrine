package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 主题偏好：黑夜模式的手动覆盖。
 *
 * null = 未手动设置，默认亮色（MainActivity 注入 NIGHT_NO，系统深浅不参与）；
 * 非 null = 用户在设置里拨过「黑夜模式」开关，此后以手动值为准，
 * 系统切换不再生效。与令牌共用同一份 DataStore 文件。
 */
object ThemePrefs {

    private val NIGHT = booleanPreferencesKey("theme_night")

    /** 手动覆盖值；null 表示跟随系统。 */
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
