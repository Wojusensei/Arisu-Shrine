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

/**
 * 网络栈：动态 BaseUrl + 自动携带 token + 401 自动刷新。
 * Retrofit 要求固定 baseUrl，这里用占位符 + 拦截器改写 host，
 * 服务器地址就随时可换（本机 IP / 以后的域名）。
 */
object Api {

    const val PLACEHOLDER_BASE = "http://arisu-placeholder/"

    /**
     * 出厂默认服务器地址：源码即配置，用户不选服（正式包没有地址配置入口）。
     * arisushrine.com → Cloudflare → 香港机 156.225.29.44，Caddy 反代 HTTPS。
     * 本机联调时用 gradle 参数 -ParisuBaseUrl=... 覆盖（见 build.gradle.kts），
     * 不必改动这里的生产地址。
     */
    val DEFAULT_BASE_URL: String = dev.arisu.shrine.BuildConfig.BASE_URL

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile var currentBaseUrl: String = ""
    @Volatile var currentAccess: String = ""
    @Volatile var currentRefresh: String = ""

    /** 会话代数：装会话/切号/登出都自增。在途的续签结果靠它识别会话已易主 */
    @Volatile private var sessionGen: Long = 0L

    /** 由 Shrine 在会话变化时调用 */
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

    // 登录/续签签发 refresh token 时服务端记录设备名（设置页「登录设备」展示）。
    // 轮换是删旧行插新行，所以每次请求都要带，漏一次设备名就变「未知设备」
    private val deviceInterceptor = Interceptor { chain ->
        val model = android.os.Build.MODEL ?: ""
        chain.proceed(
            if (model.isEmpty()) chain.request()
            else chain.request().newBuilder().header("X-Device-Name", model.take(60)).build()
        )
    }

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * 401 自动续签。要点有四：
     * · 单飞：并发 401 只放第一个去刷新，其余等锁后双检——token 已被别人刷新就直接复用；
     * · 环路保险：同一请求最多续签两次，防止服务端持续 401 时打转；
     * · 唯一路径：全应用的续签都走 [refreshAccess]（bootstrap 的主动续签同样走这里），
     *   共用一把锁——此前 Shrine 另持一把锁走 Retrofit 重复续签，两条路径并发时
     *   带着同一个旋转 refresh 请求服务器，后到者失败，会把刚刷新好的用户误踢回登录页；
     * · 服务器是旋转式 refresh（旧 token 用一次即作废），续签结果里的
     *   新 refresh token 必须交还 [onSessionRefreshed] 持久化——
     *   之前只存了新 access，本地留着的旧 refresh 已作废，冷启动必被踢回登录页
     *   （「不能像 QQ 一样一直保持登录」的根因）。
     */
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

    /** 401 专用续签：持锁双检，别的请求刚刷好就直接复用新 token */
    private suspend fun refreshAfter401(staleToken: String?): String? = refreshMutex.withLock {
        if (staleToken != null && currentAccess != staleToken) {
            currentAccess
        } else {
            refreshAccessLocked()?.accessToken
        }
    }

    /**
     * 续签成功后由 Shrine 注入：把旋转出的新令牌落盘（会话槽 + 账号档案）。
     * 参数带 oldRefresh——服务端是旋转式签发，档案里存着的正是被换掉的那一枚，
     * Shrine 靠它定位该更新哪条档案。
     */
    var onSessionRefreshed: (suspend (oldRefresh: String, access: String, refresh: String) -> Unit)? = null

    /**
     * 轮换成功但结果装不回当前会话（请求在途时用户切号/登录了）：只补账号档案，
     * 不碰会话槽——否则会把新会话刚装好的令牌顶掉。
     */
    var onRotationAbandoned: (suspend (oldRefresh: String, access: String, refresh: String) -> Unit)? = null

    /** 续签被服务端拒绝（401/403：被踢/吊销/改密/过期）时触发；网络错误不触发——断网不能误登出 */
    var onSessionDead: (() -> Unit)? = null

    /**
     * 刷新 token（全应用唯一的续签入口）；失败返回 null（上层会把会话置为未登录）。
     * 走不带拦截器的裸 client：refresh 请求若再吃 401 会重入这把锁，直接死锁。
     * 网络必须切到 IO：execute() 是同步阻塞调用，此前所有调用方都恰好在 IO 线程
     * （Authenticator 回调 / bootstrap 作用域），首个从主线程协程调它的路径
     * （切换账号）当场 NetworkOnMainThreadException，被 catch 吞成 null
     */
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
                    // 服务端明确拒绝续签（被踢/吊销/改密/过期）→ 会话已死，收敛回登录页；
                    // 网络失败走 catch 分支，断网不能误登出
                    if (resp.code == 401 || resp.code == 403) onSessionDead?.invoke()
                    return null
                }
                val result = json.decodeFromString(AuthResultDto.serializer(), resp.body!!.string())
                // 请求在途时会话可能已被切号/登出：这枚结果属于旧会话，
                // 覆写回去会把新会话刚装好的令牌顶掉（切号被踢回登录页的根因）
                if (sessionGen != gen) {
                    // 但服务端那边旧令牌已经作废、新令牌只此一份：补进账号档案，
                    // 否则那个账号从此只剩重新登录一条路
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
            // 取消是调用方主动退出，按全工程惯例原样上抛，不能吞成「刷新失败」
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** 续签专用裸 client：懒加载，整个进程共用一个（此前每次续签都新建，连接池即弃） */
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
            // 联调诊断：debug 包打印请求行与失败原因（release 由 BuildConfig.DEBUG 关死）
            .apply {
                if (dev.arisu.shrine.BuildConfig.DEBUG) {
                    addInterceptor(
                        okhttp3.logging.HttpLoggingInterceptor { msg ->
                            android.util.Log.d("ArisuNet", msg)
                        }.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BASIC)
                    )
                }
            }
            .authenticator(authenticator)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // 总闸：单次调用（含整条连接+响应读取）硬上限，防半开连接等
            // 超时未覆盖的形态把调用方（下拉刷新指示器）无限挂住
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

    /**
     * 上传专用通道：同一套拦截器与连接池，只把超时放宽。
     *
     * 普通调用的 25s 总闸是按「小 JSON 往返」定的，手机流量上传几 MB 的图
     * 十有八九传不完就被掐断——表现就是「连接服务器失败」（用户反馈的根因之一）。
     * 上传另给 180s 总闸 + 120s 写超时，慢速上行也走得完。
     */
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

/** 后端返回的业务错误（中文提示直接透传给界面）；code 供限频等需要区分状态码的调用方使用 */
class ApiError(message: String, val code: Int? = null) : Exception(message)

/** 把 Retrofit/网络异常规整成给用户看的中文提示 */
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
    // 连不上服务器（断网 / 网络切换等 IOException 整族）：底层是英文异常
    // 信息（ConnectException / SocketException「Network unreachable」等），直接
    // 透出用户摸不着头脑——统一转成中文提示
    is java.io.IOException ->
        "连接服务器失败，请检查网络或稍后再试"
    else -> {
        val detail = if (message.isNullOrBlank()) this::class.java.simpleName else message
        "网络请求失败：$detail"
    }
}

/** 解析后端错误体为 ApiError（在 response() 拿不到时手动用） */
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
        throw ApiError(msg ?: "网络请求失败（HTTP ${e.code()}）", e.code())
    } catch (e: IOException) {
        // 只重试「请求确定没到达服务器」的失败：服务端重启/网络切换后，
        // 池化的旧连接在带 body 的请求上会失败一次（OkHttp 不重试已发
        // 请求体的调用），表现是连接被重置/断管，此时重发一次是安全的。
        // 读超时（SocketTimeoutException）与响应流中途断开（StreamReset、
        // unexpected end of stream）意味着请求可能已被处理——发帖/评论/
        // 切换收藏这类非幂等操作重试会造成重复提交，必须如实上抛。
        val retryable = e is java.net.SocketException ||
            e is java.net.ConnectException ||
            e is java.net.UnknownHostException
        if (!retryable) throw e
        return block()
    }
}
