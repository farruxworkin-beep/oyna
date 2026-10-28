package uz.oyna.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

// ===================== Modellar =====================

enum class WidgetKind(val title: String) {
    Clock("Soat"),
    Calendar("Kalendar"),
    Battery("Batareya"),
    System("Tizim vidjeti"),
}

sealed interface HomeItem {
    val id: Long
}

data class AppItem(override val id: Long, val key: String) : HomeItem
data class FolderItem(override val id: Long, val name: String, val keys: List<String>) : HomeItem
data class WidgetItem(
    override val id: Long,
    val kind: WidgetKind,
    val spanX: Int,
    val spanY: Int,
    val appWidgetId: Int = -1,
) : HomeItem

data class HomeLayout(val pages: List<List<HomeItem>>, val dock: List<HomeItem>)

enum class IconShape(val label: String) { Squircle("Skvirkl"), Rounded("Yumaloq kvadrat"), Circle("Doira") }
enum class IconStyle(val label: String) { Original("Asl rang"), Glass("Shisha"), Dark("Qorong'i") }

data class LauncherSettings(
    val columns: Int = 4,
    val iconSizeDp: Int = 60,
    val iconShape: IconShape = IconShape.Squircle,
    val iconStyle: IconStyle = IconStyle.Original,
    val showLabels: Boolean = true,
    val refraction: Float = 22f,
    val blur: Float = 3f,
    val tint: Float = 0.10f,
    val chroma: Boolean = true,
    val swipeDownNotifications: Boolean = false,
    val parallax: Boolean = true,
    val addNewApps: Boolean = true,
    val bannerDismissed: Boolean = false,
)

const val DOCK_MAX = 5

// ===================== Joylashtirish (grid packing) =====================

data class Placement(val item: HomeItem, val col: Int, val row: Int, val w: Int, val h: Int)

fun HomeItem.spanIn(cols: Int): Pair<Int, Int> = when (this) {
    is WidgetItem -> spanX.coerceIn(1, cols) to spanY.coerceAtLeast(1)
    else -> 1 to 1
}

/** Elementlarni chapdan-o'ngga, yuqoridan-pastga birinchi bo'sh joyga joylaydi. */
fun pack(items: List<HomeItem>, cols: Int): List<Placement> {
    val occupied = ArrayList<BooleanArray>()
    fun row(r: Int): BooleanArray {
        while (occupied.size <= r) occupied += BooleanArray(cols)
        return occupied[r]
    }
    fun free(c: Int, r: Int, w: Int, h: Int): Boolean {
        for (y in r until r + h) for (x in c until c + w) if (row(y)[x]) return false
        return true
    }
    val out = ArrayList<Placement>(items.size)
    for (item in items) {
        val (w, h) = item.spanIn(cols)
        var r = 0
        var placed = false
        while (!placed) {
            for (c in 0..(cols - w)) {
                if (free(c, r, w, h)) {
                    for (y in r until r + h) for (x in c until c + w) row(y)[x] = true
                    out += Placement(item, c, r, w, h)
                    placed = true
                    break
                }
            }
            r++
        }
    }
    return out
}

fun usedRows(placements: List<Placement>): Int = placements.maxOfOrNull { it.row + it.h } ?: 0

fun fits(items: List<HomeItem>, cols: Int, rows: Int): Boolean = usedRows(pack(items, cols)) <= rows

// ===================== Ombor =====================

/** Bosh ekran joylashuvi va sozlamalari. SharedPreferences'da JSON ko'rinishida saqlanadi. */
object HomeStore {
    private lateinit var prefs: SharedPreferences
    private var idCounter = System.currentTimeMillis()

    private val _layout = MutableStateFlow(HomeLayout(listOf(emptyList()), emptyList()))
    val layout: StateFlow<HomeLayout> = _layout

    private val _settings = MutableStateFlow(LauncherSettings())
    val settings: StateFlow<LauncherSettings> = _settings

    /** Ekran o'lchamidan hisoblanadi (UI o'rnatadi). */
    var rowsPerPage: Int = 6

    private var initialized = false

    fun init(context: Context) {
        prefs = context.getSharedPreferences("oyna", Context.MODE_PRIVATE)
        _settings.value = readSettings()
        val raw = prefs.getString("layout", null)
        if (raw != null) {
            runCatching { _layout.value = parseLayout(JSONObject(raw)) }
            initialized = true
        }
    }

    fun newId(): Long = ++idCounter

    // ---------- Sozlamalar ----------
    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt("columns", s.columns)
            .putInt("iconSize", s.iconSizeDp)
            .putString("iconShape", s.iconShape.name)
            .putString("iconStyle", s.iconStyle.name)
            .putBoolean("showLabels", s.showLabels)
            .putFloat("refraction", s.refraction)
            .putFloat("blur", s.blur)
            .putFloat("tint", s.tint)
            .putBoolean("chroma", s.chroma)
            .putBoolean("swipeDownNotif", s.swipeDownNotifications)
            .putBoolean("parallax", s.parallax)
            .putBoolean("addNewApps", s.addNewApps)
            .putBoolean("bannerDismissed", s.bannerDismissed)
            .apply()
    }

    private fun readSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            columns = prefs.getInt("columns", d.columns),
            iconSizeDp = prefs.getInt("iconSize", d.iconSizeDp),
            iconShape = runCatching { IconShape.valueOf(prefs.getString("iconShape", null) ?: "") }.getOrDefault(d.iconShape),
            iconStyle = runCatching { IconStyle.valueOf(prefs.getString("iconStyle", null) ?: "") }.getOrDefault(d.iconStyle),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            refraction = prefs.getFloat("refraction", d.refraction),
            blur = prefs.getFloat("blur", d.blur),
            tint = prefs.getFloat("tint", d.tint),
            chroma = prefs.getBoolean("chroma", d.chroma),
            swipeDownNotifications = prefs.getBoolean("swipeDownNotif", d.swipeDownNotifications),
            parallax = prefs.getBoolean("parallax", d.parallax),
            addNewApps = prefs.getBoolean("addNewApps", d.addNewApps),
            bannerDismissed = prefs.getBoolean("bannerDismissed", d.bannerDismissed),
        )
    }

    // ---------- Joylashuvni o'zgartirish ----------
    fun update(transform: (HomeLayout) -> HomeLayout) {
        val next = normalize(transform(_layout.value))
        _layout.value = next
        prefs.edit().putString("layout", layoutToJson(next).toString()).apply()
    }

    /** Bo'sh sahifalarni (oxirgisidan tashqari) olib tashlaydi, 1 ta ilovali papkani ilovaga aylantiradi. */
    private fun normalize(l: HomeLayout): HomeLayout {
        fun fix(items: List<HomeItem>): List<HomeItem> = items.mapNotNull {
            when {
                it is FolderItem && it.keys.isEmpty() -> null
                it is FolderItem && it.keys.size == 1 -> AppItem(it.id, it.keys.first())
                else -> it
            }
        }
        val pages = l.pages.map(::fix).filterIndexed { i, p -> p.isNotEmpty() || i == 0 }
        return HomeLayout(if (pages.isEmpty()) listOf(emptyList()) else pages, fix(l.dock).take(DOCK_MAX))
    }

    /** Ilovalar ro'yxati o'zgarganda: o'chirilganlarni olib tashlash, yangilarini qo'shish. */
    fun onAppsChanged(apps: List<AppInfo>, addedPackage: String?) {
        if (apps.isEmpty()) return
        if (!initialized) {
            createDefault(apps)
            initialized = true
            return
        }
        val valid = apps.map { it.key }.toHashSet()
        fun clean(items: List<HomeItem>): List<HomeItem> = items.mapNotNull {
            when (it) {
                is AppItem -> if (it.key in valid) it else null
                is FolderItem -> it.copy(keys = it.keys.filter { k -> k in valid })
                is WidgetItem -> it
            }
        }
        val cur = _layout.value
        var next = HomeLayout(cur.pages.map(::clean), clean(cur.dock))
        if (addedPackage != null && _settings.value.addNewApps) {
            val onHome = allKeys(next)
            apps.filter { it.packageName == addedPackage && it.key !in onHome }.forEach { a ->
                next = withAppAdded(next, a.key)
            }
        }
        if (next != cur) update { next }
    }

    fun allKeys(l: HomeLayout = _layout.value): Set<String> {
        val out = HashSet<String>()
        (l.pages.flatten() + l.dock).forEach {
            when (it) {
                is AppItem -> out += it.key
                is FolderItem -> out += it.keys
                else -> {}
            }
        }
        return out
    }

    private fun withAppAdded(l: HomeLayout, key: String): HomeLayout {
        val cols = _settings.value.columns
        val item = AppItem(newId(), key)
        val pages = l.pages.toMutableList()
        for (i in pages.indices) {
            if (fits(pages[i] + item, cols, rowsPerPage)) {
                pages[i] = pages[i] + item
                return l.copy(pages = pages)
            }
        }
        pages += listOf(item)
        return l.copy(pages = pages)
    }

    fun addAppToHome(key: String) = update { withAppAdded(it, key) }

    fun addWidget(page: Int, widget: WidgetItem): Int {
        var placedPage = page
        update { l ->
            val cols = _settings.value.columns
            val pages = l.pages.toMutableList()
            val start = page.coerceIn(0, pages.lastIndex)
            val target = (start until pages.size).firstOrNull { fits(pages[it] + widget, cols, rowsPerPage) }
            if (target != null) {
                pages[target] = listOf<HomeItem>(widget) + pages[target]
                if (!fits(pages[target], cols, rowsPerPage)) {
                    // sig'masa — oxirgi elementlarni keyingi sahifaga suramiz
                    val keep = pages[target].toMutableList()
                    val overflow = ArrayList<HomeItem>()
                    while (!fits(keep, cols, rowsPerPage) && keep.size > 1) overflow.add(0, keep.removeAt(keep.lastIndex))
                    pages[target] = keep
                    if (overflow.isNotEmpty()) pages.add(target + 1, overflow)
                }
                placedPage = target
            } else {
                pages += listOf<HomeItem>(widget)
                placedPage = pages.lastIndex
            }
            l.copy(pages = pages)
        }
        return placedPage
    }

    fun removeItem(id: Long) = update { l ->
        HomeLayout(l.pages.map { p -> p.filterNot { it.id == id } }, l.dock.filterNot { it.id == id })
    }

    fun findItem(id: Long): HomeItem? = (_layout.value.pages.flatten() + _layout.value.dock).firstOrNull { it.id == id }

    fun renameFolder(id: Long, name: String) = update { l ->
        fun r(items: List<HomeItem>) = items.map { if (it is FolderItem && it.id == id) it.copy(name = name) else it }
        HomeLayout(l.pages.map(::r), r(l.dock))
    }

    /** Ilovani papkadan chiqarib, papka turgan sahifaga qo'yadi. */
    fun removeFromFolder(folderId: Long, key: String) = update { l ->
        fun r(items: List<HomeItem>) = items.map {
            if (it is FolderItem && it.id == folderId) it.copy(keys = it.keys - key) else it
        }
        val pageIndex = l.pages.indexOfFirst { p -> p.any { it.id == folderId } }
        val pages = l.pages.map(::r).toMutableList()
        val removed = HomeLayout(pages, r(l.dock))
        if (pageIndex >= 0) {
            pages[pageIndex] = pages[pageIndex] + AppItem(newId(), key)
            removed.copy(pages = pages)
        } else withAppAdded(removed, key)
    }

    fun addPage() = update { it.copy(pages = it.pages + listOf(emptyList())) }

    /** Papkani tarqatadi: ilovalar papka o'rniga qo'yiladi, sig'maganlari keyingi sahifalarga. */
    fun disbandFolder(id: Long) = update { l ->
        val cols = _settings.value.columns
        val dockIdx = l.dock.indexOfFirst { it.id == id }
        if (dockIdx >= 0) {
            val f = l.dock[dockIdx] as? FolderItem ?: return@update l
            var next = l.copy(dock = l.dock.filterNot { it.id == id })
            f.keys.forEach { next = withAppAdded(next, it) }
            return@update next
        }
        val pages = l.pages.toMutableList()
        val pi = pages.indexOfFirst { p -> p.any { it.id == id } }
        if (pi < 0) return@update l
        val list = pages[pi].toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        val f = list[idx] as? FolderItem ?: return@update l
        list.removeAt(idx)
        val leftovers = ArrayList<String>()
        var insertAt = idx
        f.keys.forEach { k ->
            val candidate = list.toMutableList().apply { add(insertAt, AppItem(0, k)) }
            if (fits(candidate, cols, rowsPerPage)) {
                list.add(insertAt, AppItem(newId(), k))
                insertAt++
            } else leftovers += k
        }
        pages[pi] = list
        var next = l.copy(pages = pages)
        leftovers.forEach { next = withAppAdded(next, it) }
        next
    }

    // ---------- Birinchi ishga tushirish ----------
    private fun createDefault(apps: List<AppInfo>) {
        val dockKeys = Apps.defaultDockKeys().take(4)
        val rest = apps.map { it.key }.filterNot { it in dockKeys }
        val page0 = buildList<HomeItem> {
            add(WidgetItem(newId(), WidgetKind.Clock, 4, 2))
            rest.take(12).forEach { add(AppItem(newId(), it)) }
        }
        val page1 = buildList<HomeItem> {
            add(WidgetItem(newId(), WidgetKind.Calendar, 2, 2))
            add(WidgetItem(newId(), WidgetKind.Battery, 2, 2))
            rest.drop(12).take(12).forEach { add(AppItem(newId(), it)) }
        }
        update {
            HomeLayout(
                pages = listOf(page0, page1).filter { it.isNotEmpty() },
                dock = dockKeys.map { AppItem(newId(), it) },
            )
        }
    }

    // ---------- JSON ----------
    private fun itemToJson(i: HomeItem): JSONObject = when (i) {
        is AppItem -> JSONObject().put("t", "a").put("id", i.id).put("k", i.key)
        is FolderItem -> JSONObject().put("t", "f").put("id", i.id).put("n", i.name).put("k", JSONArray(i.keys))
        is WidgetItem -> JSONObject().put("t", "w").put("id", i.id).put("kind", i.kind.name)
            .put("x", i.spanX).put("y", i.spanY).put("aw", i.appWidgetId)
    }

    private fun itemFromJson(o: JSONObject): HomeItem? = try {
        val id = o.getLong("id")
        idCounter = maxOf(idCounter, id)
        when (o.getString("t")) {
            "a" -> AppItem(id, o.getString("k"))
            "f" -> {
                val arr = o.getJSONArray("k")
                FolderItem(id, o.optString("n", "Papka"), (0 until arr.length()).map { arr.getString(it) })
            }
            "w" -> WidgetItem(id, WidgetKind.valueOf(o.getString("kind")), o.getInt("x"), o.getInt("y"), o.optInt("aw", -1))
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun layoutToJson(l: HomeLayout): JSONObject {
        val pages = JSONArray()
        l.pages.forEach { p -> pages.put(JSONArray().apply { p.forEach { put(itemToJson(it)) } }) }
        val dock = JSONArray().apply { l.dock.forEach { put(itemToJson(it)) } }
        return JSONObject().put("pages", pages).put("dock", dock)
    }

    private fun parseLayout(o: JSONObject): HomeLayout {
        val pagesArr = o.getJSONArray("pages")
        val pages = (0 until pagesArr.length()).map { i ->
            val p = pagesArr.getJSONArray(i)
            (0 until p.length()).mapNotNull { itemFromJson(p.getJSONObject(it)) }
        }
        val dockArr = o.optJSONArray("dock") ?: JSONArray()
        val dock = (0 until dockArr.length()).mapNotNull { itemFromJson(dockArr.getJSONObject(it)) }
        return HomeLayout(pages.ifEmpty { listOf(emptyList()) }, dock)
    }
}
