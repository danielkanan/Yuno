package com.yuno

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test
    fun videoLinks() {
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=42"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("https://youtube.com/shorts/dQw4w9WgXcQ?si=x"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD1"))
    }

    @Test
    fun linkInsideSharedText() {
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoIdIn("Watch this! https://youtu.be/dQw4w9WgXcQ."))
    }

    @Test
    fun notAVideo() {
        assertNull(YouTubeLinks.videoIdIn("https://www.youtube.com/@somechannel"))
        assertNull(YouTubeLinks.videoIdIn("https://www.youtube.com/playlist?list=PL123"))
        assertNull(YouTubeLinks.videoIdIn("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLinks.videoIdIn("just some text"))
    }

    @Test
    fun dislikeCount() {
        assertEquals(12345L, ReturnDislike.parse("""{"id":"x","likes":100,"dislikes":12345,"rating":4.5}"""))
        assertNull(ReturnDislike.parse("""{"error":"not found"}"""))
        assertNull(ReturnDislike.parse("not json"))
    }
}
