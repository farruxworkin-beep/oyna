package uz.oyna.launcher.system

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Boshqaruv paneli uchun tizim funksiyalari.
 * Android 10+ da oddiy ilova Wi-Fi/Bluetooth/mobil internetni o'zi yoqa olmaydi —
 * ular uchun tizimning kichik paneli (Settings.Panel) yoki sozlamalar ochiladi.
 */
object SystemControls {
    private lateinit var app: Context

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn
    private var torchCameraId: String? = null

    fun init(context: Context) {
        app = context.applicationContext
        val cm = app.getSystemService(CameraManager::class.java)
        torchCameraId = try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
        try {
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchCameraId) _torchOn.value = enabled
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    private fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_SHORT).show()

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    // ---------- Fonar ----------
    val hasTorch: Boolean get() = torchCameraId != null

    fun toggleTorch() {
        val id = torchCameraId ?: return toast("Fonar topilmadi")
        try {
            app.getSystemService(CameraManager::class.java).setTorchMode(id, !_torchOn.value)
        } catch (_: Exception) {
            toast("Fonar band (kamera ishlatilmoqda)")
        }
    }

    // ---------- Tarmoqlar ----------
    fun isWifiOn(): Boolean = try {
        app.getSystemService(WifiManager::class.java).isWifiEnabled
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun isBluetoothOn(): Boolean = try {
        app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun isAirplaneOn(): Boolean =
        Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun openWifi(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 29 && start(ctx, Intent(Settings.Panel.ACTION_WIFI))) return
        start(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    fun openInternet(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 29 && start(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))) return
        start(ctx, Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
    }

    fun openBluetooth(ctx: Context) {
        start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    fun openAirplane(ctx: Context) {
        start(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    }

    fun openBatterySaver(ctx: Context) {
        if (!start(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) start(ctx, Intent(Settings.ACTION_SETTINGS))
    }

    fun openSettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_SETTINGS))

    fun openDisplaySettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS))

    fun openCamera(ctx: Context) {
        if (!start(ctx, Intent("android.media.action.STILL_IMAGE_CAMERA"))) toast("Kamera topilmadi")
    }

    fun openCalculator(ctx: Context) {
        val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        if (!start(ctx, i)) toast("Kalkulyator topilmadi")
    }

    fun isBatterySaverOn(): Boolean = app.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    // ---------- Bildirishnomalar paneli ----------
    @SuppressLint("WrongConstant")
    fun expandNotifications() {
        try {
            val service = app.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        } catch (_: Throwable) {
            toast("Bildirishnomalarni ochish uchun ekran tepasidan pastga suring")
        }
    }

    // ---------- Sozlamalarga yozish ruxsati ----------
    fun canWriteSettings(): Boolean = Settings.System.canWrite(app)

    fun requestWriteSettings(ctx: Context) {
        toast("«Tizim sozlamalarini o'zgartirish»ga ruxsat bering")
        start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${app.packageName}")))
    }

    // ---------- Yorqinlik ----------
    /** Xiaomi telefonlarida yorqinlik diapazoni 255 dan katta bo'lishi mumkin. */
    private val maxBrightness: Int by lazy {
        val reflected = try {
            val pm = app.getSystemService(PowerManager::class.java)
            pm.javaClass.getMethod("getMaximumScreenBrightnessSetting").invoke(pm) as Int
        } catch (_: Throwable) {
            255
        }
        maxOf(255, reflected, Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0))
    }

    /** 0..1, ko'z qabul qilishiga yaqin (gamma) shkala. */
    fun brightness(): Float {
        val raw = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return (raw.toFloat() / maxBrightness).coerceIn(0f, 1f).pow(1 / 2.2f)
    }

    fun isAutoBrightness(): Boolean = Settings.System.getInt(
        app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun setBrightness(fraction: Float): Boolean {
        if (!canWriteSettings()) return false
        return try {
            Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val raw = (fraction.coerceIn(0.02f, 1f).pow(2.2f) * maxBrightness).roundToInt().coerceAtLeast(1)
            Settings.System.putInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setAutoBrightness(on: Boolean): Boolean {
        if (!canWriteSettings()) return false
        return runCatching {
            Settings.System.putInt(
                app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }.getOrDefault(false)
    }

    // ---------- Ekran aylanishi ----------
    fun isAutoRotate(): Boolean =
        Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun setAutoRotate(on: Boolean): Boolean {
        if (!canWriteSettings()) return false
        return runCatching {
            Settings.System.putInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        }.getOrDefault(false)
    }

    // ---------- Ovoz ----------
    private val audio: AudioManager get() = app.getSystemService(AudioManager::class.java)

    fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolume(fraction: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt().coerceIn(0, max), 0) }
    }

    /** 0 = ovoz, 1 = tebranish, 2 = jim */
    fun ringerMode(): Int = when (audio.ringerMode) {
        AudioManager.RINGER_MODE_VIBRATE -> 1
        AudioManager.RINGER_MODE_SILENT -> 2
        else -> 0
    }

    fun cycleRinger(ctx: Context) {
        val next = when (ringerMode()) {
            0 -> AudioManager.RINGER_MODE_VIBRATE
            1 -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        try {
            audio.ringerMode = next
        } catch (_: SecurityException) {
            // Jim rejim uchun "Bezovta qilmaslik"ga kirish ruxsati kerak
            if (next == AudioManager.RINGER_MODE_SILENT) {
                runCatching { audio.ringerMode = AudioManager.RINGER_MODE_NORMAL }
                requestDndAccess(ctx)
            }
        }
    }

    // ---------- Bezovta qilmaslik ----------
    private val notif: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    fun hasDndAccess(): Boolean = notif.isNotificationPolicyAccessGranted

    fun isDndOn(): Boolean = notif.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    fun toggleDnd(ctx: Context) {
        if (!hasDndAccess()) return requestDndAccess(ctx)
        runCatching {
            notif.setInterruptionFilter(
                if (isDndOn()) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
        }
    }

    private fun requestDndAccess(ctx: Context) {
        toast("«Bezovta qilmaslik»ga kirish uchun Oyna'ga ruxsat bering")
        start(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    // ---------- Standart launcher ----------
    fun isDefaultLauncher(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = ctx.packageManager.resolveActivity(i, 0)
        return res?.activityInfo?.packageName == ctx.packageName
    }

    fun openHomeSettings(ctx: Context) {
        if (!start(ctx, Intent(Settings.ACTION_HOME_SETTINGS))) {
            start(ctx, Intent(Settings.ACTION_SETTINGS))
        }
    }
}
