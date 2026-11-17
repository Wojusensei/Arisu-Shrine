package dev.arisu.shrine

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import dev.arisu.shrine.core.net.imageClient
import dev.arisu.shrine.core.util.SvgViewBoxDecoderFactory
import dev.arisu.shrine.data.repo.Shrine

/**
 * 给缺 viewBox 的 SVG 注入 viewBox：见 [SvgViewBoxDecoderFactory]。
 * scaleToDensity：size(ORIGINAL) 请求下按「自然尺寸×密度」解码，
 * 展示层换算回 dp 恰是 GitHub 的 CSS px 观感。
 */
private val svgDecoderFactory = SvgViewBoxDecoderFactory(
    SvgDecoder.Factory(scaleToDensity = true),
)

class ArisuApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // 初始化会话：读 token 与服务器地址，校验通过则自动进入主界面
        Shrine.init(this)
    }

    // GitHub 式主页美化（shields.io 徽章、统计卡）几乎全是 SVG，
    // Coil 默认只认位图，不挂解码器整块图片全部加载失败；
    // GIF 同理：不挂动图解码器，表情包里的 gif 只会停在第一帧
    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(svgDecoderFactory)
                // AnimatedImageDecoder 走系统 ImageDecoder，API 28 起才有；
                // 低版本直接落到 GifDecoder，不能无条件引用高版本类
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                }
                add(GifDecoder.Factory())
                // 网络层换成带重试的客户端：Coil 自己不重试，见 core/net/ImageHttp
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .build()
}
