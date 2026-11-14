package com.techgv.stash.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small square thumbnails for the widget rows. Fetched over https only (D17), at most [MAX_BYTES], and kept in memory.
 * shortcut: no disk cache, so a new process fetches the three images again on its first widget update; move to Coil's
 * cache if the shared UI adds one on Android.
 */
internal object WidgetThumbnails {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val SIDE_PX = 128
    private const val TIMEOUT_MS = 5_000

    private val cache = LruCache<String, Bitmap>(9)

    /** The thumbnail, or null when it cannot be fetched or decoded; the row then shows the monogram tile. */
    fun load(url: String): Bitmap? = cache.get(url) ?: fetch(url)?.also { cache.put(url, it) }

    private fun fetch(url: String): Bitmap? = try {
        val https = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
        if (!https.startsWith("https://", ignoreCase = true)) null else download(URL(https))?.let(::decode)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        // A hostile image must not take the app down; the row shows the monogram.
        null
    }

    private fun download(url: URL): ByteArray? {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                var read = input.read(buffer)
                while (read >= 0) {
                    if (out.size() + read > MAX_BYTES) return null
                    out.write(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return ThumbnailUtils.extractThumbnail(full, SIDE_PX, SIDE_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
    }

    /**
     * The power-of-two downsampling that brings the LONGER side below 2 × [SIDE_PX], so at most 256 × 256 pixels are
     * decoded whatever the shape: a 60000 × 200 strip is not decoded at full size.
     */
    fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= SIDE_PX) sample *= 2
        return sample
    }
}
