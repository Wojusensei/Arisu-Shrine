package dev.arisu.shrine.data.repo

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 只读数据的磁盘缓存：网络成功时写入，进入页面时秒出上一次的数据。
 *
 * 存储为 cacheDir/shrine-cache/ 下的独立 JSON 文件（键做散列），
 * 系统可在磁盘紧张时自动回收，不占用户空间；同时在进程内留一份文本镜像，
 * 预热后 [peek] 可同步直取（首帧用），不必等磁盘 IO。
 */
object CacheStore {

    @Serializable
    private data class Entry<T>(val cachedAt: Long, val value: T)

    data class Snapshot<T>(val cachedAt: Long, val value: T)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var dir: File

    /** 散列文件名 → 落盘原文；启动预热后读写都双写，界面首帧同步可取 */
    private val mirror = ConcurrentHashMap<String, String>()

    /**
     * 散列文件名 → 已经解析好的对象。
     *
     * peek 是在组合期（主线程）调的，此前每次都把镜像里的 JSON 重新解一遍——
     * 我的客房一帧要解 4 份、茶话首页要解 20 篇正文。同一份缓存被反复读时
     * 解析一次就够：put 时顺手记下刚拿到手的对象（零成本），从磁盘预热来的
     * 由首次 peek 解析并记住。原文变了（重新落盘）以原文为准，不会回读到旧对象。
     *
     * 上限必须有：镜像最多装 400 份缓存，若把每一份的解析结果都常驻下来，
     * 用久了堆上会同时躺着原文与对象图两份（比改动前多一份）。屏幕上真正常读
     * 的就是当前这几份，[MEMO_MAX] 条按最近使用淘汰足够覆盖，内存有界。
     */
    private class Memo(val raw: String, val value: Any?, val cachedAt: Long)

    private val memoLock = Any()
    private val memo = object : LinkedHashMap<String, Memo>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Memo>?): Boolean =
            size > MEMO_MAX
    }

    private fun memoGet(name: String, raw: String): Memo? = synchronized(memoLock) {
        memo[name]?.takeIf { it.raw == raw }
    }

    private fun memoPut(name: String, m: Memo) = synchronized(memoLock) { memo[name] = m }

    private fun memoRemove(name: String) = synchronized(memoLock) { memo.remove(name) }

    private fun memoClear() = synchronized(memoLock) { memo.clear() }

    fun init(context: Context) {
        dir = File(context.cacheDir, "shrine-cache").apply { mkdirs() }
    }

    /**
     * 启动时调用：后台把磁盘缓存读进内存镜像。
     * 不与首帧抢时间——预热完之前 [peek] 返回 null，界面行为退化成原来的空态。
     */
    fun preload() {
        if (!::dir.isInitialized) return
        Thread {
            dir.listFiles()?.forEach { f ->
                if (!f.name.endsWith(".json")) return@forEach
                runCatching { f.readText() }
                    .getOrNull()
                    ?.let { mirror[f.nameWithoutExtension] = it }
            }
        }.start()
    }

    fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        if (!::dir.isInitialized) return
        runCatching {
            val payload = json.encodeToString(
                Entry.serializer(serializer), Entry(cachedAt = now(), value = value)
            )
            val name = sha(key)
            mirror[name] = payload
            memoPut(name, Memo(payload, value, now()))
            fileFor(key).writeText(payload)
            pruneIfNeeded()
        }
    }

    fun <T> get(key: String, serializer: KSerializer<T>): Snapshot<T>? =
        decodeMemo(sha(key), mirror[sha(key)] ?: readFile(key), serializer)

    /** 同步直取（镜像为准，无 IO、重复读不再重复解析）：首帧预置数据用，取不到就返回 null */
    fun <T> peek(key: String, serializer: KSerializer<T>): T? =
        decodeMemo(sha(key), mirror[sha(key)], serializer)?.value

    fun <T> peekSnapshot(key: String, serializer: KSerializer<T>): Snapshot<T>? =
        decodeMemo(sha(key), mirror[sha(key)], serializer)

    private fun readFile(key: String): String? {
        if (!::dir.isInitialized) return null
        val f = fileFor(key)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    /** 解析并记忆；命中且原文未变时直接返回上次的结果 */
    private fun <T> decodeMemo(name: String, raw: String?, serializer: KSerializer<T>): Snapshot<T>? {
        if (raw == null) return null
        memoGet(name, raw)?.let { m ->
            @Suppress("UNCHECKED_CAST")
            return Snapshot(m.cachedAt, m.value as T)
        }
        // 解析放在锁外：主线程的 peek 与 IO 线程的 put 不会互相等一次大解码，
        // 重复解析同一份只多花一次 CPU（结果相同，后写覆盖）
        return runCatching {
            val e = json.decodeFromString(Entry.serializer(serializer), raw)
            memoPut(name, Memo(raw, e.value, e.cachedAt))
            Snapshot(e.cachedAt, e.value)
        }.getOrNull()
    }

    /** 全量清空：登出 / 注销 / 更换服务器时调用，磁盘与镜像都不留下能回读的旧会话数据 */
    fun clear() {
        mirror.clear()
        memoClear()
        if (!::dir.isInitialized) return
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun fileFor(key: String): File = File(dir, sha(key) + ".json")

    private fun pruneIfNeeded() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach {
                mirror.remove(it.nameWithoutExtension)
                memoRemove(it.nameWithoutExtension)
                it.delete()
            }
    }

    private fun sha(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun now(): Long = System.currentTimeMillis() / 1000

    private const val MAX_FILES = 400

    /** 解析记忆上限：够覆盖当前屏幕来回读的那几份，又不至于把整张缓存图都常驻 */
    private const val MEMO_MAX = 64
}
