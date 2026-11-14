package com.techgv.stash.core.designsystem

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSoundPlayer(): (ByteArray) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) { { bytes -> play(context, bytes) } }
}

private fun play(context: Context, bytes: ByteArray) {
    if (context.getSystemService(AudioManager::class.java)?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    val player = MediaPlayer()
    try {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        player.setDataSource(BytesSource(bytes))
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { it.release() }
        player.setOnErrorListener { p, _, _ -> p.release(); true }
        player.prepareAsync()
    } catch (e: Exception) {
        Log.w("Stash", "Sound not played", e)
        player.release()
    }
}

private class BytesSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val count = minOf(size.toLong(), data.size - position).toInt()
        data.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
        return count
    }

    override fun getSize() = data.size.toLong()

    override fun close() = Unit
}
