package com.yuno

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Dislike counts from Return YouTube Dislike (returnyoutubedislikeapi.com), the public service
 * ReVanced and the browser extension use; YouTube itself stopped publishing them. The counts are
 * estimates. Only the video's id is sent. A switch in Settings › Playback turns it off.
 */
object ReturnDislike {
    private const val KEY = "return_dislike"
    private const val API = "https://returnyoutubedislikeapi.com/votes?videoId="
    private var prefs: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, Long>()

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY, true) != false
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
    }

    /** [videoId]'s dislike count, or null when the service has none or can't be reached. */
    suspend fun dislikes(videoId: String): Long? = withContext(Dispatchers.IO) {
        cache[videoId]?.let { return@withContext it }
        val request = Request.Builder().url(API + videoId).header("User-Agent", "Yuno/${BuildConfig.VERSION_NAME}").build()
        runCatching {
            Net.client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                parse(r.body?.string().orEmpty())
            }
        }.getOrNull()?.also { cache[videoId] = it }
    }

    internal fun parse(json: String): Long? =
        runCatching { JSONObject(json).optLong("dislikes", -1L) }.getOrNull()?.takeIf { it >= 0 }
}
