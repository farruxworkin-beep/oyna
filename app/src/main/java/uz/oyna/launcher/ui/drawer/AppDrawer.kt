package uz.oyna.launcher.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uz.oyna.launcher.data.AppInfo
import uz.oyna.launcher.data.Apps
import uz.oyna.launcher.data.LauncherSettings
import uz.oyna.launcher.ui.LauncherState
import uz.oyna.launcher.ui.MenuTarget
import uz.oyna.launcher.ui.Oyna
import uz.oyna.launcher.ui.glass.Backdrop
import uz.oyna.launcher.ui.glass.GlassSurface
import uz.oyna.launcher.ui.home.launcherGestures
import uz.oyna.launcher.ui.home.toAndroidRect
import uz.oyna.launcher.ui.icons.AppIcon

private fun norm(s: String) = s.lowercase()
    .replace('ʻ', '\'').replace('’', '\'').replace('`', '\'').replace('ʼ', '\'').replace('‘', '\'')

/** Barcha ilovalar (A–Z) va qidiruv — xira shisha ustida. */
@Composable
fun AppDrawer(state: LauncherState, home: Backdrop, apps: List<AppInfo>, settings: LauncherSettings) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()

    val filtered = remember(apps, query) {
        val q = norm(query.trim())
        if (q.isEmpty()) apps else apps.filter { norm(it.label).contains(q) || it.packageName.contains(q) }
    }

    LaunchedEffect(state.drawerSearchFocus) {
        if (state.drawerSearchFocus) {
            delay(250)
            runCatching { focus.requestFocus() }
        }
    }

    fun close() {
        focusManager.clearFocus()
        keyboard?.hide()
        state.drawerOpen = false
    }

    // Ro'yxat eng tepada turganda pastga tortilsa — yopiladi
    var pull by remember { mutableFloatStateOf(0f) }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    pull += available.y
                    if (pull > 260f) {
                        pull = 0f
                        close()
                    }
                } else if (available.y < 0f) pull = 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    GlassSurface(
        backdrop = home,
        cornerRadius = 0.dp,
        tint = Color(0x8C0B0D12),
        refraction = 0.dp,
        bezel = 1.dp,
        blur = 32.dp,
        rimAlpha = 0f,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Tepada: tutqich + qidiruv
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures { _, dy -> if (dy > 18f) close() }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(40.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                Spacer(Modifier.height(12.dp))
                GlassSurface(
                    backdrop = null,
                    cornerRadius = 22.dp,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(46.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = Oyna.Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Oyna.Text, fontSize = 16.sp),
                            cursorBrush = SolidColor(Oyna.Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                filtered.firstOrNull()?.let { Apps.launch(context, it.key); close() }
                            }),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Text("Ilovalarni qidirish", color = Oyna.Faint, fontSize = 16.sp)
                                    inner()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).clickable { query = "" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, "Tozalash", tint = Oyna.Muted, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                state = gridState,
                modifier = Modifier.fillMaxSize().nestedScroll(nested),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        if (query.isBlank()) "Barcha ilovalar · ${apps.size}" else "Natijalar · ${filtered.size}",
                        color = Oyna.Faint, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                items(filtered, key = { it.key }) { app ->
                    Column(
                        Modifier.launcherGestures(
                            key = "d-" + app.key,
                            editMode = false,
                            canDrag = false,
                            onTap = { b -> Apps.launch(context, app.key, b.toAndroidRect()); close() },
                            onLongPress = { b -> state.menu = MenuTarget(anchor = b, appKey = app.key, fromDrawer = true) },
                            onDragStart = { _, _, _ -> },
                            onDragMove = {},
                            onDragEnd = {},
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app.key, settings.iconSizeDp.dp.coerceAtMost(58.dp), settings.iconShape, settings.iconStyle, null)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label, style = Oyna.iconLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "«$query» topilmadi", color = Oyna.Muted, fontSize = 15.sp,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}
