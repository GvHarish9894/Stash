package com.techgv.stash.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/** The playing sound: AVAudioPlayer stops when it is released. Main thread only. */
private var playing: AVAudioPlayer? = null

@Composable
internal actual fun rememberSoundPlayer(): (ByteArray) -> Unit = remember { ::play }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun play(bytes: ByteArray) {
    // Ambient: muted by the silent switch and mixed with other audio.
    AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, error = null)
    AVAudioSession.sharedInstance().setActive(true, error = null)
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    playing = AVAudioPlayer(data = data, error = null).takeIf { it.play() }
}
