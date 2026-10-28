package uz.oyna.launcher.ui.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.oyna.launcher.data.Apps
import uz.oyna.launcher.data.IconShape
import uz.oyna.launcher.data.IconStyle
import uz.oyna.launcher.ui.glass.GlassSurface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Ikonka shaklining burchak radiusi (shisha fon uchun), o'lchamga nisbatan. */
fun IconShape.cornerFraction(): Float = when (this) {
    IconShape.Squircle -> 0.27f
    IconShape.Rounded -> 0.22f
    IconShape.Circle -> 0.5f
}

object IconRenderer {

    fun shapePath(shape: IconShape, s: Float): Path = Path().apply {
        when (shape) {
            IconShape.Circle -> addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            IconShape.Rounded -> addRoundRect(0f, 0f, s, s, s * 0.22f, s * 0.22f, Path.Direction.CW)
            IconShape.Squircle -> {
                // Superellips |x|^5 + |y|^5 = 1 (iOS ikonka shakliga yaqin)
                val n = 5.0
                val r = s / 2
                val steps = 96
                for (i in 0..steps) {
                    val t = 2 * Math.PI * i / steps
                    val c = cos(t)
                    val sn = sin(t)
                    val x = r + r * Math.signum(c) * abs(c).pow(2 / n)
                    val y = r + r * Math.signum(sn) * abs(sn).pow(2 / n)
                    if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
                }
                close()
            }
        }
    }

    private val greyFilter by lazy {
        val m = ColorMatrix().apply { setSaturation(0f) }
        // biroz yorqinroq va oqroq
        val bright = ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 40f,
                0f, 1.25f, 0f, 0f, 40f,
                0f, 0f, 1.25f, 0f, 40f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        m.postConcat(bright)
        ColorMatrixColorFilter(m)
    }

    fun render(source: Drawable, s: Int, shape: IconShape, style: IconStyle): Bitmap {
        val d = source.mutate()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path = shapePath(shape, s.toFloat())

        if (d is AdaptiveIconDrawable) {
            val inset = s / 4
            c.save()
            c.clipPath(path)
            when (style) {
                IconStyle.Original -> {
                    d.background?.mutate()?.apply { setBounds(-inset, -inset, s + inset, s + inset); draw(c) }
                    d.foreground?.mutate()?.apply { setBounds(-inset, -inset, s + inset, s + inset); draw(c) }
                }
                IconStyle.Dark -> {
                    c.drawColor(0xFF1C1C1E.toInt())
                    d.foreground?.mutate()?.apply { setBounds(-inset, -inset, s + inset, s + inset); draw(c) }
                }
                IconStyle.Glass -> {
                    val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome else null
                    if (mono != null) {
                        mono.mutate().apply {
                            setTint(0xFFFFFFFF.toInt())
                            setTintMode(PorterDuff.Mode.SRC_IN)
                            setBounds(-inset, -inset, s + inset, s + inset)
                            draw(c)
                        }
                    } else {
                        d.foreground?.mutate()?.apply {
                            colorFilter = greyFilter
                            setBounds(-inset, -inset, s + inset, s + inset)
                            draw(c)
                            colorFilter = null
                        }
                    }
                }
            }
            c.restore()
        } else {
            when (style) {
                IconStyle.Original -> {
                    d.setBounds(0, 0, s, s)
                    d.draw(c)
                }
                IconStyle.Dark, IconStyle.Glass -> {
                    c.save()
                    c.clipPath(path)
                    if (style == IconStyle.Dark) c.drawColor(0xFF1C1C1E.toInt())
                    val pad = (s * 0.16f).toInt()
                    d.setBounds(pad, pad, s - pad, s - pad)
                    if (style == IconStyle.Glass) d.colorFilter = greyFilter
                    d.draw(c)
                    d.colorFilter = null
                    c.restore()
                }
            }
        }
        return bmp
    }
}

object IconCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun key(appKey: String, size: Int, shape: IconShape, style: IconStyle) = "$appKey|$size|${shape.name}|${style.name}"

    fun peek(k: String): ImageBitmap? = cache.get(k)

    suspend fun load(context: Context, appKey: String, size: Int, shape: IconShape, style: IconStyle): ImageBitmap? {
        val k = key(appKey, size, shape, style)
        cache.get(k)?.let { return it }
        return withContext(Dispatchers.Default) {
            val drawable = Apps.icon(appKey, context.resources.displayMetrics.densityDpi) ?: return@withContext null
            runCatching { IconRenderer.render(drawable, size, shape, style).asImageBitmap() }.getOrNull()
        }?.also { cache.put(k, it) }
    }

    fun clear() = cache.evictAll()
}

/**
 * Ilova ikonkasi. "Shisha" uslubida orqasida haqiqiy shisha chiziladi
 * (backdrop beriladi) — iOS 26 dagi "Clear" ikonkalar kabi.
 */
@Composable
fun AppIcon(
    appKey: String,
    size: Dp,
    shape: IconShape,
    style: IconStyle,
    backdrop: uz.oyna.launcher.ui.glass.Backdrop?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val k = IconCache.key(appKey, px, shape, style)
    val bitmap by produceState(initialValue = IconCache.peek(k), k) {
        if (value == null) value = IconCache.load(context, appKey, px, shape, style)
    }
    if (style == IconStyle.Glass) {
        GlassSurface(
            backdrop = backdrop,
            cornerRadius = size * shape.cornerFraction(),
            refraction = size * 0.18f,
            bezel = size * 0.22f,
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, null, Modifier.size(size)) }
        }
    } else {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, null, Modifier.size(size)) }
        }
    }
}

@Suppress("unused")
private val defaultIcon = 60.dp
