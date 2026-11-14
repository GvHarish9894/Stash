package com.techgv.stash.feature.reader

// Every decision the platform web views make lives here, so it is tested in common code; the actuals only wire it up.

/**
 * The https form of [url] the reader may load: https as is, http upgraded to https (decision D17, cleartext is blocked
 * on both platforms), anything else (mailto:, tel:, intent:, javascript:, file:, data:, custom app schemes) null, so
 * the web view never follows it and never hands it to another app.
 */
internal fun httpsUrl(url: String): String? {
    val scheme = url.substringBefore(':', missingDelimiterValue = "")
    return when (scheme.lowercase()) {
        "https" -> url
        "http" -> "https" + url.substring(scheme.length)
        else -> null
    }
}

/** A link tapped in reader mode goes to the browser when it is a web link; other schemes are ignored. */
internal fun browserLink(url: String): String? = url.takeIf { httpsUrl(it) != null }

/** Handoff: the brass load bar runs from 8 % to 100 % of the width as the page loads ([progress] 0..1). */
internal fun loadBarFraction(progress: Float): Float = 0.08f + 0.92f * progress.coerceIn(0f, 1f)

/**
 * WebKit reports a navigation the reader cancelled itself (a blocked scheme or an http link re-loaded as https) as
 * `NSURLErrorCancelled` or "frame load interrupted"; those are not load failures.
 */
internal fun isCancelledNavigation(domain: String, code: Long): Boolean =
    (domain == "NSURLErrorDomain" && code == -999L) || (domain == "WebKitErrorDomain" && code == 102L)
