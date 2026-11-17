package com.yuno

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistViewTest {
    private fun v(id: String, sec: Long, views: Long, uploaded: String?) =
        VideoItem(id, id, "ch", null, null, null, sec, views, uploaded)

    private val list = listOf(
        v("a", 240, 1_000, "2 years ago"),
        v("b", 45, 9_000, "3 days ago"),
        v("c", 300, 50_000, "5 months ago"),
        v("d", 0, -1, null),
    )

    @Test
    fun playlistOrderIsKept() {
        assertEquals(listOf("a", "b", "c", "d"), PlaylistView.apply(list, PlaylistSort.Playlist, PlaylistFilter.All).map { it.id })
    }

    @Test
    fun sorts() {
        assertEquals(listOf("c", "b", "a", "d"), PlaylistView.apply(list, PlaylistSort.Popular, PlaylistFilter.All).map { it.id })
        assertEquals(listOf("b", "c", "a", "d"), PlaylistView.apply(list, PlaylistSort.Newest, PlaylistFilter.All).map { it.id })
        assertEquals(listOf("a", "c", "b", "d"), PlaylistView.apply(list, PlaylistSort.Oldest, PlaylistFilter.All).map { it.id })
    }

    @Test
    fun filters() {
        assertEquals(listOf("b"), PlaylistView.apply(list, PlaylistSort.Playlist, PlaylistFilter.Shorts).map { it.id })
        assertEquals(listOf("a", "c", "d"), PlaylistView.apply(list, PlaylistSort.Playlist, PlaylistFilter.Videos).map { it.id })
    }
}
