package uz.oyna.launcher.ui.glass

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * LIQUID GLASS
 * ------------
 * 1) `backdropSource` — ortdagi kontentni (fon rasmi) GraphicsLayer ga yozib oladi.
 * 2) `GlassSurface`    — shu qatlamni o'z joyiga siljitib chizadi va effekt beradi:
 *      • Android 13+ (API 33): AGSL shader — chetlarida linzadek sinish + rang ajralishi + yengil blur
 *      • Android 12: faqat blur
 *      • Eski telefonlar: yarim shaffof to'q fon
 * GlassSurface o'zi foydalanayotgan backdropSource ICHIDA bo'lmasligi kerak.
 */

/** Sozlamalardan keladigan umumiy shisha parametrlari. */
@Immutable
data class GlassConfig(
    val refractionScale: Float = 1f,
    val extraBlur: Float = 0f,
    val tint: Float = 0.10f,
    val chroma: Boolean = true,
)

val LocalGlassConfig = staticCompositionLocalOf { GlassConfig() }

@Stable
class Backdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Composable
fun GlassSurface(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    tint: Color? = null,
    refraction: Dp = 22.dp,
    bezel: Dp = 18.dp,
    blur: Dp = 2.dp,
    rimAlpha: Float = 0.55f,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val cfg = LocalGlassConfig.current
    val inner = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val sampling = backdrop != null && Build.VERSION.SDK_INT >= 31
    val fillColor = tint ?: Color.White.copy(alpha = cfg.tint)

    Box(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInRoot() }
            .drawWithCache {
                val r = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
                val outline = Path().apply {
                    addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r, r)))
                }
                val effect = if (sampling) {
                    GlassEffects.create(
                        size, r, bezel.toPx(),
                        refraction.toPx() * cfg.refractionScale,
                        blur.toPx() + cfg.extraBlur * density,
                        if (cfg.chroma) 0.08f else 0f,
                    )
                } else null
                val fill = if (backdrop != null && !sampling) Color(0xD91A1C22) else fillColor
                val rim = Brush.linearGradient(
                    0f to Color.White.copy(alpha = rimAlpha),
                    0.35f to Color.White.copy(alpha = rimAlpha * 0.22f),
                    0.6f to Color.Transparent,
                    1f to Color.White.copy(alpha = rimAlpha * 0.55f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val sheen = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.45f to Color.Transparent,
                    1f to Color.Transparent,
                )
                val stroke = Stroke(width = 1.dp.toPx())

                onDrawBehind {
                    if (sampling && backdrop != null) {
                        val offset = position - backdrop.origin
                        inner.renderEffect = effect
                        inner.record {
                            translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                        }
                        clipPath(outline) { drawLayer(inner) }
                    }
                    drawPath(outline, fill)
                    drawPath(outline, sheen)
                    drawPath(outline, rim, style = stroke)
                }
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

internal object GlassEffects {
    fun create(size: Size, radius: Float, bezel: Float, refraction: Float, blur: Float, chroma: Float): RenderEffect? {
        if (size.width < 1f || size.height < 1f) return null
        return when {
            Build.VERSION.SDK_INT >= 33 -> Api33.refraction(size, radius, bezel, refraction, blur, chroma)
            Build.VERSION.SDK_INT >= 31 -> Api31.blur(maxOf(blur, 18f))
            else -> null
        }
    }
}

@RequiresApi(31)
private object Api31 {
    fun blur(radius: Float): RenderEffect =
        android.graphics.RenderEffect
            .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
}

@RequiresApi(33)
private object Api33 {
    private const val SHADER = """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float bezel;
        uniform float strength;
        uniform float chroma;

        float sdRoundRect(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r, r);
            return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 coord) {
            float2 hs = size * 0.5;
            float2 p = coord - hs;
            float d = -sdRoundRect(p, hs, radius);
            if (d <= 0.0 || d >= bezel) {
                return content.eval(coord);
            }
            float e = 0.75;
            float gx = sdRoundRect(p + float2(e, 0.0), hs, radius) - sdRoundRect(p - float2(e, 0.0), hs, radius);
            float gy = sdRoundRect(p + float2(0.0, e), hs, radius) - sdRoundRect(p - float2(0.0, e), hs, radius);
            float2 g = float2(gx, gy);
            float len = length(g);
            float2 n = float2(0.0, 0.0);
            if (len > 0.0001) {
                n = g / len;
            }
            float t = clamp(d / bezel, 0.0, 1.0);
            float mag = pow(1.0 - t, 2.2) * strength;
            float2 off = -n * mag;
            half4 base = content.eval(coord + off);
            half rr = content.eval(coord + off * (1.0 + chroma)).r;
            half bb = content.eval(coord + off * (1.0 - chroma)).b;
            return half4(rr, base.g, bb, base.a);
        }
    """

    fun refraction(size: Size, radius: Float, bezel: Float, strength: Float, blur: Float, chroma: Float): RenderEffect {
        val shader = RuntimeShader(SHADER)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bezel", bezel.coerceAtLeast(1f))
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("chroma", chroma)
        val refract = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val combined = if (blur >= 0.5f) {
            android.graphics.RenderEffect.createChainEffect(
                refract,
                android.graphics.RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
            )
        } else refract
        return combined.asComposeRenderEffect()
    }
}
