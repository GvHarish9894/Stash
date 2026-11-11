package com.techgv.stash

import java.util.TimeZone

actual fun localUtcOffsetSeconds(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
