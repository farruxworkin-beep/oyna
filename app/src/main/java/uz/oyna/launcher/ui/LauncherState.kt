package uz.oyna.launcher.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import uz.oyna.launcher.data.*

sealed interface DropTarget {
    data class Insert(val page: Int, val index: Int) : DropTarget
    data class Dock(val index: Int) : DropTarget
    data class Merge(val targetId: Long) : DropTarget
}

/** Sudrab olib ketilayotgan element. fromPage = -1 bo'lsa — dokdan. */
data class DragState(
    val item: HomeItem,
    val fromPage: Int,
    val pointer: Offset,
    val grab: Offset,
    val size: Size,
)

/** Uzoq bosilganda chiqadigan menyu. */
data class MenuTarget(
    val anchor: Rect,
    val appKey: String? = null,
    val item: HomeItem? = null,
    val folderId: Long? = null,
    val fromDrawer: Boolean = false,
)

@Stable
class LauncherState {
    // ---------- Oynalar ----------
    var editMode by mutableStateOf(false)
    var drawerOpen by mutableStateOf(false)
    var drawerSearchFocus by mutableStateOf(false)
    var controlOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var widgetPickerOpen by mutableStateOf(false)
    var openFolderId by mutableStateOf<Long?>(null)
    var menu by mutableStateOf<MenuTarget?>(null)

    var isDefaultLauncher by mutableStateOf(true)

    // ---------- Sahifalar ----------
    var currentPage by mutableIntStateOf(0)
    /** Parallaks uchun: joriy sahifa + siljish ulushi. */
    var pageScroll by mutableFloatStateOf(0f)
    var scrollToPage: (Int) -> Unit = {}

    // ---------- Setka geometriyasi (UI o'lchaydi) ----------
    var gridOrigin by mutableStateOf(Offset.Zero)
    var cellW by mutableFloatStateOf(1f)
    var cellH by mutableFloatStateOf(1f)
    var cols by mutableIntStateOf(4)
    var rows by mutableIntStateOf(6)
    var iconPx by mutableFloatStateOf(150f)
    var iconCenterInCell by mutableFloatStateOf(80f)
    var screenWidth by mutableFloatStateOf(1080f)
    var edgePx by mutableFloatStateOf(70f)
    var dockRect by mutableStateOf(Rect.Zero)
    val dockCenters = mutableStateMapOf<Long, Offset>()

    // ---------- Sudrash ----------
    var drag by mutableStateOf<DragState?>(null)
    var hover by mutableStateOf<DropTarget?>(null)
    private var lastTarget: DropTarget? = null
    private var stableTicks = 0
    private var edgeTicks = 0

    val anyOverlay: Boolean
        get() = drawerOpen || controlOpen || settingsOpen || widgetPickerOpen || openFolderId != null || menu != null

    /** Orqaga tugmasi: launcher hech qachon yopilmaydi. */
    fun back() {
        when {
            menu != null -> menu = null
            widgetPickerOpen -> widgetPickerOpen = false
            settingsOpen -> settingsOpen = false
            openFolderId != null -> openFolderId = null
            drawerOpen -> drawerOpen = false
            controlOpen -> controlOpen = false
            editMode -> editMode = false
        }
    }

    /** "Home" tugmasi bosilganda: hammasini yopib, birinchi sahifaga qaytadi. */
    fun onHomePressed() {
        if (anyOverlay || editMode) {
            menu = null; widgetPickerOpen = false; settingsOpen = false
            openFolderId = null; drawerOpen = false; controlOpen = false; editMode = false
        } else {
            scrollToPage(0)
        }
    }

    fun openDrawer(search: Boolean) {
        drawerSearchFocus = search
        drawerOpen = true
    }

    // ---------- Sudrash mantiqi ----------
    fun beginDrag(item: HomeItem, fromPage: Int, pointer: Offset, grab: Offset, size: Size) {
        menu = null
        drag = DragState(item, fromPage, pointer, grab, size)
        lastTarget = null
        stableTicks = 0
        edgeTicks = 0
    }

    fun dragTo(pointer: Offset) {
        drag = drag?.copy(pointer = pointer)
    }

    fun cancelDrag() {
        drag = null
        hover = null
    }

    fun computeTarget(p: Offset, l: HomeLayout): DropTarget? {
        val d = drag ?: return null
        // --- Dok ---
        if (dockRect.contains(p)) {
            val others = l.dock.filterNot { it.id == d.item.id }
            if (d.item is AppItem) {
                others.forEach { o ->
                    val c = dockCenters[o.id] ?: return@forEach
                    if ((p - c).getDistance() < iconPx * 0.42f) return DropTarget.Merge(o.id)
                }
            }
            val idx = others.count { (dockCenters[it.id]?.x ?: Float.MAX_VALUE) < p.x }
            return DropTarget.Dock(idx)
        }
        // --- Setka ---
        val gx = p.x - gridOrigin.x
        val gy = p.y - gridOrigin.y
        if (gx < 0f || gy < 0f || gx > cols * cellW || gy > rows * cellH) return null
        val col = (gx / cellW).toInt().coerceIn(0, cols - 1)
        val row = (gy / cellH).toInt().coerceIn(0, rows - 1)
        val page = currentPage
        val items = l.pages.getOrNull(page)?.filterNot { it.id == d.item.id } ?: emptyList()
        val placements = pack(items, cols)
        val hit = placements.firstOrNull { col in it.col until it.col + it.w && row in it.row until it.row + it.h }
        if (hit != null) {
            if (d.item is AppItem && hit.item !is WidgetItem) {
                val center = gridOrigin + Offset((hit.col + 0.5f) * cellW, hit.row * cellH + iconCenterInCell)
                if ((p - center).getDistance() < iconPx * 0.42f) return DropTarget.Merge(hit.item.id)
            }
            return DropTarget.Insert(page, items.indexOf(hit.item))
        }
        val next = placements.firstOrNull { it.row > row || (it.row == row && it.col > col) }
        return DropTarget.Insert(page, if (next == null) items.size else items.indexOf(next.item))
    }

    /** Har ~120 ms da chaqiriladi: jonli tartiblash va chetga olib borilganda sahifani almashtirish. */
    fun tick() {
        val d = drag ?: return
        val l = HomeStore.layout.value
        val target = computeTarget(d.pointer, l)
        hover = target

        if (target == lastTarget) stableTicks++ else {
            stableTicks = 0
            lastTarget = target
        }
        // Shu sahifa ichida jonli tartiblash
        if (target is DropTarget.Insert && stableTicks == 2 && d.fromPage == target.page) {
            val page = l.pages.getOrNull(target.page) ?: return
            val curIndex = page.indexOfFirst { it.id == d.item.id }
            if (curIndex >= 0 && curIndex != target.index) {
                HomeStore.update { lay ->
                    val pages = lay.pages.toMutableList()
                    val list = pages[target.page].toMutableList()
                    val it = list.removeAt(curIndex)
                    list.add(target.index.coerceIn(0, list.size), it)
                    pages[target.page] = list
                    lay.copy(pages = pages)
                }
            }
        }
        // Chetga olib borilsa — qo'shni sahifaga o'tish
        val x = d.pointer.x
        val nearLeft = x < edgePx
        val nearRight = x > screenWidth - edgePx
        if ((nearLeft || nearRight) && !dockRect.contains(d.pointer)) {
            edgeTicks++
            if (edgeTicks >= 6) {
                edgeTicks = 0
                if (nearLeft && currentPage > 0) {
                    scrollToPage(currentPage - 1)
                } else if (nearRight) {
                    val pages = l.pages
                    if (currentPage == pages.lastIndex) {
                        val others = pages[currentPage].filterNot { it.id == d.item.id }
                        if (others.isNotEmpty()) {
                            HomeStore.addPage()
                            scrollToPage(currentPage + 1)
                        }
                    } else scrollToPage(currentPage + 1)
                }
            }
        } else edgeTicks = 0
    }

    /** Qo'yib yuborilganda. Muvaffaqiyatsiz bo'lsa false qaytaradi (masalan, joy yo'q). */
    fun drop(): Boolean {
        val d = drag ?: return false
        val l = HomeStore.layout.value
        val target = computeTarget(d.pointer, l)
        drag = null
        hover = null
        if (target == null) return true
        val next = applyDrop(l, d.item, target, HomeStore.settings.value.columns, HomeStore.rowsPerPage) ?: return false
        HomeStore.update { next }
        return true
    }
}

/** Elementni joyidan olib, yangi joyga qo'yadi. Sig'masa null. */
fun applyDrop(l: HomeLayout, item: HomeItem, t: DropTarget, cols: Int, rows: Int): HomeLayout? {
    val pages = l.pages.map { p -> p.filterNot { it.id == item.id } }.toMutableList()
    val dock = l.dock.filterNot { it.id == item.id }.toMutableList()
    when (t) {
        is DropTarget.Insert -> {
            while (t.page >= pages.size) pages += listOf(emptyList())
            val p = pages[t.page].toMutableList()
            p.add(t.index.coerceIn(0, p.size), item)
            if (!fits(p, cols, rows)) return null
            pages[t.page] = p
        }
        is DropTarget.Dock -> {
            if (item is WidgetItem) return null
            if (dock.size >= DOCK_MAX) return null
            dock.add(t.index.coerceIn(0, dock.size), item)
        }
        is DropTarget.Merge -> {
            val key = (item as? AppItem)?.key ?: return null
            fun merge(list: MutableList<HomeItem>): Boolean {
                val i = list.indexOfFirst { it.id == t.targetId }
                if (i < 0) return false
                list[i] = when (val tg = list[i]) {
                    is AppItem -> {
                        val c1 = Apps.get(tg.key)?.category ?: -1
                        val c2 = Apps.get(key)?.category ?: -1
                        FolderItem(HomeStore.newId(), if (c1 == c2) Apps.categoryName(c1) else "Papka", listOf(tg.key, key))
                    }
                    is FolderItem -> if (key in tg.keys) tg else tg.copy(keys = tg.keys + key)
                    is WidgetItem -> return false
                }
                return true
            }
            var ok = merge(dock)
            if (!ok) {
                for (pi in pages.indices) {
                    val m = pages[pi].toMutableList()
                    if (merge(m)) {
                        pages[pi] = m
                        ok = true
                        break
                    }
                }
            }
            if (!ok) return null
        }
    }
    return HomeLayout(pages, dock)
}
