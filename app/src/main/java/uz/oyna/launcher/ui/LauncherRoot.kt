package uz.oyna.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.oyna.launcher.data.Apps
import uz.oyna.launcher.data.FolderItem
import uz.oyna.launcher.data.HomeStore
import uz.oyna.launcher.data.Wallpaper
import uz.oyna.launcher.system.SystemControls
import uz.oyna.launcher.ui.control.ControlCenter
import uz.oyna.launcher.ui.drawer.AppDrawer
import uz.oyna.launcher.ui.glass.GlassConfig
import uz.oyna.launcher.ui.glass.LocalGlassConfig
import uz.oyna.launcher.ui.glass.backdropSource
import uz.oyna.launcher.ui.glass.rememberBackdrop
import uz.oyna.launcher.ui.home.ContextMenu
import uz.oyna.launcher.ui.home.DragGhost
import uz.oyna.launcher.ui.home.FolderOverlay
import uz.oyna.launcher.ui.home.HomeScreen
import uz.oyna.launcher.ui.settings.SettingsScreen
import uz.oyna.launcher.ui.widgets.WidgetPicker

@Composable
fun LauncherRoot(state: LauncherState) {
    val layout by HomeStore.layout.collectAsStateWithLifecycle()
    val settings by HomeStore.settings.collectAsStateWithLifecycle()
    val apps by Apps.apps.collectAsStateWithLifecycle()
    val wallpaper by Wallpaper.image.collectAsStateWithLifecycle()

    val wall = rememberBackdrop()   // faqat fon rasmi
    val home = rememberBackdrop()   // butun bosh ekran (panellar uchun)

    val glass = GlassConfig(
        refractionScale = settings.refraction / 22f,
        extraBlur = settings.blur - 3f,
        tint = settings.tint,
        chroma = settings.chroma,
    )
    val currentSettings by rememberUpdatedState(settings)

    // Launcher hech qachon "orqaga" bilan yopilmaydi
    BackHandler(enabled = true) { state.back() }

    CompositionLocalProvider(LocalGlassConfig provides glass) {
        Box(Modifier.fillMaxSize().background(Oyna.Bg)) {
            Box(Modifier.fillMaxSize().backdropSource(home)) {
                Box(Modifier.fillMaxSize().backdropSource(wall)) {
                    WallpaperLayer(wallpaper, state, layout.pages.size, settings.parallax)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (state.drag == null && !state.editMode && !state.anyOverlay) {
                                        if (total < -140f) state.openDrawer(search = false)
                                        else if (total > 140f) {
                                            if (currentSettings.swipeDownNotifications) SystemControls.expandNotifications()
                                            else state.controlOpen = true
                                        }
                                    }
                                },
                            ) { _, dy -> total += dy }
                        }
                ) {
                    HomeScreen(state, wall, layout, settings, state.isDefaultLauncher)
                }
            }

            DragGhost(state, wall, settings)

            AnimatedVisibility(
                visible = state.drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) { AppDrawer(state, home, apps, settings) }

            AnimatedVisibility(
                visible = state.controlOpen,
                enter = slideInVertically { -it / 4 } + fadeIn(),
                exit = slideOutVertically { -it / 4 } + fadeOut(),
            ) { ControlCenter(state, home) }

            val folder = state.openFolderId?.let { id ->
                (layout.pages.flatten() + layout.dock).firstOrNull { it.id == id } as? FolderItem
            }
            AnimatedVisibility(
                visible = folder != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
            ) {
                // Animatsiya tugaguncha oxirgi papka ko'rinib tursin
                var last by remember { mutableStateOf(folder) }
                if (folder != null) last = folder
                last?.let { FolderOverlay(state, home, it, settings) }
            }

            AnimatedVisibility(visible = state.settingsOpen, enter = fadeIn(), exit = fadeOut()) {
                SettingsScreen(state, home, settings)
            }
            AnimatedVisibility(visible = state.widgetPickerOpen, enter = fadeIn(), exit = fadeOut()) {
                WidgetPicker(state, home)
            }

            val menu = state.menu
            AnimatedVisibility(visible = menu != null, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut()) {
                var last by remember { mutableStateOf(menu) }
                if (menu != null) last = menu
                last?.let { ContextMenu(state, home, it) }
            }
        }
    }
}

@Composable
private fun WallpaperLayer(image: ImageBitmap?, state: LauncherState, pages: Int, parallax: Boolean) {
    val fraction = if (parallax && pages > 1) (state.pageScroll / (pages - 1)).coerceIn(0f, 1f) - 0.5f else 0f
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val s = if (parallax) 1.12f else 1f
                scaleX = s
                scaleY = s
                translationX = -fraction * size.width * 0.10f
            },
        )
    } else {
        DefaultWallpaper(Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.12f
            scaleY = 1.12f
            translationX = -fraction * size.width * 0.10f
        })
    }
}

/** Standart fon: to'q ko'k-binafsha-olovrang dog'lar (shisha effekti yaxshi ko'rinishi uchun). */
@Composable
private fun DefaultWallpaper(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF1A2440),
                0.5f to Color(0xFF0D0F16),
                1f to Color(0xFF2A160F),
            )
        )
        val blobs = listOf(
            Triple(Offset(size.width * 0.15f, size.height * 0.12f), size.width * 0.9f, Color(0xFF3B5BDB)),
            Triple(Offset(size.width * 0.95f, size.height * 0.45f), size.width * 0.8f, Color(0xFF7C3AED)),
            Triple(Offset(size.width * 0.3f, size.height * 0.92f), size.width * 0.95f, Color(0xFFEA580C)),
            Triple(Offset(size.width * 0.85f, size.height * 0.05f), size.width * 0.5f, Color(0xFF06B6D4)),
        )
        blobs.forEach { (c, r, color) ->
            drawCircle(
                Brush.radialGradient(0f to color.copy(alpha = 0.55f), 1f to Color.Transparent, center = c, radius = r),
                radius = r, center = c,
            )
        }
    }
}
