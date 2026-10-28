package uz.oyna.launcher.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Launcher fon rasmi. Shisha effekti fonni "sindira" olishi uchun rasm ilovaning o'zida chiziladi.
 * Foydalanuvchi galereyadan tanlaydi; xohlasa tizim fon rasmi qilib ham o'rnatadi.
 */
object Wallpaper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _image = MutableStateFlow<ImageBitmap?>(null)
    val image: StateFlow<ImageBitmap?> = _image

    private fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    fun init(context: Context) {
        val ctx = context.applicationContext
        scope.launch { _image.value = withContext(Dispatchers.IO) { load(ctx) } }
    }

    fun hasCustom(ctx: Context) = file(ctx).exists()

    private fun load(ctx: Context): ImageBitmap? {
        val f = file(ctx)
        if (f.exists()) return decodeScaled(ctx, f.readBytes())?.asImageBitmap()
        // Tizim fon rasmini o'qishga urinib ko'ramiz (Android 13+ da ko'pincha ruxsat berilmaydi)
        return try {
            val dm = ctx.resources.displayMetrics
            WallpaperManager.getInstance(ctx).drawable
                ?.toBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
                ?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeScaled(ctx: Context, bytes: ByteArray): Bitmap? {
        val dm = ctx.resources.displayMetrics
        val targetW = (dm.widthPixels * 1.2f).toInt() // parallaks uchun biroz kengroq
        val targetH = dm.heightPixels
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Galereyadan tanlangan rasmni saqlaydi. */
    suspend fun setFromUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
            val bmp = decodeScaled(context, bytes) ?: return@withContext false
            FileOutputStream(file(context)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            _image.value = bmp.asImageBitmap()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** which: WallpaperManager.FLAG_SYSTEM / FLAG_LOCK yoki ikkalasi. */
    suspend fun applyToSystem(context: Context, which: Int): Boolean = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext false
        try {
            FileInputStream(f).use { WallpaperManager.getInstance(context).setStream(it, null, true, which) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun reset(context: Context) {
        file(context).delete()
        _image.value = null
        init(context)
    }
}
