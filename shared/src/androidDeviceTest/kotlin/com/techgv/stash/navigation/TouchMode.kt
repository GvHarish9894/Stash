package com.techgv.stash.navigation

import androidx.test.platform.app.InstrumentationRegistry

/** Leaves touch mode as a hardware key press does: keyboard focus, focus rings and focusable buttons need it on Android. */
internal fun leaveTouchMode() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
