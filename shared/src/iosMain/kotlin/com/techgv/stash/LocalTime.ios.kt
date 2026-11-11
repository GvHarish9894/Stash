package com.techgv.stash

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun localUtcOffsetSeconds(): Int = NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate()).toInt()
