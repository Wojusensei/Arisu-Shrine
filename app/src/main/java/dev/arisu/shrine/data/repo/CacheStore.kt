package dev.arisu.shrine.data.repo

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

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

    fun init(context: Context) {
        dir = File(context.cacheDir, "shrine-cache").apply { mkdirs() }
    }

    fun <T> put(key: String, serializer: KSerializer<T>, value: T) {
        if (!::dir.isInitialized) return
        runCatching {
            val payload = json.encodeToString(
                Entry.serializer(serializer), Entry(cachedAt = now(), value = value)
            )
            fileFor(key).writeText(payload)
            pruneIfNeeded()
        }
    }

    fun <T> get(key: String, serializer: KSerializer<T>): Snapshot<T>? {
        if (!::dir.isInitialized) return null
        return runCatching {
            val f = fileFor(key)
            if (!f.exists()) return null
            val e = json.decodeFromString(Entry.serializer(serializer), f.readText())
            Snapshot(e.cachedAt, e.value)
        }.getOrNull()
    }

    fun clear() {
        if (!::dir.isInitialized) return
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun fileFor(key: String): File = File(dir, sha(key) + ".json")

    private fun pruneIfNeeded() {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_FILES)
            .forEach { it.delete() }
    }

    private fun sha(key: String): String =
        MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun now(): Long = System.currentTimeMillis() / 1000

    private const val MAX_FILES = 400
}
