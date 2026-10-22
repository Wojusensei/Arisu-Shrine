package dev.arisu.shrine.core.net

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

private val retryBackoffMillis = longArrayOf(300L, 900L)

internal val imageRetryInterceptor = Interceptor { chain ->
    var last: IOException? = null
    for (attempt in 0..retryBackoffMillis.size) {
        try {
            val response = chain.proceed(chain.request())

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

fun thumbUrl(url: String?, width: Int): String? {
    if (url.isNullOrEmpty() || !url.startsWith("http") || !url.contains("/uploads/")) return url
    if (url.contains("?w=") || url.contains("&w=")) return url
    return url + (if (url.contains('?')) "&" else "?") + "w=" + width
}
