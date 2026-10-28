package uz.oyna.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import uz.oyna.launcher.data.HomeStore
import uz.oyna.launcher.data.WidgetItem
import uz.oyna.launcher.data.WidgetKind
import uz.oyna.launcher.system.WidgetHostManager
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.Oyna
import uz.oyna.launcher.ui.glass.Backdrop
import uz.oyna.launcher.ui.glass.GlassSurface
import java.util.Calendar

val UzDays = listOf("Yakshanba", "Dushanba", "Seshanba", "Chorshanba", "Payshanba", "Juma", "Shanba")
val UzDaysShort = listOf("YAK", "DUSH", "SESH", "CHOR", "PAY", "JUM", "SHAN")
val UzMonths = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr")

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Har daqiqada yangilanadigan vaqt. */
@Composable
fun rememberNow(): Calendar {
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            val ms = System.currentTimeMillis()
            delay(60_000 - ms % 60_000 + 50)
            value = Calendar.getInstance()
        }
    }
    return now
}

@Composable
fun WidgetView(item: WidgetItem, backdrop: Backdrop?, modifier: Modifier = Modifier, interactive: Boolean = true) {
    when (item.kind) {
        WidgetKind.Clock -> ClockWidget(item, backdrop, modifier)
        WidgetKind.Calendar -> CalendarWidget(backdrop, modifier)
        WidgetKind.Battery -> BatteryWidget(backdrop, modifier)
        WidgetKind.System -> SystemWidget(item, backdrop, modifier, interactive)
    }
}

@Composable
private fun ClockWidget(item: WidgetItem, backdrop: Backdrop?, modifier: Modifier) {
    val now = rememberNow()
    val time = "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    val date = "${UzDays[now.get(Calendar.DAY_OF_WEEK) - 1]}, ${now.get(Calendar.DAY_OF_MONTH)}-${UzMonths[now.get(Calendar.MONTH)]}"
    GlassSurface(backdrop, modifier, cornerRadius = 28.dp, refraction = 26.dp, bezel = 24.dp, contentAlignment = Alignment.Center) {
        if (item.spanX >= 3) {
            Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = Oyna.Text, fontSize = 64.sp, fontWeight = FontWeight.Light, style = Oyna.iconLabel.copy(fontSize = 64.sp, fontWeight = FontWeight.Light))
                Text(date, color = Oyna.Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, style = Oyna.iconLabel.copy(fontSize = 38.sp, fontWeight = FontWeight.Light))
                Text(date, color = Oyna.Muted, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun CalendarWidget(backdrop: Backdrop?, modifier: Modifier) {
    val now = rememberNow()
    GlassSurface(backdrop, modifier, cornerRadius = 28.dp, refraction = 22.dp, bezel = 22.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(UzDaysShort[now.get(Calendar.DAY_OF_WEEK) - 1], color = Color(0xFFFF6B6B), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("${now.get(Calendar.DAY_OF_MONTH)}", style = Oyna.iconLabel.copy(fontSize = 52.sp, fontWeight = FontWeight.Light))
            Spacer(Modifier.weight(1f))
            Text(
                "${UzMonths[now.get(Calendar.MONTH)].replaceFirstChar { it.uppercase() }}, ${now.get(Calendar.YEAR)}",
                color = Oyna.Muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun BatteryWidget(backdrop: Backdrop?, modifier: Modifier) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (l >= 0) level = l * 100 / s
                val st = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val color = when {
        charging -> Color(0xFF4ADE80)
        level <= 20 -> Color(0xFFFF6B6B)
        else -> Color.White
    }
    GlassSurface(backdrop, modifier, cornerRadius = 28.dp, refraction = 22.dp, bezel = 22.dp, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx()
                val d = size.minDimension - w
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
                drawArc(color, -90f, 360f * level / 100f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    null, tint = color, modifier = Modifier.size(22.dp),
                )
                Text("$level%", style = Oyna.iconLabel.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SystemWidget(item: WidgetItem, backdrop: Backdrop?, modifier: Modifier, interactive: Boolean) {
    val info: AppWidgetProviderInfo? = remember(item.appWidgetId) {
        runCatching { WidgetHostManager.manager.getAppWidgetInfo(item.appWidgetId) }.getOrNull()
    }
    val density = LocalDensity.current
    GlassSurface(backdrop, modifier, cornerRadius = 26.dp, refraction = 18.dp, bezel = 18.dp, contentAlignment = Alignment.Center) {
        if (info == null) {
            Text("Vidjet mavjud emas", color = Oyna.Muted, fontSize = 13.sp)
        } else {
            var hostView by remember { mutableStateOf<AppWidgetHostView?>(null) }
            AndroidView(
                factory = { ctx ->
                    WidgetHostManager.host.createView(ctx, item.appWidgetId, info).also { hostView = it }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp))
                    .onSizeChanged { s ->
                        val wDp = with(density) { s.width.toDp().value.toInt() }
                        val hDp = with(density) { s.height.toDp().value.toInt() }
                        runCatching { hostView?.updateAppWidgetSize(null, wDp, hDp, wDp, hDp) }
                    },
            )
            if (!interactive) {
                // tahrirlash rejimida vidjet bosilmasin
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
            }
        }
    }
}

// ===================== Vidjet tanlash oynasi =====================

private data class ProviderRow(val info: AppWidgetProviderInfo, val label: String, val app: String, val icon: ImageBitmap?)

@Composable
fun WidgetPicker(state: LauncherState, home: Backdrop) {
    val context = LocalContext.current
    val rows = remember {
        val pm = context.packageManager
        WidgetHostManager.providers().map { p ->
            val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p.provider.packageName, 0)).toString() }
                .getOrDefault(p.provider.packageName)
            val icon = runCatching { p.loadIcon(context, context.resources.displayMetrics.densityDpi)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
            ProviderRow(p, runCatching { p.loadLabel(pm) }.getOrNull() ?: appLabel, appLabel, icon)
        }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
    }
    val close = { state.widgetPickerOpen = false }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)).pointerInput(Unit) { detectTapGestures { close() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassSurface(
            backdrop = home,
            cornerRadius = 34.dp,
            tint = Color(0x661C1F26),
            blur = 30.dp,
            refraction = 24.dp,
            bezel = 22.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .padding(8.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vidjet qo'shish", color = Oyna.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { close() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Yopish", tint = Oyna.Text, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("OYNA VIDJETLARI", color = Oyna.Faint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            Triple(WidgetKind.Clock, Icons.Rounded.Schedule, 4 to 2),
                            Triple(WidgetKind.Calendar, Icons.Rounded.CalendarMonth, 2 to 2),
                            Triple(WidgetKind.Battery, Icons.Rounded.BatteryStd, 2 to 2),
                        ).forEach { (kind, icon, span) ->
                            GlassSurface(
                                backdrop = null, cornerRadius = 20.dp,
                                modifier = Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(20.dp)).clickable {
                                    HomeStore.addWidget(state.currentPage, WidgetItem(HomeStore.newId(), kind, span.first, span.second))
                                    close()
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(icon, null, tint = Oyna.Text, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text(kind.title, color = Oyna.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${span.first}×${span.second}", color = Oyna.Faint, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text("TIZIM VIDJETLARI", color = Oyna.Faint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (rows.isEmpty()) Text("Vidjetlar topilmadi", color = Oyna.Muted, fontSize = 14.sp)
                }
                items(rows, key = { it.info.provider.flattenToString() }) { r ->
                    val (sx, sy) = WidgetHostManager.spanFor(r.info, context, HomeStore.settings.value.columns)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            val activity = context.findActivity() ?: return@clickable
                            close()
                            WidgetHostManager.startAdd(activity, r.info, state.currentPage)
                        }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                            if (r.icon != null) Image(r.icon, null, Modifier.size(32.dp))
                            else Icon(Icons.Rounded.Widgets, null, tint = Oyna.Muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.label, color = Oyna.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.app, color = Oyna.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("$sx×$sy", color = Oyna.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
