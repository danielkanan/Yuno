package com.yuno

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** How much of a video to keep: its sound only, or the picture up to a height too. */
enum class DownloadQuality(val label: String, val height: Int) {
    AudioOnly("Audio only", 0),
    P360("360p", 360),
    P720("720p", 720),
    P1080("1080p", 1080),
}

@Immutable
data class Download(
    val item: VideoItem,
    val quality: DownloadQuality,
    val state: State,
    /** 0..1 while running; 1 when done. */
    val progress: Float = 0f,
    val bytes: Long = 0,
    val error: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    enum class State { Queued, Running, Done, Failed }
    val done get() = state == State.Done
    val active get() = state == State.Queued || state == State.Running
}

/**
 * Offline videos, as YouTube Premium's downloads: the video's own streams saved on the phone
 * (app storage; removed with the app), then played from there with no network at all.
 *
 * Each video has a folder `downloads/<id>/` holding `video.mp4` (picture only, H.264 when YouTube
 * offers it) and `audio.m4a`/`audio.webm` (sound), which the player merges, plus `thumb.jpg` and
 * `meta.json`. Streams come down in ranged pieces like the player fetches them (a whole stream in
 * one request is throttled to a crawl); a piece refused with 403 (links expire) gets fresh links.
 * A download stopped halfway (app killed) is shown as failed and continues where it stopped.
 */
object Downloads {
    private const val TAG = "Yuno"
    private const val CHUNK = 8L * 1024 * 1024
    private const val MAX_LINK_REFRESHES = 3

    private lateinit var root: File
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Newest first. */
    var all by mutableStateOf<List<Download>>(emptyList())
        private set

    val running get() = all.count { it.active }

    fun init(context: Context) {
        app = context.applicationContext
        root = File(context.filesDir, "downloads").apply { mkdirs() }
        all = root.listFiles().orEmpty().mapNotNull { dir -> readMeta(dir) }
            .map { if (it.active) it.copy(state = Download.State.Failed, error = "Stopped. Tap to continue.") else it }
            .sortedByDescending { it.addedAt }
    }

    fun get(id: String): Download? = all.firstOrNull { it.item.id == id }

    /** The saved files for [id] when its download is complete: (picture or null for audio only, sound). */
    fun files(id: String): Pair<File?, File>? {
        val d = get(id)?.takeIf { it.done } ?: return null
        val dir = File(root, id)
        val audio = dir.listFiles()?.firstOrNull { it.name.startsWith("audio.") && !it.name.endsWith(".part") } ?: return null
        val video = File(dir, "video.mp4").takeIf { it.exists() } ?: File(dir, "video.webm").takeIf { it.exists() }
        if (d.quality != DownloadQuality.AudioOnly && video == null) return null
        return video to audio
    }

    fun start(item: VideoItem, quality: DownloadQuality) {
        if (jobs[item.id]?.isActive == true) return
        val existing = get(item.id)
        val entry = Download(
            item = existing?.item ?: item,
            quality = quality,
            state = Download.State.Queued,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
        )
        // Another quality than the stopped one: start those files over.
        if (existing != null && existing.quality != quality) File(root, item.id).deleteRecursively()
        put(entry)
        jobs[item.id] = scope.launch { run(entry) }
        startService()
    }

    /** Continues a failed or stopped download with the quality it had. */
    fun retry(id: String) {
        val d = get(id) ?: return
        start(d.item, d.quality)
    }

    fun delete(id: String) {
        jobs.remove(id)?.cancel()
        File(root, id).deleteRecursively()
        all = all.filterNot { it.item.id == id }
    }

    private fun put(d: Download) {
        val others = all.filterNot { it.item.id == d.item.id }
        all = (others + d).sortedByDescending { it.addedAt }
        writeMeta(d)
    }

    private fun update(id: String, change: (Download) -> Download) {
        val d = get(id) ?: return
        val next = change(d)
        all = all.map { if (it.item.id == id) next else it }
        if (next.state != d.state) writeMeta(next)
    }

    private suspend fun run(entry: Download) {
        val id = entry.item.id
        val dir = File(root, id).apply { mkdirs() }
        try {
            update(id) { it.copy(state = Download.State.Running, error = null) }
            var info = withLinks(id, fresh = false)
            saveThumbnail(id, dir, entry.item)
            // The details give a title and length the card may not have had (opened from a link).
            update(id) { d ->
                d.copy(item = d.item.copy(
                    title = d.item.title.ifBlank { info.name },
                    channel = d.item.channel.ifBlank { info.uploaderName.orEmpty() },
                    durationSec = d.item.durationSec.takeIf { it > 0 } ?: info.duration,
                ))
            }
            val (videoPick, audioPick) = pick(info, entry.quality)
            val audio = audioPick ?: throw IOException("No sound stream for this video.")
            if (entry.quality != DownloadQuality.AudioOnly && videoPick == null) throw IOException("No video stream for this video.")
            val audioFile = File(dir, "audio." + (if (audio.format == MediaFormat.WEBMA || audio.format == MediaFormat.WEBMA_OPUS) "webm" else "m4a"))
            val videoFile = videoPick?.let { File(dir, if (it.format == MediaFormat.WEBM) "video.webm" else "video.mp4") }
            val total = (videoPick?.itagItem?.contentLength ?: 0L).coerceAtLeast(0) + (audio.itagItem?.contentLength ?: 0L).coerceAtLeast(0)
            var doneBefore = 0L
            val parts = listOfNotNull(videoPick?.let { it.itag to videoFile!! }, audio.itag to audioFile)
            for ((itag, file) in parts) {
                var refreshes = 0
                while (true) {
                    val url = (info.videoOnlyStreams.firstOrNull { it.itag == itag && it.isUrl }?.content
                        ?: info.audioStreams.firstOrNull { it.itag == itag && it.isUrl }?.content)
                        ?: throw IOException("That quality is no longer offered.")
                    try {
                        fetch(url, file) { got, size ->
                            val whole = if (total > 0) total else size
                            val p = if (whole > 0) ((doneBefore + got).toFloat() / whole).coerceIn(0f, 0.99f) else 0f
                            update(id) { it.copy(progress = p, bytes = doneBefore + got) }
                        }
                        break
                    } catch (e: Refused) {
                        if (++refreshes > MAX_LINK_REFRESHES) throw IOException("YouTube refused the download.")
                        info = withLinks(id, fresh = true)
                    }
                }
                doneBefore += file.length()
            }
            update(id) { it.copy(state = Download.State.Done, progress = 1f, bytes = doneBefore, error = null) }
            Log.i(TAG, "download $id done: ${entry.quality.label}, ${doneBefore / 1024 / 1024} MB")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download $id failed", e)
            update(id) { it.copy(state = Download.State.Failed, error = YouTube.friendlyError(e)) }
        } finally {
            jobs.remove(id)
        }
    }

    private fun withLinks(id: String, fresh: Boolean): StreamInfo {
        if (fresh) StreamCache.invalidate(id)
        return StreamCache.getOrLoad(id)
    }

    /** The picture (none for audio only) and sound to save for [quality]. */
    internal fun pick(info: StreamInfo, quality: DownloadQuality): Pair<VideoStream?, AudioStream?> {
        val audio = info.audioStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedWith(
                compareBy<AudioStream> { if (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL) 0 else 1 }
                    .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                    .thenByDescending { it.averageBitrate },
            )
            .firstOrNull()
        if (quality == DownloadQuality.AudioOnly) return null to audio
        val usable = info.videoOnlyStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.height > 0 }
        // H.264 plays everywhere in hardware; VP9 only when that's all there is at this height.
        val video = (usable.filter { it.height <= quality.height }.ifEmpty { usable })
            .sortedWith(
                compareByDescending<VideoStream> { if (it.height <= quality.height) it.height else -it.height }
                    .thenBy { if (it.codec?.startsWith("avc") == true) 0 else 1 }
                    .thenBy { it.fps },
            )
            .firstOrNull()
        return video to audio
    }

    private class Refused : IOException("refused")

    /** Downloads [url] into [dest] in ranged pieces, continuing a `.part` file left from before. */
    private suspend fun fetch(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        if (dest.exists()) { onProgress(dest.length(), dest.length()); return }
        val part = File(dest.path + ".part")
        var got = part.length()
        var size = -1L
        FileOutputStream(part, true).use { out ->
            while (size < 0 || got < size) {
                coroutineContext.ensureActive()
                val request = Request.Builder().url(url)
                    .header("User-Agent", Net.USER_AGENT)
                    .header("Range", "bytes=$got-${got + CHUNK - 1}")
                    .build()
                Net.client.newCall(request).execute().use { r ->
                    if (r.code == 416 && size < 0) { size = got; return@use }
                    if (r.code == 403) throw Refused()
                    if (!r.isSuccessful) throw IOException("Download stopped (HTTP ${r.code}).")
                    size = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: (r.body?.contentLength()?.takeIf { it >= 0 }?.plus(got) ?: -1L)
                    val input = r.body?.byteStream() ?: throw IOException("Empty answer")
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        got += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 300) { lastReport = now; onProgress(got, size) }
                    }
                    // A server that ignored the range sent everything at once.
                    if (r.code == 200) size = got
                }
                if (size < 0) throw IOException("Unknown size")
            }
        }
        if (!part.renameTo(dest)) throw IOException("Couldn't save the file.")
        onProgress(got, size)
    }

    private fun saveThumbnail(id: String, dir: File, item: VideoItem) {
        val file = File(dir, "thumb.jpg")
        if (file.exists()) return
        val url = item.thumbnail?.takeIf { it.startsWith("http") } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        runCatching {
            Net.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
            }
        }
        if (file.exists() && file.length() > 0) update(id) { it.copy(item = it.item.copy(thumbnail = file.toURI().toString())) }
    }

    private fun writeMeta(d: Download) {
        val dir = File(root, d.item.id).apply { mkdirs() }
        val v = d.item
        val json = JSONObject()
            .put("id", v.id).put("title", v.title).put("channel", v.channel).put("channelUrl", v.channelUrl ?: "")
            .put("thumbnail", v.thumbnail ?: "").put("avatar", v.avatar ?: "").put("durationSec", v.durationSec)
            .put("views", v.views).put("uploaded", v.uploaded ?: "")
            .put("quality", d.quality.name).put("state", d.state.name).put("bytes", d.bytes).put("addedAt", d.addedAt)
        runCatching { File(dir, "meta.json").writeText(json.toString()) }
    }

    private fun readMeta(dir: File): Download? = runCatching {
        val o = JSONObject(File(dir, "meta.json").readText())
        val item = VideoItem(
            id = o.getString("id"),
            title = o.optString("title"),
            channel = o.optString("channel"),
            channelUrl = o.optString("channelUrl").ifBlank { null },
            thumbnail = o.optString("thumbnail").ifBlank { null },
            avatar = o.optString("avatar").ifBlank { null },
            durationSec = o.optLong("durationSec"),
            views = o.optLong("views", -1),
            uploaded = o.optString("uploaded").ifBlank { null },
        )
        Download(
            item = item,
            quality = DownloadQuality.valueOf(o.optString("quality", DownloadQuality.P720.name)),
            state = Download.State.valueOf(o.optString("state", Download.State.Failed.name)),
            progress = if (o.optString("state") == Download.State.Done.name) 1f else 0f,
            bytes = o.optLong("bytes"),
            addedAt = o.optLong("addedAt", dir.lastModified()),
        )
    }.getOrNull()

    private fun startService() {
        val intent = Intent(app, DownloadService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
        }.onFailure { Log.w(TAG, "download service didn't start; downloading in the app only", it) }
    }
}

/** Keeps downloads going with the app in the background, with their progress in a notification. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val first = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, first)
        scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
        scope.launch {
            while (Downloads.running > 0) {
                delay(1_000)
                updateNotification(manager)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(manager: NotificationManager) {
        runCatching { manager.notify(ID, notification()) }
    }

    private fun notification(): Notification {
        val active = Downloads.all.filter { it.active }
        val now = active.firstOrNull { it.state == Download.State.Running } ?: active.firstOrNull()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_skipii)
            .setContentTitle(if (active.size > 1) "Downloading ${active.size} videos" else "Downloading")
            .setContentText(now?.item?.title.orEmpty())
            .setProgress(100, ((now?.progress ?: 0f) * 100).toInt(), now == null || now.progress <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val ID = 4207
    }
}
