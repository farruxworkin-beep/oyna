package uz.oyna.launcher.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Ikonka/papka/vidjet uchun yagona imo-ishora:
 *  • bosish → onTap
 *  • uzoq bosish (qimirlamasdan qo'yib yuborish) → onLongPress (menyu)
 *  • uzoq bosib surish (tahrirlash rejimida — darhol surish) → onDragStart/onDragMove/onDragEnd
 *  • oddiy surish → hech narsa qilmaydi (sahifa almashtirish/svayp ota-elementga o'tadi)
 */
@Composable
fun Modifier.launcherGestures(
    key: Any,
    editMode: Boolean,
    canDrag: Boolean,
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
    onDragStart: (pointerRoot: Offset, grab: Offset, bounds: Rect) -> Unit,
    onDragMove: (pointerRoot: Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDragMove)
    val end by rememberUpdatedState(onDragEnd)
    val edit by rememberUpdatedState(editMode)
    val draggable by rememberUpdatedState(canDrag)

    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                val slop = viewConfiguration.touchSlop
                // 1 = qo'yib yuborildi (bosish), 2 = surildi, 3 = uzoq bosildi
                val outcome: Int = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var r = 0
                    while (r == 0) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) {
                            r = 1
                        } else if ((ch.position - down.position).getDistance() > slop) {
                            r = 2
                        }
                    }
                    r
                } ?: 3

                when {
                    outcome == 1 -> tap(bounds)
                    outcome == 3 || (outcome == 2 && edit && draggable) -> {
                        if (outcome == 3) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        var dragging = false
                        if (outcome == 2) {
                            dragging = true
                            start(bounds.topLeft + down.position, down.position, bounds)
                        }
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                ch.consume()
                                break
                            }
                            val rootPos = bounds.topLeft + ch.position
                            if (!dragging && draggable && (ch.position - down.position).getDistance() > slop) {
                                dragging = true
                                start(rootPos, down.position, bounds)
                            }
                            if (dragging) {
                                move(rootPos)
                                ch.consume()
                            }
                        }
                        if (dragging) end() else long(bounds)
                    }
                    else -> Unit // oddiy svayp — ota-elementlar hal qiladi
                }
            }
        }
}
