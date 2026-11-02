package dev.arisu.shrine.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class HistoryEntry(
    val userId: String,
    val kind: String,
    val targetId: String,
    val title: String = "",
    val subtitle: String = "",
    val visitedAt: Long = 0L,
    val lastCommentId: String? = null,
    val lastIndex: Int = -1,
    val lastOffset: Int = 0,
)

object HistoryStore {

    const val POST = "post"
    const val EMA = "ema"

    private const val MAX_ENTRIES = 200

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val serializer = ListSerializer(HistoryEntry.serializer())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private lateinit var file: File

    @Volatile
    private var cache: List<HistoryEntry>? = null

    fun init(context: Context) {
        initAt(File(context.filesDir, "local"))
    }

    internal fun initAt(dir: File) {
        dir.mkdirs()
        file = File(dir, "history.json")
        cache = null
    }

    suspend fun list(userId: String): List<HistoryEntry> = mutex.withLock {
        readLocked()
            .filter { it.userId == userId }
            .sortedByDescending { it.visitedAt }
    }

    suspend fun get(userId: String, kind: String, targetId: String): HistoryEntry? = mutex.withLock {
        readLocked().firstOrNull { it.userId == userId && it.kind == kind && it.targetId == targetId }
    }

    suspend fun visit(
        userId: String,
        kind: String,
        targetId: String,
        title: String,
        subtitle: String,
    ) = mutex.withLock {
        val old = readLocked().firstOrNull {
            it.userId == userId && it.kind == kind && it.targetId == targetId
        }
        upsertLocked(
            HistoryEntry(
                userId = userId,
                kind = kind,
                targetId = targetId,
                title = title,
                subtitle = subtitle,
                visitedAt = System.currentTimeMillis(),
                lastCommentId = old?.lastCommentId,
                lastIndex = old?.lastIndex ?: -1,
                lastOffset = old?.lastOffset ?: 0,
            ),
        )
    }

    suspend fun savePosition(
        userId: String,
        kind: String,
        targetId: String,
        commentId: String,
        index: Int,
        offset: Int,
    ) = mutex.withLock {
        val old = readLocked().firstOrNull {
            it.userId == userId && it.kind == kind && it.targetId == targetId
        } ?: return@withLock
        upsertLocked(
            old.copy(
                lastCommentId = commentId,
                lastIndex = index,
                lastOffset = offset,
                visitedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clear(userId: String) = mutex.withLock {
        writeLocked(readLocked().filterNot { it.userId == userId })
    }

    fun visitAsync(userId: String, kind: String, targetId: String, title: String, subtitle: String) {
        scope.launch { visit(userId, kind, targetId, title, subtitle) }
    }

    fun savePositionAsync(
        userId: String,
        kind: String,
        targetId: String,
        commentId: String,
        index: Int,
        offset: Int,
    ) {
        scope.launch { savePosition(userId, kind, targetId, commentId, index, offset) }
    }

    fun clearAsync(userId: String) {
        scope.launch { clear(userId) }
    }

    private fun upsertLocked(entry: HistoryEntry) {
        val rest = readLocked().filterNot {
            it.userId == entry.userId && it.kind == entry.kind && it.targetId == entry.targetId
        }
        writeLocked((rest + entry).sortedByDescending { it.visitedAt }.take(MAX_ENTRIES))
    }

    private fun readLocked(): List<HistoryEntry> {
        cache?.let { return it }
        val loaded = runCatching {
            if (!::file.isInitialized || !file.exists()) emptyList()
            else json.decodeFromString(serializer, file.readText())
        }.getOrElse { emptyList() }
        cache = loaded
        return loaded
    }

    private fun writeLocked(list: List<HistoryEntry>) {
        if (!::file.isInitialized) return
        val text = json.encodeToString(serializer, list)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
        cache = list
    }
}
