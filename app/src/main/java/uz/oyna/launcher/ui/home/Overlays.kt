package uz.oyna.launcher.ui.home

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import uz.oyna.launcher.data.*
import uz.oyna.launcher.system.WidgetHostManager
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.MenuTarget
import uz.oyna.launcher.ui.Oyna
import uz.oyna.launcher.ui.glass.Backdrop
import uz.oyna.launcher.ui.glass.GlassSurface
import uz.oyna.launcher.ui.icons.AppIcon
import uz.oyna.launcher.ui.widgets.WidgetView
import kotlin.math.roundToInt

/** Ochilgan papka (iOS kabi markazda katta shisha karta). */
@Composable
fun FolderOverlay(state: LauncherState, home: Backdrop, folder: FolderItem, settings: LauncherSettings) {
    val context = LocalContext.current
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(interactionSource = noRipple, indication = null) {
                if (name.isNotBlank() && name != folder.name) HomeStore.renameFolder(folder.id, name.trim())
                state.openFolderId = null
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            backdrop = home,
            cornerRadius = 38.dp,
            tint = Color.White.copy(alpha = 0.14f),
            refraction = 30.dp,
            bezel = 28.dp,
            blur = 26.dp,
            modifier = Modifier
                .padding(horizontal = 26.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    singleLine = true,
                    textStyle = TextStyle(color = Oyna.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Oyna.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                val cols = 3
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    folder.keys.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { k ->
                                Column(
                                    Modifier.weight(1f).launcherGestures(
                                        key = "f-$k",
                                        editMode = false,
                                        canDrag = false,
                                        onTap = { b ->
                                            Apps.launch(context, k, b.toAndroidRect())
                                            state.openFolderId = null
                                        },
                                        onLongPress = { b -> state.menu = MenuTarget(anchor = b, appKey = k, folderId = folder.id) },
                                        onDragStart = { _, _, _ -> },
                                        onDragMove = {},
                                        onDragEnd = {},
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AppIcon(k, settings.iconSizeDp.dp, settings.iconShape, settings.iconStyle, null)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        Apps.get(k)?.label ?: "", style = Oyna.iconLabel, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp),
                                    )
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Uzoq bosilganda chiqadigan shisha menyu: tezkor amallar + boshqaruv. */
@Composable
fun ContextMenu(state: LauncherState, home: Backdrop, target: MenuTarget) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val noRipple = remember { MutableInteractionSource() }
    val close = { state.menu = null }

    val key = target.appKey
    val shortcuts = remember(key) { if (key != null) Apps.shortcuts(key) else emptyList() }
    val onHome = remember(key) { key != null && key in HomeStore.allKeys() }

    // Joylashuv: ikonka ostida, sig'masa — ustida
    val gap = with(density) { 10.dp.toPx() }
    val below = target.anchor.bottom + gap
    val y = if (below + menuSize.height < screenH - gap * 4) below else (target.anchor.top - gap - menuSize.height).coerceAtLeast(gap * 4)
    val x = (target.anchor.center.x - menuSize.width / 2f).coerceIn(gap, (screenW - menuSize.width - gap).coerceAtLeast(gap))

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = noRipple, indication = null) { close() }
    ) {
        GlassSurface(
            backdrop = home,
            cornerRadius = 22.dp,
            tint = Color(0x332A2D35),
            refraction = 18.dp,
            bezel = 16.dp,
            blur = 22.dp,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(260.dp)
                .onSizeChanged { menuSize = it }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                shortcuts.forEach { s ->
                    ShortcutRow(s) { close(); Apps.startShortcut(context, s) }
                }
                if (shortcuts.isNotEmpty()) HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                val item = target.item
                when {
                    target.folderId != null && key != null -> {
                        MenuRow(Icons.Rounded.DriveFileMove, "Papkadan chiqarish") {
                            close(); HomeStore.removeFromFolder(target.folderId, key)
                        }
                    }
                    target.fromDrawer && key != null -> {
                        if (!onHome) MenuRow(Icons.Rounded.AddToHomeScreen, "Bosh ekranga qo'shish") {
                            close(); HomeStore.addAppToHome(key)
                            android.widget.Toast.makeText(context, "Bosh ekranga qo'shildi", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    item is FolderItem -> {
                        MenuRow(Icons.Rounded.Edit, "Nomini o'zgartirish") { close(); state.openFolderId = item.id }
                        MenuRow(Icons.Rounded.GridView, "Papkani tarqatish") { close(); HomeStore.disbandFolder(item.id) }
                    }
                    item is WidgetItem -> {
                        MenuRow(Icons.Rounded.RemoveCircleOutline, "Vidjetni olib tashlash", danger = true) {
                            close(); WidgetHostManager.delete(item.appWidgetId); HomeStore.removeItem(item.id)
                        }
                    }
                    item != null -> {
                        MenuRow(Icons.Rounded.RemoveCircleOutline, "Bosh ekrandan olib tashlash") {
                            close(); HomeStore.removeItem(item.id)
                        }
                    }
                }
                if (key != null) {
                    MenuRow(Icons.Rounded.Info, "Ilova haqida") { close(); Apps.openDetails(key) }
                    if (!Apps.isSystemApp(key)) {
                        MenuRow(Icons.Rounded.DeleteOutline, "O'chirish", danger = true) { close(); Apps.uninstall(context, key) }
                    }
                }
                if (!target.fromDrawer && target.folderId == null) {
                    MenuRow(Icons.Rounded.EditNote, "Bosh ekranni tahrirlash") { close(); state.editMode = true }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(s: ShortcutInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(s.id) {
        Apps.shortcutIcon(s, context.resources.displayMetrics.densityDpi)
            ?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (s.shortLabel ?: s.longLabel ?: "").toString(), color = Oyna.Text, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (icon != null) Image(icon, null, Modifier.size(22.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) Oyna.Danger else Oyna.Text
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

/** Sudralayotgan element barmoq ostida "suzib" yuradi. */
@Composable
fun DragGhost(state: LauncherState, wall: Backdrop, settings: LauncherSettings) {
    val d = state.drag ?: return
    val density = LocalDensity.current
    val topLeft = d.pointer - d.grab
    Box(
        Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
            .graphicsLayerScale(1.08f),
        contentAlignment = Alignment.TopCenter,
    ) {
        val iconSize = settings.iconSizeDp.dp
        when (val item = d.item) {
            is AppItem -> AppIcon(item.key, iconSize, settings.iconShape, settings.iconStyle, null)
            is FolderItem -> FolderTile(item, iconSize, settings, null)
            is WidgetItem -> WidgetView(item, null, Modifier.fillMaxSize(), interactive = false)
        }
    }
}

private fun Modifier.graphicsLayerScale(s: Float): Modifier = this.graphicsLayer {
    scaleX = s
    scaleY = s
    alpha = 0.95f
}
