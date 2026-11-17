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

/**
 * 本地账号档案：多账号快速切换（QQ 式账号管理）。
 * 只存续期令牌、不存密码——切换=把对应令牌装回会话槽位；令牌被吊销或
 * 30 天滑动过期后才需要重输一次密码，重新登录成功会覆盖档案。
 * 每条档案自带 baseUrl：不同服务器（模拟器/局域网/未来的域名）的账号可以并存，
 * 切换账号连服务器地址一起换。
 */
@Serializable
data class VaultAccount(
    /** MeDto.id，档案主键 */
    val userId: Long,
    /** 展示编号，如 User_000001 */
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
                // 读侧解密：存量明文透传；解密失败的档案令牌记空=不可用（fail-closed），重登即恢复
                .map { account ->
                    account.copy(
                        access = TokenCrypto.decrypt(account.access) ?: "",
                        refresh = TokenCrypto.decrypt(account.refresh) ?: "",
                    )
                }
        } ?: emptyList()
    }

    suspend fun snapshot(): List<VaultAccount> = accounts.first()

    // upsert/remove 都是「读快照→改→整表写回」，两笔并发交错时后写者会覆盖掉
    // 先写的账号（比如改密落档与用户同时移除另一账号），互斥兜住丢档案
    private val mutex = Mutex()

    /**
     * 保存档案：同账号覆盖并置顶（最近使用的在前）。
     * 不设数量上限——登录第 N+1 个账号时静默拒存会造成「明明登录了却切不出来」的暗坑
     */
    suspend fun upsert(account: VaultAccount) = mutex.withLock {
        val list = snapshot().toMutableList()
        list.removeAll { it.userId == account.userId }
        list.add(0, account)
        persist(list)
    }

    suspend fun remove(userId: Long) = mutex.withLock {
        persist(snapshot().filterNot { it.userId == userId })
    }

    /**
     * 令牌轮换后同步档案：按「刚被服务器换掉的那一枚 refresh」定位账号。
     * 服务端是旋转式签发，旧令牌一用即废——档案里如果一直留着旧的那枚，
     * 登录满一天后点它免密切换必然 401 被踢回登录页（2026-10-17 事故）。
     * 找不到对应档案就什么都不做（比如令牌属于已移除的账号）。
     */
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
        // 落盘前令牌转密文；昵称/头像/地址等非敏感字段保持明文（换服调试要看得见）
        context.dataStore.edit {
            it[key] = Api.json.encodeToString(
                list.map { a ->
                    a.copy(access = TokenCrypto.encrypt(a.access), refresh = TokenCrypto.encrypt(a.refresh))
                },
            )
        }
    }
}
