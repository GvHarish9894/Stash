package com.techgv.stash.core.designsystem

import androidx.compose.runtime.Composable

/**
 * Plays a short bundled sound (WAV bytes) once, as a UI sound: silent when the device is on silent or vibrate, mixed
 * with any music that is playing. A sound that cannot play is skipped.
 */
@Composable
internal expect fun rememberSoundPlayer(): (ByteArray) -> Unit
