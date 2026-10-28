package uz.oyna.launcher.ui.control

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uz.oyna.launcher.system.SystemControls
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.Oyna
import uz.oyna.launcher.ui.glass.Backdrop
import uz.oyna.launcher.ui.glass.GlassSurface
import uz.oyna.launcher.ui.widgets.UzDays
import uz.oyna.launcher.ui.widgets.UzMonths
import uz.oyna.launcher.ui.widgets.rememberNow
import java.util.Calendar

/** iOS uslubidagi boshqaruv paneli (bosh ekranda pastga surilganda ochiladi). */
@Composable
fun ControlCenter(state: LauncherState, home: Backdrop) {
    val context = LocalContext.current
    val torch by SystemControls.torchOn.collectAsStateWithLifecycle()
    var tickKey by remember { mutableIntStateOf(0) }
    // Tizim panellaridan qaytganda holat yangilanib tursin
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tickKey++
        }
    }
    val wifi = remember(tickKey) { SystemControls.isWifiOn() }
    val bt = remember(tickKey) { SystemControls.isBluetoothOn() }
    val airplane = remember(tickKey) { SystemControls.isAirplaneOn() }
    val rotate = remember(tickKey) { SystemControls.isAutoRotate() }
    val ringer = remember(tickKey) { SystemControls.ringerMode() }
    val dnd = remember(tickKey) { SystemControls.isDndOn() }
    val saver = remember(tickKey) { SystemControls.isBatterySaverOn() }
    val autoBright = remember(tickKey) { SystemControls.isAutoBrightness() }
    var brightness by remember { mutableFloatStateOf(SystemControls.brightness()) }
    var volume by remember { mutableFloatStateOf(SystemControls.volume()) }
    val now = rememberNow()
    val noRipple = remember { MutableInteractionSource() }
    val close = { state.controlOpen = false }
    val refresh = { tickKey++ }

    Box(Modifier.fillMaxSize()) {
        // Orqa fon: kuchli xiralangan shisha
        GlassSurface(
            backdrop = home, cornerRadius = 0.dp, tint = Color(0x590B0D12),
            refraction = 0.dp, bezel = 1.dp, blur = 30.dp, rimAlpha = 0f,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = noRipple, indication = null) { close() }
                .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -20f) close() } },
        ) {}

        BoxWithConstraints(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            val gap = 12.dp
            val s = (maxWidth - gap * 3) / 4

            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                // Sana
                Text(
                    "${UzDays[now.get(Calendar.DAY_OF_WEEK) - 1]}, ${now.get(Calendar.DAY_OF_MONTH)}-${UzMonths[now.get(Calendar.MONTH)]}",
                    style = Oyna.iconLabel.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    // Tarmoqlar bloki 2x2
                    GlassSurface(
                        home, Modifier.size(s * 2 + gap), cornerRadius = 30.dp, blur = 28.dp,
                        tint = Color.White.copy(alpha = 0.10f), refraction = 20.dp, bezel = 20.dp,
                    ) {
                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                RoundToggle(Icons.Rounded.AirplanemodeActive, "Samolyot", airplane, Color(0xFFFF9F0A)) { SystemControls.openAirplane(context) }
                                RoundToggle(Icons.Rounded.SignalCellularAlt, "Mobil internet", false, Color(0xFF30D158)) { SystemControls.openInternet(context) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                RoundToggle(Icons.Rounded.Wifi, "Wi-Fi", wifi, Color(0xFF0A84FF)) { SystemControls.openWifi(context) }
                                RoundToggle(Icons.Rounded.Bluetooth, "Bluetooth", bt, Color(0xFF0A84FF)) { SystemControls.openBluetooth(context) }
                            }
                        }
                    }
                    // Yorqinlik
                    VerticalSlider(
                        home, s, s * 2 + gap,
                        value = brightness,
                        icon = if (autoBright) Icons.Rounded.BrightnessAuto else Icons.Rounded.LightMode,
                        onStart = {
                            if (!SystemControls.canWriteSettings()) {
                                SystemControls.requestWriteSettings(context); false
                            } else true
                        },
                        onChange = { v -> brightness = v; SystemControls.setBrightness(v) },
                    )
                    // Ovoz
                    VerticalSlider(
                        home, s, s * 2 + gap,
                        value = volume,
                        icon = if (volume < 0.01f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        onStart = { true },
                        onChange = { v -> volume = v; SystemControls.setVolume(v) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    SquareTile(home, s, Icons.Rounded.FlashlightOn, "Fonar", torch) { SystemControls.toggleTorch() }
                    SquareTile(home, s, if (rotate) Icons.Rounded.ScreenRotation else Icons.Rounded.ScreenLockRotation, if (rotate) "Aylanish" else "Qulf", !rotate) {
                        if (!SystemControls.canWriteSettings()) SystemControls.requestWriteSettings(context)
                        else { SystemControls.setAutoRotate(!rotate); refresh() }
                    }
                    SquareTile(
                        home, s,
                        when (ringer) { 1 -> Icons.Rounded.Vibration; 2 -> Icons.Rounded.NotificationsOff; else -> Icons.Rounded.NotificationsActive },
                        when (ringer) { 1 -> "Tebranish"; 2 -> "Jim"; else -> "Ovoz" },
                        ringer != 0,
                    ) { SystemControls.cycleRinger(context); refresh() }
                    SquareTile(home, s, Icons.Rounded.DarkMode, "Bezovta qilmaslik", dnd, small = true) { SystemControls.toggleDnd(context); refresh() }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    SquareTile(home, s, Icons.Rounded.PhotoCamera, "Kamera", false) { close(); SystemControls.openCamera(context) }
                    SquareTile(home, s, Icons.Rounded.Calculate, "Kalkulyator", false, small = true) { close(); SystemControls.openCalculator(context) }
                    SquareTile(home, s, Icons.Rounded.BatterySaver, "Tejash", saver) { SystemControls.openBatterySaver(context) }
                    SquareTile(home, s, Icons.Rounded.Settings, "Sozlamalar", false, small = true) { close(); SystemControls.openSettings(context) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    WideTile(home, s * 2 + gap, s * 0.8f, Icons.Rounded.Notifications, "Bildirishnomalar") {
                        close(); SystemControls.expandNotifications()
                    }
                    WideTile(home, s * 2 + gap, s * 0.8f, Icons.Rounded.BrightnessAuto, if (autoBright) "Avto-yorqinlik: yoq" else "Avto-yorqinlik: o'chiq") {
                        if (!SystemControls.canWriteSettings()) SystemControls.requestWriteSettings(context)
                        else { SystemControls.setAutoBrightness(!autoBright); refresh() }
                    }
                }

                Text(
                    "Yopish uchun bo'sh joyga bosing yoki tepaga suring",
                    color = Oyna.Faint, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundToggle(icon: ImageVector, desc: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape)
            .background(if (active) activeColor else Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun SquareTile(
    home: Backdrop,
    size: Dp,
    icon: ImageVector,
    label: String,
    active: Boolean,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    GlassSurface(
        home,
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).clickable(onClick = onClick),
        cornerRadius = size * 0.3f,
        tint = if (active) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
        blur = 28.dp,
        refraction = 14.dp,
        bezel = 14.dp,
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (active) Color(0xFF111318) else Color.White
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = fg, modifier = Modifier.size(if (small) 24.dp else 28.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WideTile(home: Backdrop, width: Dp, height: Dp, icon: ImageVector, label: String, onClick: () -> Unit) {
    GlassSurface(
        home,
        Modifier.width(width).height(height).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        cornerRadius = 24.dp,
        blur = 28.dp,
        refraction = 16.dp,
        bezel = 16.dp,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

/** iOS dagi kabi baland "kapsula" slayder (yorqinlik, ovoz). */
@Composable
private fun VerticalSlider(
    home: Backdrop,
    width: Dp,
    height: Dp,
    value: Float,
    icon: ImageVector,
    onStart: () -> Boolean,
    onChange: (Float) -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val start by rememberUpdatedState(onStart)
    val current by rememberUpdatedState(value)
    GlassSurface(
        home,
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(width * 0.42f))
            .pointerInput(Unit) {
                var allowed = true
                var v = current
                detectVerticalDragGestures(
                    onDragStart = { allowed = start(); v = current },
                ) { ch, dy ->
                    ch.consume()
                    if (!allowed) return@detectVerticalDragGestures
                    v = (v - dy / size.height).coerceIn(0f, 1f)
                    change(v)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    if (start()) change((1f - p.y / size.height).coerceIn(0f, 1f))
                }
            },
        cornerRadius = width * 0.42f,
        blur = 28.dp,
        refraction = 14.dp,
        bezel = 16.dp,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val h = size.height * value.coerceIn(0f, 1f)
                drawRoundRect(
                    Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(0f, size.height - h),
                    size = Size(size.width, h),
                    cornerRadius = CornerRadius(0f, 0f),
                )
            }
        )
        Icon(
            icon, null,
            tint = if (value > 0.18f) Color(0xFF3A3D45) else Color.White,
            modifier = Modifier.padding(bottom = 16.dp).size(26.dp),
        )
    }
}
