package dev.arisu.shrine.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.arisu.shrine.data.remote.Api
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class VaultAccount(

    val userId: Long,

    val no: String,
    val email: String,
    val nickname: String,
    val avatarUrl: String,
    val baseUrl: String,
    val access: String,
    val refresh: String,
    val savedAt: Long,
)

class AccountVault(private val context: Context) {

    private val key = stringPreferencesKey("vault_accounts")

    val accounts: Flow<List<VaultAccount>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { Api.json.decodeFromString<List<VaultAccount>>(raw) }.getOrDefault(emptyList())

                .map { account ->
                    account.copy(
                        access = TokenCrypto.decrypt(account.access) ?: "",
                        refresh = TokenCrypto.decrypt(account.refresh) ?: "",
                    )
                }
        } ?: emptyList()
    }

    suspend fun snapshot(): List<VaultAccount> = accounts.first()

    private val mutex = Mutex()

    suspend fun upsert(account: VaultAccount) = mutex.withLock {
        val list = snapshot().toMutableList()
        list.removeAll { it.userId == account.userId }
        list.add(0, account)
        persist(list)
    }

    suspend fun remove(userId: Long) = mutex.withLock {
        persist(snapshot().filterNot { it.userId == userId })
    }

    suspend fun refreshTokens(oldRefresh: String, access: String, refresh: String) = mutex.withLock {
        if (oldRefresh.isEmpty() || refresh.isEmpty()) return@withLock
        val list = snapshot()
        val i = list.indexOfFirst { it.refresh == oldRefresh }
        if (i < 0) return@withLock
        val next = list.toMutableList()
        next[i] = next[i].copy(access = access, refresh = refresh, savedAt = System.currentTimeMillis())
        persist(next)
    }

    private suspend fun persist(list: List<VaultAccount>) {

        context.dataStore.edit {
            it[key] = Api.json.encodeToString(
                list.map { a ->
                    a.copy(access = TokenCrypto.encrypt(a.access), refresh = TokenCrypto.encrypt(a.refresh))
                },
            )
        }
    }
}
