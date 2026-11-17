package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// internal：同包的 AccountVault（本地账号库）共用这一份 preferences 文件
internal val Context.dataStore by preferencesDataStore(name = "arisu_session")

/**
 * 会话持久化：token + 服务器地址。
 * 只要用户不主动退出登录，access/refresh token 会一直滑动续期，永不需要重新登录。
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val baseUrl = stringPreferencesKey("base_url")
        // 当前会话所属用户：弱网冷启动时要按它取出上次的资料（缓存的键带用户 id，不串号）
        val userId = stringPreferencesKey("user_id")
    }

    // 令牌读写都过 TokenCrypto：读侧解密（存量明文透传），写侧落盘即密文；
    // 解密失败按空串处理=未登录（fail-closed），宁可重新登录也不把坏密文当真
    val accessToken: Flow<String> = context.dataStore.data.map { TokenCrypto.decrypt(it[Keys.access] ?: "") ?: "" }
    val refreshToken: Flow<String> = context.dataStore.data.map { TokenCrypto.decrypt(it[Keys.refresh] ?: "") ?: "" }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.baseUrl] ?: "" }

    suspend fun currentBaseUrl(): String = baseUrl.first().ifEmpty { dev.arisu.shrine.data.remote.Api.DEFAULT_BASE_URL }
    suspend fun currentAccess(): String = accessToken.first()
    suspend fun currentRefresh(): String = refreshToken.first()
    suspend fun currentUserId(): String = context.dataStore.data.first()[Keys.userId] ?: ""

    suspend fun saveUserId(id: String) {
        context.dataStore.edit { it[Keys.userId] = id }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit {
            it[Keys.access] = TokenCrypto.encrypt(access)
            it[Keys.refresh] = TokenCrypto.encrypt(refresh)
        }
    }

    suspend fun saveAccess(access: String) {
        context.dataStore.edit { it[Keys.access] = TokenCrypto.encrypt(access) }
    }

    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.baseUrl] = url.trimEnd('/') }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.access)
            it.remove(Keys.refresh)
        }
    }
}
