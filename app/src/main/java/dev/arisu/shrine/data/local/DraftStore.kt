package dev.arisu.shrine.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Draft(
    val userId: String,
    val kind: String,
    val targetId: String,
    val targetTitle: String = "",
    val title: String = "",
    val body: String = "",
    val intro: String = "",
    val board: String = "",
    val tags: List<String> = emptyList(),
    val coverUrl: String? = null,
    val imageUrl: String? = null,
    val replyToId: String? = null,
    val replyToNickname: String? = null,
    val updatedAt: Long = 0L,
) {
    val id: String get() = "$userId|$kind|$targetId"
}

object DraftStore {

    const val COMMENT_POST = "comment_post"
    const val COMMENT_EMA = "comment_ema"
    const val POST = "post"
    const val EMA = "ema"

    private const val MAX_DRAFTS = 100
    private const val MAX_BODY = 40000

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    private val serializer = ListSerializer(Draft.serializer())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private lateinit var file: File

    @Volatile
    private var cache: List<Draft>? = null

    fun init(context: Context) {
        initAt(File(context.filesDir, "local"))
    }

    internal fun initAt(dir: File) {
        dir.mkdirs()
        file = File(dir, "drafts.json")
        cache = null
    }

    suspend fun list(userId: String): List<Draft> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readLocked()
                .filter { it.userId == userId }
                .sortedByDescending { it.updatedAt }
        }
    }

    suspend fun get(userId: String, kind: String, targetId: String): Draft? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readLocked().firstOrNull { it.userId == userId && it.kind == kind && it.targetId == targetId }
        }
    }

    suspend fun save(draft: Draft) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val clean = draft.copy(
                body = draft.body.take(MAX_BODY),
                updatedAt = if (draft.updatedAt == 0L) now else draft.updatedAt,
            )
            val rest = readLocked().filterNot { it.id == clean.id }
            writeLocked((rest + clean).sortedByDescending { it.updatedAt }.take(MAX_DRAFTS))
        }
    }

    suspend fun delete(userId: String, kind: String, targetId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = "$userId|$kind|$targetId"
            writeLocked(readLocked().filterNot { it.id == id })
        }
    }

    fun saveAsync(draft: Draft) {
        scope.launch { save(draft) }
    }

    fun deleteAsync(userId: String, kind: String, targetId: String) {
        scope.launch { delete(userId, kind, targetId) }
    }

    private fun readLocked(): List<Draft> {
        cache?.let { return it }
        val loaded = runCatching {
            if (!::file.isInitialized || !file.exists()) emptyList()
            else json.decodeFromString(serializer, file.readText())
        }.getOrElse { emptyList() }
        cache = loaded
        return loaded
    }

    private fun writeLocked(list: List<Draft>) {
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
