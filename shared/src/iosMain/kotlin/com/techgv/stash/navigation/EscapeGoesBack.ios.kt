package com.techgv.stash.navigation

import androidx.compose.runtime.Composable

// Compose on iOS already turns an Esc key that nothing consumed into back (its scene's navigation input).
@Composable
internal actual fun EscapeGoesBack() = Unit
