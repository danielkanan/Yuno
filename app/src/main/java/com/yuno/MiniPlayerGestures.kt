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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
                var moved = slopped.position.x - down.position.x
                slide.floatValue = bound(moved)
                val finished = drag(pointer) { change ->
                    moved += change.positionChange().x
                    change.consume()
                    tracker.addPointerInputChange(change)
                    slide.floatValue = bound(moved)
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
                    } else {
                        if (pause && toggle != null) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            toggle()
                        }
                        animate(
                            initialValue = from,
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> slide.floatValue = value }
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
    }.graphicsLayer {
        translationY = if (pinMini) 0f else drop.floatValue - lift.floatValue
        translationX = slide.floatValue
        // Fades as it leaves to the right, so the close reads as a dismissal.
        alpha = if (slide.floatValue > 0f) (1f - slide.floatValue / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
    }
}
