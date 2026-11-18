package com.yuno

import android.content.Intent

/**
 * YouTube links opened in Yuno: tapped in another app ("Open with", once Yuno is allowed to open
 * youtube.com links) or shared to it from the share sheet. Only video links open something;
 * a channel or playlist link is ignored.
 */
object YouTubeLinks {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtu.be")
    private val PATH_ID = Regex("^/(?:shorts|live|embed|v)/([A-Za-z0-9_-]{11})")
    private val URL = Regex("""https?://\S+""")

    /** The video in a VIEW intent's link, or in the text a SEND intent shares. */
    fun videoFrom(intent: Intent?): VideoItem? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        val id = videoIdIn(text) ?: return null
        // Taken once, so a rotation or a later onNewIntent doesn't open it again.
        intent.action = null
        return VideoItem(
            id = id,
            title = "",
            channel = "",
            channelUrl = null,
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            avatar = null,
            durationSec = 0, views = -1, uploaded = null,
        )
    }

    /** The first YouTube video id in [text]: a bare link, or a link inside shared text. */
    fun videoIdIn(text: String): String? {
        val candidates = URL.findAll(text).map { it.value }.toList().ifEmpty { listOf(text.trim()) }
        return candidates.firstNotNullOfOrNull(::videoIdOf)
    }

    /** youtu.be/ID, youtube.com/watch?v=ID, /shorts/ID, /live/ID, /embed/ID, /v/ID. */
    internal fun videoIdOf(link: String): String? {
        val uri = runCatching { java.net.URI(link.trim().trimEnd('.', ',', ')', '!')) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in HOSTS) return null
        val path = uri.rawPath.orEmpty()
        val id = when {
            host.endsWith("youtu.be") -> path.removePrefix("/").substringBefore('/')
            path == "/watch" -> uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            else -> PATH_ID.find(path)?.groupValues?.get(1)
        }
        return id?.takeIf { ID.matches(it) }
    }
}
