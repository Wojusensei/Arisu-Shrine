package dev.arisu.shrine.data.repo

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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

    private val mirror = ConcurrentHashMap<String, String>()

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

    fun <T> peek(key: String, serializer: KSerializer<T>): T? =
        decodeMemo(sha(key), mirror[sha(key)], serializer)?.value

    fun <T> peekSnapshot(key: String, serializer: KSerializer<T>): Snapshot<T>? =
        decodeMemo(sha(key), mirror[sha(key)], serializer)

    private fun readFile(key: String): String? {
        if (!::dir.isInitialized) return null
        val f = fileFor(key)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    private fun <T> decodeMemo(name: String, raw: String?, serializer: KSerializer<T>): Snapshot<T>? {
        if (raw == null) return null
        memoGet(name, raw)?.let { m ->
            @Suppress("UNCHECKED_CAST")
            return Snapshot(m.cachedAt, m.value as T)
        }

        return runCatching {
            val e = json.decodeFromString(Entry.serializer(serializer), raw)
            memoPut(name, Memo(raw, e.value, e.cachedAt))
            Snapshot(e.cachedAt, e.value)
        }.getOrNull()
    }

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

    private const val MEMO_MAX = 64
}
