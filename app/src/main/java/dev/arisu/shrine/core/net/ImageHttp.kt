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

internal val imageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(imageRetryInterceptor)
        .build()
}
