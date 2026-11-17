package com.yuno

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuno.icons.SfIcons

/** A playlist's order, as the YouTube app offers it (Sort). */
enum class PlaylistSort(val label: String) {
    Playlist("Playlist order"),
    Popular("Most popular"),
    Newest("Newest"),
    Oldest("Oldest"),
}

/** Which videos of a playlist show. */
enum class PlaylistFilter(val label: String) { All("All"), Videos("Videos"), Shorts("Shorts") }

object PlaylistView {
    /** Shorts are a minute or less; a video with no known length counts as a regular one. */
    fun isShort(v: VideoItem) = v.durationSec in 1..60

    fun apply(videos: List<VideoItem>, sort: PlaylistSort, filter: PlaylistFilter): List<VideoItem> {
        val shown = when (filter) {
            PlaylistFilter.All -> videos
            PlaylistFilter.Videos -> videos.filterNot(::isShort)
            PlaylistFilter.Shorts -> videos.filter(::isShort)
        }
        return when (sort) {
            PlaylistSort.Playlist -> shown
            // Stable sorts: equal keys keep the playlist's order. Unknown views/ages go last.
            PlaylistSort.Popular -> shown.sortedByDescending { it.views.coerceAtLeast(-1) }
            PlaylistSort.Newest -> shown.sortedBy { NewVideos.ageMs(it.uploaded) ?: Long.MAX_VALUE }
            PlaylistSort.Oldest -> shown.sortedByDescending { NewVideos.ageMs(it.uploaded) ?: Long.MIN_VALUE }
        }
    }
}

/** Play all and Shuffle, under a playlist's title. */
@Composable
fun PlaylistPlayButtons(busy: Boolean, enabled: Boolean, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayButton(
            if (busy) "Loading…" else "Play all", SfIcons.PlayFill, c.primary, c.onPrimary,
            Modifier.weight(1f), enabled && !busy, onPlayAll,
        )
        PlayButton(
            "Shuffle", SfIcons.Shuffle, c.surfaceContainerHigh, c.onSurface,
            Modifier.weight(1f), enabled && !busy, onShuffle,
        )
    }
}

@Composable
private fun PlayButton(
    text: String,
    icon: ImageVector,
    bg: Color,
    fg: Color,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .height(44.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/**
 * The playlist's sort chips, then (when it has any Shorts) Videos / Shorts. Tapping the chosen
 * filter again shows everything.
 */
@Composable
fun PlaylistChips(
    sort: PlaylistSort,
    filter: PlaylistFilter,
    showFilters: Boolean,
    onSort: (PlaylistSort) -> Unit,
    onFilter: (PlaylistFilter) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistSort.entries.forEach { s -> Chip(s.label, s == sort) { onSort(s) } }
        if (showFilters) {
            Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 22.dp).background(c.outlineVariant))
            listOf(PlaylistFilter.Videos, PlaylistFilter.Shorts).forEach { f ->
                Chip(f.label, f == filter) { onFilter(if (f == filter) PlaylistFilter.All else f) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val bg by animateColorAsState(if (on) c.primary else c.surfaceContainerHigh, tween(if (reduced) 0 else 220), label = "plBg")
    val fg by animateColorAsState(if (on) c.onPrimary else c.onSurfaceVariant, tween(if (reduced) 0 else 220), label = "plFg")
    val corner by animateDpAsState(
        if (on) 18.dp else 10.dp,
        if (reduced) tween(0) else spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "plCorner",
    )
    Box(
        Modifier
            .pressable(onClick = onClick)
            .height(36.dp)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}
