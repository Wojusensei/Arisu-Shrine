package dev.arisu.shrine.core.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 首次 + 两次重试，退避 300/900ms：只为吃掉瞬断，不做长等 */
private val retryBackoffMillis = longArrayOf(300L, 900L)

/**
 * 图片与静态资源共用的重试拦截器：Coil 的失败不会自己重来，第一次网络抖动
 * 就让那张图一直空着，直到退出页面重进才重建请求。这里就地补两次。
 */
internal val imageRetryInterceptor = Interceptor { chain ->
    var last: IOException? = null
    for (attempt in 0..retryBackoffMillis.size) {
        try {
            val response = chain.proceed(chain.request())
            // 5xx 多半是回源抖动，值得再要一次；4xx 是确定性失败，重试无意义
            if (response.code >= 500 && attempt < retryBackoffMillis.size) {
                response.close()
                last = IOException("HTTP ${response.code}")
                Thread.sleep(retryBackoffMillis[attempt])
                continue
            }
            return@Interceptor response
        } catch (e: IOException) {
            last = e
            if (attempt < retryBackoffMillis.size) Thread.sleep(retryBackoffMillis[attempt])
        }
    }
    throw last ?: IOException("图片请求失败")
}

/**
 * 图片请求头：按浏览器发。
 *
 * 只带 okhttp 的 User-Agent 时，Cloudflare 前面的图床（如 README 里的访客计数器
 * count.getloli.com）直接甩一张「Just a moment…」的人机校验页（403），解码器拿到
 * HTML 只能显示占位；补上 UA + Accept 才有 200 的 SVG（实测：只补 UA 仍是 403）。
 */
internal val imageHeadersInterceptor = Interceptor { chain ->
    val request = chain.request().newBuilder()
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .build()
    chain.proceed(request)
}

/** 图片专用客户端：超时放宽（默认 10 秒对跨境图片偏紧）+ 瞬断重试 + 浏览器请求头 */
internal val imageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(imageHeadersInterceptor)
        .addInterceptor(imageRetryInterceptor)
        .build()
}

/**
 * 列表/头像用的小图地址：站内上传图挂 ?w=，服务端按需出缩略图。
 *
 * 只认本站 /uploads/ 的 http(s) 地址：外链图、本地文件路径、content:// 原样返回；
 * 服务端对 gif/webp 不做缩略（动图不能被拍成静帧），会原图下发，所以这里无条件加
 * 参数也安全。详情页/查看大图仍用原地址，不经过这里。
 */
fun thumbUrl(url: String?, width: Int): String? {
    if (url.isNullOrEmpty() || !url.startsWith("http") || !url.contains("/uploads/")) return url
    if (url.contains("?w=") || url.contains("&w=")) return url
    return url + (if (url.contains('?')) "&" else "?") + "w=" + width
}
