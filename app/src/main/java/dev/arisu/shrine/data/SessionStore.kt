package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(name = "arisu_session")

class SessionStore(private val context: Context) {

    private object Keys {
        val access = stringPreferencesKey("access_token")
        val refresh = stringPreferencesKey("refresh_token")
        val baseUrl = stringPreferencesKey("base_url")
    }

    val accessToken: Flow<String> = context.dataStore.data.map { TokenCrypto.decrypt(it[Keys.access] ?: "") ?: "" }
    val refreshToken: Flow<String> = context.dataStore.data.map { TokenCrypto.decrypt(it[Keys.refresh] ?: "") ?: "" }
    val baseUrl: Flow<String> = context.dataStore.data.map { it[Keys.baseUrl] ?: "" }

    suspend fun currentBaseUrl(): String = baseUrl.first().ifEmpty { dev.arisu.shrine.data.remote.Api.DEFAULT_BASE_URL }
    suspend fun currentAccess(): String = accessToken.first()
    suspend fun currentRefresh(): String = refreshToken.first()

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
