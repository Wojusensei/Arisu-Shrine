package dev.arisu.shrine.data.remote

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

object Api {

    const val PLACEHOLDER_BASE = "http://arisu-placeholder/"

    val DEFAULT_BASE_URL: String = dev.arisu.shrine.BuildConfig.BASE_URL

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile var currentBaseUrl: String = ""
    @Volatile var currentAccess: String = ""
    @Volatile var currentRefresh: String = ""

    @Volatile private var sessionGen: Long = 0L

    fun updateSession(baseUrl: String, access: String, refresh: String) {
        currentBaseUrl = baseUrl.trimEnd('/')
        currentAccess = access
        currentRefresh = refresh
        sessionGen++
    }

    private val baseRewrite = Interceptor { chain ->
        val request = chain.request()
        if (currentBaseUrl.isEmpty()) {
            chain.proceed(request)
        } else {
            val url = request.url
            val newUrl = (currentBaseUrl + url.encodedPath +
                (url.encodedQuery?.let { "?$it" } ?: ""))
            chain.proceed(request.newBuilder().url(newUrl).build())
        }
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val withToken = if (currentAccess.isNotEmpty()) {
            request.newBuilder().header("Authorization", "Bearer $currentAccess").build()
        } else request
        chain.proceed(withToken)
    }

    private val deviceInterceptor = Interceptor { chain ->
        val model = android.os.Build.MODEL ?: ""
        chain.proceed(
            if (model.isEmpty()) chain.request()
            else chain.request().newBuilder().header("X-Device-Name", model.take(60)).build()
        )
    }

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    private val authenticator = okhttp3.Authenticator { _, response ->
        var depth = 0
        var prior = response.priorResponse
        while (prior != null) {
            depth++
            prior = prior.priorResponse
        }
        if (depth >= 2) return@Authenticator null
        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val newAccess = runBlocking { refreshAfter401(staleToken) } ?: return@Authenticator null
        response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private suspend fun refreshAfter401(staleToken: String?): String? = refreshMutex.withLock {
        if (staleToken != null && currentAccess != staleToken) {
            currentAccess
        } else {
            refreshAccessLocked()?.accessToken
        }
    }

    var onSessionRefreshed: (suspend (oldRefresh: String, access: String, refresh: String) -> Unit)? = null

    var onRotationAbandoned: (suspend (oldRefresh: String, access: String, refresh: String) -> Unit)? = null

    var onSessionDead: (() -> Unit)? = null

    suspend fun refreshAccess(): AuthResultDto? = refreshMutex.withLock {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { refreshAccessLocked() }
    }

    private suspend fun refreshAccessLocked(): AuthResultDto? {
        val refresh = currentRefresh
        if (refresh.isEmpty() || currentBaseUrl.isEmpty()) return null
        val gen = sessionGen
        return try {
            val body = json.encodeToString(
                RefreshReq.serializer(), RefreshReq(refreshToken = refresh)
            ).toRequestBody()
            val request = Request.Builder()
                .url(currentBaseUrl + "/api/auth/refresh")
                .post(body)
                .apply {
                    val model = android.os.Build.MODEL ?: ""
                    if (model.isNotEmpty()) header("X-Device-Name", model.take(60))
                }
                .build()
            refreshClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {

                    if (resp.code == 401 || resp.code == 403) onSessionDead?.invoke()
                    return null
                }
                val result = json.decodeFromString(AuthResultDto.serializer(), resp.body!!.string())

                if (sessionGen != gen) {

                    onRotationAbandoned?.invoke(refresh, result.accessToken, result.refreshToken)
                    return null
                }
                currentAccess = result.accessToken
                if (result.refreshToken.isNotEmpty()) {
                    currentRefresh = result.refreshToken
                    onSessionRefreshed?.invoke(refresh, result.accessToken, result.refreshToken)
                }
                result
            }
        } catch (e: kotlinx.coroutines.CancellationException) {

            throw e
        } catch (_: Exception) {
            null
        }
    }

    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun String.toRequestBody() =
        okhttp3.RequestBody.create("application/json".toMediaType(), this)

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(baseRewrite)
            .addInterceptor(deviceInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)

            .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    val uploadService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(
                client.newBuilder()
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

class ApiError(message: String) : Exception(message)

fun Throwable.toApiMessage(): String = when (this) {
    is ApiError -> message ?: "请求失败"
    is HttpException -> {
        val msg = try {
            val body = response()?.errorBody()?.string()
            body?.let { Api.json.decodeFromString(ErrorRes.serializer(), it).error }
        } catch (_: Exception) {
            null
        }
        when (code()) {
            401 -> "登录已过期，请重新登录"
            else -> msg ?: "网络请求失败（HTTP ${code()}）"
        }
    }

    is java.io.IOException ->
        "连接服务器失败，请检查网络或稍后再试"
    else -> {
        val detail = if (message.isNullOrBlank()) this::class.java.simpleName else message
        "网络请求失败：$detail"
    }
}

suspend fun <T> apiCall(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: HttpException) {
        val msg = try {
            val body = e.response()?.errorBody()?.string()
            body?.let { Api.json.decodeFromString(ErrorRes.serializer(), it).error }
        } catch (_: Exception) {
            null
        }
        throw ApiError(msg ?: "网络请求失败（HTTP ${e.code()}）")
    } catch (e: IOException) {

        val retryable = e is java.net.SocketException ||
            e is java.net.ConnectException ||
            e is java.net.UnknownHostException
        if (!retryable) throw e
        return block()
    }
}
