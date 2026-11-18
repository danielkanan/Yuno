package com.yuno

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextParseTest {
    private fun fixture(name: String) = JSONObject(javaClass.classLoader!!.getResource("$name.json")!!.readText())

    @Test
    fun watchNextGivesAutoplayPickAndRelated() {
        val up = InnerTube.parseUpNext(fixture("next"))
        assertEquals("sehKAccM8p0", up.autoplayId)
        assertTrue("related list should be filled, was ${up.related.size}", up.related.size >= 10)
        assertEquals("hNGJ0WHXMyE", up.related.first().id)
        // The autoplay pick leads; nothing is listed twice.
        assertEquals("sehKAccM8p0", up.inOrder.first().id)
        assertEquals(up.inOrder.size, up.inOrder.map { it.id }.toSet().size)
    }

    @Test
    fun mixPanelVideos() {
        val panel = JSONObject(
            """{"contents":{"twoColumnWatchNextResults":{"playlist":{"playlist":{"contents":[
              {"playlistPanelVideoRenderer":{"videoId":"aaaaaaaaaaa","title":{"simpleText":"Song A"},
                "shortBylineText":{"runs":[{"text":"Artist A"}]},"lengthText":{"simpleText":"3:30"}}},
              {"playlistPanelVideoRenderer":{"videoId":"bbbbbbbbbbb","title":{"simpleText":"Song B"},
                "shortBylineText":{"runs":[{"text":"Artist B"}]},"lengthText":{"simpleText":"4:05"}}}
            ]}}}}}""",
        )
        val mix = InnerTube.parseMix(panel)
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), mix.map { it.id })
        assertEquals("Song A", mix[0].title)
        assertEquals("Artist A", mix[0].channel)
        assertEquals(210L, mix[0].durationSec)
    }
}
