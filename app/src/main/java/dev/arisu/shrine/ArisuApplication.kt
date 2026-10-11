package dev.arisu.shrine

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder
import dev.arisu.shrine.core.util.SvgViewBoxDecoderFactory
import dev.arisu.shrine.data.repo.Shrine

private val svgDecoderFactory = SvgViewBoxDecoderFactory(
    SvgDecoder.Factory(scaleToDensity = true),
)

class ArisuApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        Shrine.init(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(svgDecoderFactory) }
            .build()
}
