package uz.oyna.launcher.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.oyna.launcher.data.*
import uz.oyna.launcher.system.SystemControls
import uz.oyna.launcher.system.WidgetHostManager
import uz.oyna.launcher.ui.DropTarget
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.MenuTarget
import uz.oyna.launcher.ui.Oyna
import uz.oyna.launcher.ui.glass.Backdrop
import uz.oyna.launcher.ui.glass.GlassSurface
import uz.oyna.launcher.ui.icons.AppIcon
import uz.oyna.launcher.ui.icons.cornerFraction
import uz.oyna.launcher.ui.widgets.WidgetView
import kotlin.math.roundToInt

private val GridPadding = 12.dp

@Composable
fun HomeScreen(
    state: LauncherState,
    wall: Backdrop,
    layout: HomeLayout,
    settings: LauncherSettings,
    isDefaultLauncher: Boolean,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pager = rememberPagerState(pageCount = { layout.pages.size })

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { state.currentPage = it }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { state.pageScroll = it }
    }
    SideEffect {
        state.scrollToPage = { p -> scope.launch { pager.animateScrollToPage(p.coerceIn(0, (layout.pages.size - 1).coerceAtLeast(0))) } }
    }
    // Sudrash paytida: jonli tartiblash va sahifa almashtirish
    val dragging = state.drag != null
    LaunchedEffect(dragging) {
        while (dragging) {
            state.tick()
            delay(120)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // ---------- Yuqori qism: tahrirlash paneli yoki banner ----------
        AnimatedVisibility(visible = state.editMode, enter = fadeIn(), exit = fadeOut()) {
            EditBar(state, wall)
        }
        if (!state.editMode && !isDefaultLauncher && !settings.bannerDismissed) {
            DefaultLauncherBanner(wall)
        }

        // ---------- Sahifalar ----------
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cols = settings.columns
            val rows = (maxHeight / 104.dp).toInt().coerceIn(4, 7)
            val gridW = maxWidth - GridPadding * 2
            val cellW = gridW / cols
            val cellH = maxHeight / rows
            val iconSize = settings.iconSizeDp.dp.coerceAtMost(cellW - 10.dp)

            SideEffect {
                HomeStore.rowsPerPage = rows
                state.cols = cols
                state.rows = rows
                with(density) {
                    state.cellW = cellW.toPx()
                    state.cellH = cellH.toPx()
                    state.iconPx = iconSize.toPx()
                    state.iconCenterInCell = (8.dp + iconSize / 2).toPx()
                    state.edgePx = 28.dp.toPx()
                }
                WidgetHostManager.cellWidthDp = cellW.value
                WidgetHostManager.cellHeightDp = cellH.value
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = layout.pages.size,
                userScrollEnabled = !dragging,
                key = { it },
            ) { page ->
                val items = layout.pages.getOrElse(page) { emptyList() }
                PageGrid(state, wall, page, items, settings, cellW, cellH, iconSize, isSettled = pager.settledPage == page)
            }
        }

        // ---------- Sahifa nuqtalari + qidiruv ----------
        PageDotsAndSearch(state, wall, layout.pages.size, pager.currentPage)

        // ---------- Dok ----------
        Dock(state, wall, layout.dock, settings)
    }
}

@Composable
private fun PageGrid(
    state: LauncherState,
    wall: Backdrop,
    page: Int,
    items: List<HomeItem>,
    settings: LauncherSettings,
    cellW: Dp,
    cellH: Dp,
    iconSize: Dp,
    isSettled: Boolean,
) {
    val placements = remember(items, settings.columns) { pack(items, settings.columns) }
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = GridPadding)
            .onGloballyPositioned { if (isSettled) state.gridOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { state.editMode = true },
                    onTap = { if (state.editMode) state.editMode = false },
                )
            }
    ) {
        placements.forEach { pl ->
            key(pl.item.id) {
                GridCell(state, wall, page, pl, settings, cellW, cellH, iconSize)
            }
        }
    }
}

@Composable
private fun GridCell(
    state: LauncherState,
    wall: Backdrop,
    page: Int,
    pl: Placement,
    settings: LauncherSettings,
    cellW: Dp,
    cellH: Dp,
    iconSize: Dp,
) {
    val density = LocalDensity.current
    val target = with(density) { IntOffset((cellW * pl.col).roundToPx(), (cellH * pl.row).roundToPx()) }
    val offset by animateIntOffsetAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "cell")
    val item = pl.item
    val isDragged = state.drag?.item?.id == item.id
    val mergeHover = (state.hover as? DropTarget.Merge)?.targetId == item.id

    Box(
        Modifier
            .offset { offset }
            .size(cellW * pl.w, cellH * pl.h)
            .graphicsLayer { alpha = if (isDragged) 0f else 1f }
    ) {
        HomeItemView(
            state = state,
            wall = wall,
            item = item,
            fromPage = page,
            settings = settings,
            iconSize = iconSize,
            highlight = mergeHover,
            showLabel = settings.showLabels,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Bosh ekrandagi bitta element (ilova, papka yoki vidjet) — imo-ishoralar bilan. */
@Composable
fun HomeItemView(
    state: LauncherState,
    wall: Backdrop,
    item: HomeItem,
    fromPage: Int,
    settings: LauncherSettings,
    iconSize: Dp,
    highlight: Boolean,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val jiggle = rememberJiggle(state.editMode, item.id)
    val scale by animateFloatAsState(if (highlight) 1.18f else 1f, label = "hl")

    val gestures = Modifier.launcherGestures(
        key = item.id,
        editMode = state.editMode,
        canDrag = true,
        onTap = { bounds ->
            if (state.editMode) return@launcherGestures
            when (item) {
                is AppItem -> Apps.launch(context, item.key, bounds.toAndroidRect())
                is FolderItem -> state.openFolderId = item.id
                is WidgetItem -> Unit
            }
        },
        onLongPress = { bounds ->
            state.menu = MenuTarget(anchor = bounds, appKey = (item as? AppItem)?.key, item = item)
        },
        onDragStart = { p, grab, bounds ->
            state.beginDrag(item, fromPage, p, grab, Size(bounds.width, bounds.height))
        },
        onDragMove = { state.dragTo(it) },
        onDragEnd = {
            if (!state.drop()) {
                android.widget.Toast.makeText(context, "Bu yerda joy yo'q", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
    )

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        when (item) {
            is WidgetItem -> {
                Box(
                    Modifier.fillMaxSize().padding(6.dp).rotate(jiggle * 0.4f).then(gestures)
                ) {
                    WidgetView(item, wall, Modifier.fillMaxSize(), interactive = !state.editMode)
                }
            }
            else -> {
                Column(
                    Modifier.padding(top = 8.dp).rotate(jiggle).scale(scale).then(gestures),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (item) {
                        is AppItem -> AppIcon(item.key, iconSize, settings.iconShape, settings.iconStyle, wall)
                        is FolderItem -> FolderTile(item, iconSize, settings, wall)
                        else -> Unit
                    }
                    if (showLabel) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = when (item) {
                                is AppItem -> Apps.get(item.key)?.label ?: ""
                                is FolderItem -> item.name
                                else -> ""
                            },
                            style = Oyna.iconLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = iconSize + 22.dp),
                        )
                    }
                }
            }
        }
        if (state.editMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = if (item is WidgetItem) 0.dp else 6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xE6303338))
                    .clickable {
                        if (item is WidgetItem) WidgetHostManager.delete(item.appWidgetId)
                        HomeStore.removeItem(item.id)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Remove, "Olib tashlash", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

fun androidx.compose.ui.geometry.Rect.toAndroidRect() =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

/** Tahrirlash rejimidagi "titrash" (iOS dagi kabi). */
@Composable
private fun rememberJiggle(active: Boolean, id: Long): Float {
    if (!active) return 0f
    val t = rememberInfiniteTransition(label = "jiggle")
    val phase = if (id % 2L == 0L) 1f else -1f
    val v by t.animateFloat(
        initialValue = -1.6f * phase,
        targetValue = 1.6f * phase,
        animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
        label = "j",
    )
    return v
}

@Composable
fun FolderTile(folder: FolderItem, size: Dp, settings: LauncherSettings, backdrop: Backdrop?) {
    val pad = size * 0.14f
    val gap = size * 0.05f
    val mini = (size - pad * 2 - gap * 2) / 3
    GlassSurface(
        backdrop = backdrop,
        cornerRadius = size * settings.iconShape.cornerFraction(),
        refraction = size * 0.18f,
        bezel = size * 0.2f,
        modifier = Modifier.size(size),
    ) {
        Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
            folder.keys.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { k ->
                        val style = if (settings.iconStyle == IconStyle.Glass) IconStyle.Original else settings.iconStyle
                        AppIcon(k, mini, settings.iconShape, style, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDotsAndSearch(state: LauncherState, wall: Backdrop, pageCount: Int, current: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (pageCount > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                repeat(pageCount) { i ->
                    Box(
                        Modifier.size(7.dp).clip(CircleShape)
                            .background(if (i == current) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
        GlassSurface(
            backdrop = wall,
            cornerRadius = 19.dp,
            refraction = 10.dp,
            bezel = 12.dp,
            modifier = Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).clickable { state.openDrawer(search = true) },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Oyna.Text, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Qidiruv", color = Oyna.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Dock(state: LauncherState, wall: Backdrop, dock: List<HomeItem>, settings: LauncherSettings) {
    val iconSize = settings.iconSizeDp.dp.coerceAtMost(64.dp)
    val hoverDock = state.hover is DropTarget.Dock
    GlassSurface(
        backdrop = wall,
        cornerRadius = 34.dp,
        refraction = 26.dp,
        bezel = 22.dp,
        blur = 4.dp,
        modifier = Modifier
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(iconSize + 30.dp)
            .onGloballyPositioned { state.dockRect = it.boundsInRoot() },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dock.isEmpty()) {
                Text(
                    if (hoverDock) "Shu yerga qo'ying" else "Ilovani shu yerga suring",
                    color = Oyna.Muted, fontSize = 13.sp,
                )
            }
            dock.forEach { item ->
                key(item.id) {
                    Box(
                        Modifier.onGloballyPositioned { state.dockCenters[item.id] = it.boundsInRoot().center }
                            .graphicsLayer { alpha = if (state.drag?.item?.id == item.id) 0f else 1f }
                    ) {
                        HomeItemView(
                            state = state,
                            wall = wall,
                            item = item,
                            fromPage = -1,
                            settings = settings,
                            iconSize = iconSize,
                            highlight = (state.hover as? DropTarget.Merge)?.targetId == item.id,
                            showLabel = false,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditBar(state: LauncherState, wall: Backdrop) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillButton(wall, Icons.Rounded.Add, "Vidjet") { state.widgetPickerOpen = true }
        PillButton(wall, Icons.Rounded.Tune, "Sozlamalar") { state.settingsOpen = true }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Oyna.Accent)
                .clickable { state.editMode = false }.padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Tayyor", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
    }
}

@Composable
fun PillButton(backdrop: Backdrop?, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    GlassSurface(
        backdrop = backdrop,
        cornerRadius = 20.dp,
        refraction = 10.dp,
        bezel = 12.dp,
        modifier = Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Oyna.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Oyna.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun DefaultLauncherBanner(wall: Backdrop) {
    val context = LocalContext.current
    GlassSurface(
        backdrop = wall,
        cornerRadius = 22.dp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Oyna — standart bosh ekran emas", color = Oyna.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Home tugmasi Oyna'ni ochishi uchun o'rnating", color = Oyna.Muted, fontSize = 12.sp)
            }
            Box(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Oyna.Accent)
                    .clickable { SystemControls.openHomeSettings(context) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("O'rnatish", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable {
                    HomeStore.updateSettings { it.copy(bannerDismissed = true) }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Yopish", tint = Oyna.Muted, modifier = Modifier.size(18.dp)) }
        }
    }
}
