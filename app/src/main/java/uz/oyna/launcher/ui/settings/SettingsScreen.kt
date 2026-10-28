package uz.oyna.launcher.ui.settings

import android.app.WallpaperManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.oyna.launcher.data.HomeStore
import uz.oyna.launcher.data.IconShape
import uz.oyna.launcher.data.IconStyle
import uz.oyna.launcher.data.LauncherSettings
import uz.oyna.launcher.data.Wallpaper
import uz.oyna.launcher.system.SystemControls
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.Oyna
import uz.oyna.launcher.ui.glass.Backdrop
import uz.oyna.launcher.ui.glass.GlassSurface
import kotlin.math.roundToInt

/** Launcher tashqi ko'rinishini boshqarish. */
@Composable
fun SettingsScreen(state: LauncherState, home: Backdrop, settings: LauncherSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askApply by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (Wallpaper.setFromUri(context, uri)) askApply = true
            else Toast.makeText(context, "Rasmni ochib bo'lmadi", Toast.LENGTH_SHORT).show()
        }
    }

    GlassSurface(
        backdrop = home, cornerRadius = 0.dp, tint = Color(0x8C0B0D12),
        refraction = 0.dp, bezel = 1.dp, blur = 34.dp, rimAlpha = 0f,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sozlamalar", color = Oyna.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                        .clickable { state.settingsOpen = false },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Yopish", tint = Oyna.Text) }
            }

            // ---------- Fon rasmi ----------
            Section("Fon rasmi") {
                ActionRow(Icons.Rounded.Image, "Galereyadan tanlash") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (Wallpaper.hasCustom(context)) {
                    ActionRow(Icons.Rounded.Wallpaper, "Tizim fon rasmi qilib o'rnatish") { askApply = true }
                    ActionRow(Icons.Rounded.RestartAlt, "Standart fonga qaytarish") { Wallpaper.reset(context) }
                }
                Hint("Shisha effekti fon rasmini sindirishi uchun rasm Oyna ichida chiziladi.")
            }

            // ---------- Shisha ----------
            Section("Shisha effekti") {
                SliderRow("Sinish kuchi", settings.refraction, 0f..50f, "${settings.refraction.roundToInt()}") { v ->
                    HomeStore.updateSettings { it.copy(refraction = v) }
                }
                SliderRow("Xiralik", settings.blur, 0f..20f, "${settings.blur.roundToInt()}") { v ->
                    HomeStore.updateSettings { it.copy(blur = v) }
                }
                SliderRow("Oq tus", settings.tint, 0f..0.35f, "${(settings.tint * 100).roundToInt()}%") { v ->
                    HomeStore.updateSettings { it.copy(tint = v) }
                }
                SwitchRow("Rang ajralishi (qirrada)", settings.chroma) { v -> HomeStore.updateSettings { it.copy(chroma = v) } }
                Hint("Haqiqiy sinish Android 13 va undan yuqorida ishlaydi.")
            }

            // ---------- Ikonkalar ----------
            Section("Ikonkalar") {
                Label("Uslub")
                ChipRow(IconStyle.entries.map { it.label }, settings.iconStyle.ordinal) { i ->
                    HomeStore.updateSettings { it.copy(iconStyle = IconStyle.entries[i]) }
                }
                Label("Shakl")
                ChipRow(IconShape.entries.map { it.label }, settings.iconShape.ordinal) { i ->
                    HomeStore.updateSettings { it.copy(iconShape = IconShape.entries[i]) }
                }
                SliderRow("O'lcham", settings.iconSizeDp.toFloat(), 44f..72f, "${settings.iconSizeDp} dp") { v ->
                    HomeStore.updateSettings { it.copy(iconSizeDp = v.roundToInt()) }
                }
                SwitchRow("Nomlarni ko'rsatish", settings.showLabels) { v -> HomeStore.updateSettings { it.copy(showLabels = v) } }
            }

            // ---------- Setka ----------
            Section("Bosh ekran") {
                Label("Ustunlar soni")
                ChipRow(listOf("4", "5"), if (settings.columns == 5) 1 else 0) { i ->
                    HomeStore.updateSettings { it.copy(columns = if (i == 1) 5 else 4) }
                }
                SwitchRow("Fon parallaksi", settings.parallax) { v -> HomeStore.updateSettings { it.copy(parallax = v) } }
                SwitchRow("Yangi ilovalarni bosh ekranga qo'shish", settings.addNewApps) { v -> HomeStore.updateSettings { it.copy(addNewApps = v) } }
                SwitchRow("Pastga surish — bildirishnomalar", settings.swipeDownNotifications) { v ->
                    HomeStore.updateSettings { it.copy(swipeDownNotifications = v) }
                }
                Hint("O'chiq bo'lsa, pastga surganda Oyna boshqaruv paneli ochiladi.")
            }

            // ---------- Tizim ----------
            Section("Tizim") {
                ActionRow(Icons.Rounded.Home, "Standart bosh ekran qilish") { SystemControls.openHomeSettings(context) }
                ActionRow(Icons.Rounded.AdminPanelSettings, "Yorqinlik va aylanishga ruxsat") { SystemControls.requestWriteSettings(context) }
                ActionRow(Icons.Rounded.Settings, "Telefon sozlamalari") { SystemControls.openSettings(context) }
                ActionRow(Icons.Rounded.DisplaySettings, "Ekran (shrift, mavzu) sozlamalari") { SystemControls.openDisplaySettings(context) }
            }

            Text(
                "Oyna launcher · ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}",
                color = Oyna.Faint, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
    }

    if (askApply) {
        AlertDialog(
            onDismissRequest = { askApply = false },
            containerColor = Oyna.Panel,
            title = { Text("Tizim fon rasmi") },
            text = { Text("Rasm Oyna'da o'rnatildi. Uni tizimga ham qo'yaymi?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    listOf(
                        "Bosh ekran va qulf ekrani" to (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
                        "Faqat qulf ekrani" to WallpaperManager.FLAG_LOCK,
                        "Faqat bosh ekran" to WallpaperManager.FLAG_SYSTEM,
                    ).forEach { (label, flag) ->
                        TextButton(onClick = {
                            askApply = false
                            scope.launch {
                                val ok = Wallpaper.applyToSystem(context, flag)
                                Toast.makeText(context, if (ok) "Fon rasmi o'rnatildi" else "O'rnatib bo'lmadi", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text(label, color = Oyna.Accent) }
                    }
                    TextButton(onClick = { askApply = false }) { Text("Kerak emas", color = Oyna.Muted) }
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(), color = Oyna.Faint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
    GlassSurface(backdrop = null, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = Oyna.Muted, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Oyna.Faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Oyna.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = Oyna.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = Oyna.Faint)
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Oyna.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF34C759),
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SliderRow(text: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(text, color = Oyna.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(display, color = Oyna.Muted, fontSize = 14.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Oyna.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val sel = i == selected
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (sel) Oyna.Accent else Color.White.copy(alpha = 0.12f))
                    .clickable { onSelect(i) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(l, color = if (sel) Color.Black else Oyna.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
