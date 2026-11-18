package com.yuno

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PULL_BIAS = 1.5f
private val PULL_LIFT_EASE = 72.dp
/** How far a slow swipe down must travel before it closes the video. A flick closes sooner. */
private val DISMISS_DISTANCE = 56.dp
private const val DISMISS_FLICK = 1400f
/** A sideways swipe acts once it passes this share of the bar's width, or on a flick. */
private const val SIDE_ACT_SHARE = 0.3f
private const val SIDE_FLICK = 1200f
/** Swiping left only pauses, so the bar moves no further than this before it springs back. */
private val PAUSE_PULL_MAX = 96.dp
/** Behind the sliding bar: green for pause/play, red for close, a white icon by the edge. */
private val SWIPE_GREEN = Color(0xFF34C759)
private val SWIPE_RED = Color(0xFFFF3B30)
private val SWIPE_GLYPH = 24.dp
/** How far in from the bar's edge the icon settles. */
private val SWIPE_ICON_INSET = 30.dp
/** After a close the bar stays where it left (off to the side, or dropped) while it exits. */
private const val CLOSED_HOLD_MS = 900L

private fun pullLift(pulled: Float, ease: Float): Float =
    if (pulled <= 0f || ease <= 0f) 0f else 0.5f * 2f * ease * (sqrt(1f + pulled / ease) - 1f)

@Composable
fun Modifier.playerDockArt(dock: PlayerDock?, corner: Dp): Modifier {
    if (dock == null) return this
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    androidx.compose.runtime.DisposableEffect(dock) {
        onDispose { placed[0]?.let(dock::releaseMiniArt) }
    }
    return this
        .onPlaced { coordinates ->
            placed[0] = coordinates
            dock.reportMiniArt(coordinates, corner)
        }
        .graphicsLayer { alpha = if (dock.coversMiniArt) 0f else 1f }
}

/**
 * The mini-player's swipes: up opens the video, down or right closes it, left plays or pauses it
 * (the bar slides a little and springs back).
 */
fun Modifier.miniPlayerPullGestures(
    pull: MiniPlayerPull?,
    onDismiss: (() -> Unit)? = null,
    onTogglePlay: (() -> Unit)? = null,
    playing: Boolean = true,
    shape: Shape = RoundedCornerShape(percent = 50),
): Modifier = composed {
    if (pull == null && onDismiss == null && onTogglePlay == null) return@composed this
    val currentPull by rememberUpdatedState(pull)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentToggle by rememberUpdatedState(onTogglePlay)
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val slide = remember { mutableFloatStateOf(0f) }
    val pinMini by remember(pull) { derivedStateOf { pull?.pinsMini == true } }
    val scope = rememberCoroutineScope()
    val lift = remember { mutableFloatStateOf(0f) }
    val drop = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    val currentPlaying by rememberUpdatedState(playing)
    // 0..1 as a sideways swipe crosses the point where letting go acts; the pop is a
    // short overshoot of the revealed glyph at that moment and when it acts.
    val armed = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val armedSide = remember { mutableFloatStateOf(0f) }
    val pausePainter = rememberVectorPainter(Icons.Rounded.Pause)
    val playPainter = rememberVectorPainter(Icons.Rounded.PlayArrow)
    val closePainter = rememberVectorPainter(Icons.Rounded.Close)
    fun popGlyph() = scope.launch {
        pop.snapTo(1f)
        pop.animateTo(1.22f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh))
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    fun arm(on: Boolean) = scope.launch {
        armed.animateTo(if (on) 1f else 0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
    }
    pointerInput(Unit) {
        val liftEase = PULL_LIFT_EASE.toPx()
        val dismissPx = DISMISS_DISTANCE.toPx()
        val pauseMaxPx = PAUSE_PULL_MAX.toPx()
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointer = down.id
            var start: PointerInputChange? = null
            var upward = false
            var sideways = false
            while (start == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                upward = moved.y < 0f && abs(moved.x) < -moved.y * PULL_BIAS
                val downward = moved.y > 0f && abs(moved.x) < moved.y * PULL_BIAS
                val rightward = moved.x > 0f && abs(moved.y) < moved.x
                val leftward = moved.x < 0f && abs(moved.y) < -moved.x
                // Down or right closes the video, up opens it, left plays or pauses it.
                if ((rightward && currentDismiss != null && !pinMini) || (leftward && currentToggle != null)) {
                    sideways = true
                    change.consume()
                    start = change
                    break
                }
                if (!upward && !downward) return@awaitEachGesture
                if (downward && (currentDismiss == null || pinMini)) return@awaitEachGesture
                if (upward && currentPull == null) return@awaitEachGesture
                change.consume()
                start = change
            }
            val slopped = start ?: return@awaitEachGesture
            settle[0]?.cancel()
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(slopped)
            if (sideways) {
                val width = size.width.toFloat().coerceAtLeast(1f)
                // Left only nudges the bar (it pauses, it doesn't leave); right follows the finger off.
                fun bound(x: Float) = if (x < 0f) -pauseMaxPx * (1f - 1f / (1f + -x / pauseMaxPx)) else x
                val actPx = width * SIDE_ACT_SHARE
                var moved = slopped.position.x - down.position.x
                slide.floatValue = bound(moved)
                var wasArmed = false
                fun track() {
                    val canAct = (moved > 0f && currentDismiss != null) || (moved < 0f && currentToggle != null)
                    val nowArmed = canAct && abs(moved) >= actPx
                    if (nowArmed != wasArmed) {
                        wasArmed = nowArmed
                        armedSide.floatValue = if (moved > 0f) 1f else -1f
                        arm(nowArmed)
                        if (nowArmed) {
                            popGlyph()
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                track()
                val finished = drag(pointer) { change ->
                    moved += change.positionChange().x
                    change.consume()
                    tracker.addPointerInputChange(change)
                    slide.floatValue = bound(moved)
                    track()
                }
                val velocity = if (finished) tracker.calculateVelocity().x.coerceIn(-maxVelocity, maxVelocity) else 0f
                val close = currentDismiss
                val toggle = currentToggle
                val dismiss = finished && close != null && moved > 0f &&
                    (moved >= width * SIDE_ACT_SHARE || velocity >= SIDE_FLICK)
                val pause = finished && toggle != null && moved < 0f &&
                    (-moved >= width * SIDE_ACT_SHARE || velocity <= -SIDE_FLICK)
                val from = slide.floatValue
                settle[0] = scope.launch {
                    if (dismiss && close != null) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        animate(
                            initialValue = from,
                            targetValue = width,
                            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                        ) { value, _ -> slide.floatValue = value }
                        close()
                        // Stay off to the side while the bar exits. Snapping back to 0 here
                        // flashed the bar in its old place before it left.
                        delay(CLOSED_HOLD_MS)
                        slide.floatValue = 0f
                        armed.snapTo(0f)
                    } else {
                        if (pause && toggle != null) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            toggle()
                            armedSide.floatValue = -1f
                            armed.snapTo(1f)
                            popGlyph()
                        }
                        animate(
                            initialValue = from,
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> slide.floatValue = value }
                        arm(false)
                    }
                }
                return@awaitEachGesture
            }
            if (!upward) {
                var dropped = (slopped.position.y - down.position.y).coerceAtLeast(0f)
                drop.floatValue = dropped
                val finished = drag(pointer) { change ->
                    val delta = change.positionChange().y
                    change.consume()
                    tracker.addPointerInputChange(change)
                    dropped = (dropped + delta).coerceAtLeast(0f)
                    drop.floatValue = dropped
                }
                val velocity = if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f
                val dismiss = finished && (dropped >= dismissPx || velocity >= DISMISS_FLICK)
                val from = drop.floatValue
                val close = currentDismiss
                settle[0] = scope.launch {
                    if (dismiss && close != null) {
                        animate(
                            initialValue = from,
                            targetValue = from + dismissPx,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
                        ) { value, _ -> drop.floatValue = value }
                        close()
                        delay(CLOSED_HOLD_MS)
                        drop.floatValue = 0f
                    } else {
                        animate(
                            initialValue = from,
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> drop.floatValue = value }
                    }
                }
                return@awaitEachGesture
            }
            val target = currentPull ?: return@awaitEachGesture
            target.drag(slopped.position.y - down.position.y)
            var pulled = 0f
            val finished = drag(pointer) { change ->
                val delta = change.positionChange().y
                change.consume()
                tracker.addPointerInputChange(change)
                pulled -= delta
                target.drag(delta)
                lift.floatValue = pullLift(pulled, liftEase)
            }
            target.release(
                if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f,
            )
            val from = lift.floatValue
            settle[0] = scope.launch {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> lift.floatValue = value }
            }
        }
    }.drawBehind {
        // Like swiping an email away: the bar's own shape, filled behind it in the action's
        // colour (green pause/play when sliding left, red close when sliding right), with the
        // icon by the edge it uncovers. It deepens and the icon pops once letting go would act.
        val moved = slide.floatValue
        if (moved == 0f) return@drawBehind
        val left = moved < 0f
        val gap = abs(moved)
        val reach = if (left) PAUSE_PULL_MAX.toPx() * 0.55f else size.width * SIDE_ACT_SHARE
        val grow = (gap / reach).coerceIn(0f, 1f)
        if (grow <= 0.01f) return@drawBehind
        val lit = if ((armedSide.floatValue < 0f) == left) armed.value else 0f
        val hue = if (left) SWIPE_GREEN else SWIPE_RED
        val fill = lerp(hue.copy(alpha = 0.55f), hue, lit)
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            color = fill.copy(alpha = fill.alpha * grow),
        )
        val edge = SWIPE_ICON_INSET.toPx()
        val cx = if (left) size.width - (gap / 2f).coerceAtMost(edge) else (gap / 2f).coerceAtMost(edge)
        val cy = size.height / 2f
        val painter = when {
            !left -> closePainter
            currentPlaying -> pausePainter
            else -> playPainter
        }
        val g = SWIPE_GLYPH.toPx() * (0.6f + 0.4f * grow) * pop.value
        translate(cx - g / 2f, cy - g / 2f) {
            with(painter) {
                draw(androidx.compose.ui.geometry.Size(g, g), alpha = grow, colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }.graphicsLayer {
        translationY = if (pinMini) 0f else drop.floatValue - lift.floatValue
        translationX = slide.floatValue
        // Fades as it leaves to the right, so the close reads as a dismissal.
        alpha = if (slide.floatValue > 0f) (1f - slide.floatValue / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
    }
}
