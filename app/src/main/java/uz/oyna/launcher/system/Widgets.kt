package uz.oyna.launcher.system

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import uz.oyna.launcher.data.HomeStore
import uz.oyna.launcher.data.WidgetItem
import uz.oyna.launcher.data.WidgetKind
import kotlin.math.ceil

/** Tizim vidjetlarini (AppWidget) bosh ekranga joylash. */
object WidgetHostManager {
    const val HOST_ID = 4271
    const val REQ_BIND = 7101
    const val REQ_CONFIGURE = 7102

    lateinit var host: AppWidgetHost
        private set
    lateinit var manager: AppWidgetManager
        private set

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null
    private var pendingPage = 0

    /** Bitta katak o'lchami (dp) — UI o'rnatadi. */
    var cellWidthDp = 90f
    var cellHeightDp = 100f

    fun init(context: Context) {
        host = AppWidgetHost(context.applicationContext, HOST_ID)
        manager = AppWidgetManager.getInstance(context.applicationContext)
    }

    fun providers(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders
    } catch (_: Exception) {
        emptyList()
    }

    fun spanFor(info: AppWidgetProviderInfo, context: Context, cols: Int): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, cols) to info.targetCellHeight.coerceIn(1, 6)
        }
        val d = context.resources.displayMetrics.density
        val wDp = info.minWidth / d
        val hDp = info.minHeight / d
        val x = ceil(wDp / cellWidthDp).toInt().coerceIn(1, cols)
        val y = ceil(hDp / cellHeightDp).toInt().coerceIn(1, 6)
        return x to y
    }

    fun startAdd(activity: Activity, info: AppWidgetProviderInfo, page: Int) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        pendingPage = page
        val allowed = try {
            manager.bindAppWidgetIdIfAllowed(id, info.provider)
        } catch (_: Exception) {
            false
        }
        if (allowed) {
            configureOrFinish(activity)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun configureOrFinish(activity: Activity) {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null)
                return
            } catch (_: Exception) {
                // sozlash oynasi ochilmasa — to'g'ridan-to'g'ri qo'shamiz
            }
        }
        finish(activity)
    }

    private fun finish(context: Context) {
        val info = pendingInfo ?: return
        val (x, y) = spanFor(info, context, HomeStore.settings.value.columns)
        HomeStore.addWidget(pendingPage, WidgetItem(HomeStore.newId(), WidgetKind.System, x, y, pendingId))
        pendingId = -1
        pendingInfo = null
    }

    private fun cancel() {
        if (pendingId != -1) runCatching { host.deleteAppWidgetId(pendingId) }
        pendingId = -1
        pendingInfo = null
    }

    /** MainActivity.onActivityResult dan chaqiriladi. */
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            REQ_BIND -> {
                if (resultCode == Activity.RESULT_OK) configureOrFinish(activity) else {
                    cancel()
                    Toast.makeText(activity, "Vidjet qo'shilmadi", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_CONFIGURE -> if (resultCode == Activity.RESULT_OK) finish(activity) else cancel()
            else -> return false
        }
        return true
    }

    fun delete(appWidgetId: Int) {
        if (appWidgetId >= 0) runCatching { host.deleteAppWidgetId(appWidgetId) }
    }
}
