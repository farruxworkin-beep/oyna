package uz.oyna.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val installTime: Long,
    val category: Int,
) {
    val packageName: String get() = component.packageName
}

/** Telefondagi barcha ishga tushiriladigan ilovalar (LauncherApps orqali). */
object Apps {
    private lateinit var app: Context
    private lateinit var launcherApps: LauncherApps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    @Volatile private var byKey: Map<String, AppInfo> = emptyMap()
    @Volatile private var activityByKey: Map<String, LauncherActivityInfo> = emptyMap()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(added = packageName)
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    fun init(context: Context) {
        app = context.applicationContext
        launcherApps = app.getSystemService(LauncherApps::class.java)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload(added: String? = null) {
        scope.launch {
            val list = withContext(Dispatchers.IO) { load() }
            byKey = list.associateBy { it.key }
            _apps.value = list
            _loaded.value = true
            HomeStore.onAppsChanged(list, added)
        }
    }

    private fun load(): List<AppInfo> {
        val user = Process.myUserHandle()
        val activities = try {
            launcherApps.getActivityList(null, user)
        } catch (_: Exception) {
            emptyList()
        }
        val map = HashMap<String, LauncherActivityInfo>()
        val result = activities
            .filter { it.componentName.packageName != app.packageName }
            .map { info ->
                val key = info.componentName.flattenToShortString()
                map[key] = info
                AppInfo(
                    key = key,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName },
                    component = info.componentName,
                    user = info.user,
                    installTime = info.firstInstallTime,
                    category = info.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED,
                )
            }
            .sortedBy { it.label.lowercase() }
        activityByKey = map
        return result
    }

    fun get(key: String): AppInfo? = byKey[key]

    fun icon(key: String, densityDpi: Int): Drawable? = try {
        activityByKey[key]?.getIcon(densityDpi)
    } catch (_: Exception) {
        null
    }

    fun launch(context: Context, key: String, bounds: Rect? = null) {
        val info = byKey[key] ?: return
        try {
            launcherApps.startMainActivity(info.component, info.user, bounds, null)
        } catch (_: Exception) {
            Toast.makeText(context, "Ilovani ochib bo'lmadi", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDetails(key: String) {
        val info = byKey[key] ?: return
        runCatching { launcherApps.startAppDetailsActivity(info.component, info.user, null, null) }
    }

    fun uninstall(context: Context, key: String) {
        val info = byKey[key] ?: return
        try {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${info.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            openDetails(key)
        }
    }

    fun isSystemApp(key: String): Boolean {
        val ai = activityByKey[key]?.applicationInfo ?: return false
        return ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 && ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }

    // ---------- Tezkor amallar (app shortcuts) ----------
    fun shortcuts(key: String): List<ShortcutInfo> {
        val info = byKey[key] ?: return emptyList()
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
                .setPackage(info.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (launcherApps.getShortcuts(q, info.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                .take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun shortcutIcon(s: ShortcutInfo, densityDpi: Int): Drawable? =
        runCatching { launcherApps.getShortcutIconDrawable(s, densityDpi) }.getOrNull()

    fun startShortcut(context: Context, s: ShortcutInfo) {
        try {
            launcherApps.startShortcut(s, null, null)
        } catch (_: Exception) {
            Toast.makeText(context, "Amalni bajarib bo'lmadi", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Standart ilovalarni topish (dok uchun) ----------
    fun defaultDockKeys(): List<String> {
        val pm = app.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val list = _apps.value
        return intents.mapNotNull { intent ->
            val pkg = try {
                pm.resolveActivity(intent, 0)?.activityInfo?.packageName
            } catch (_: Exception) {
                null
            }
            val byDefault = pkg?.let { p -> list.firstOrNull { it.packageName == p } }
            byDefault ?: run {
                // standart tanlanmagan bo'lsa — birinchi mos ilova
                val candidates = try {
                    pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }
                } catch (_: Exception) {
                    emptyList()
                }
                list.firstOrNull { it.packageName in candidates }
            }
        }.map { it.key }.distinct()
    }

    fun categoryName(category: Int): String = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "O'yinlar"
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Media"
        ApplicationInfo.CATEGORY_IMAGE -> "Foto"
        ApplicationInfo.CATEGORY_SOCIAL -> "Ijtimoiy"
        ApplicationInfo.CATEGORY_NEWS -> "Yangiliklar"
        ApplicationInfo.CATEGORY_MAPS -> "Xaritalar"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Ish va unumdorlik"
        else -> "Papka"
    }
}
