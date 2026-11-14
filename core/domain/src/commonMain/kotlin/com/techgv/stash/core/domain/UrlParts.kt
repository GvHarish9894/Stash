package com.techgv.stash.core.domain

/** A parsed http(s) URL without user info. Query params stay in their raw, still-encoded `key=value` form. */
internal data class UrlParts(
    val scheme: String,
    val host: String,
    val port: Int?,
    val path: String,
    val query: List<String>,
    val fragment: String?,
) {
    fun queryValue(key: String): String? =
        query.firstOrNull { percentDecode(it.substringBefore('=')) == key && '=' in it }
            ?.let { percentDecode(it.substringAfter('=')) }

    override fun toString(): String = buildString {
        append(scheme).append("://")
        append(host)
        port?.let { append(':').append(it) }
        append(path)
        if (query.isNotEmpty()) append('?').append(query.joinToString("&"))
        fragment?.let { append('#').append(it) }
    }

    companion object {
        private val hostChars = Regex("""[\p{L}\p{N}._~-]+""")

        /**
         * Null unless [raw] is an absolute http or https URL with a host. Scheme and host come back lowercase; any
         * `user:pass@` is dropped. A backslash in the authority is rejected: browsers read it as `/`, so
         * `https://attacker.com\@trusted.com` would open attacker.com while looking like trusted.com.
         */
        fun parse(raw: String): UrlParts? {
            val text = raw.trim()
            val sep = text.indexOf("://")
            if (sep <= 0) return null
            val scheme = text.substring(0, sep).lowercase()
            if (scheme != "http" && scheme != "https") return null
            val afterScheme = text.substring(sep + 3)
            val fragment = if ('#' in afterScheme) afterScheme.substringAfter('#') else null
            val noFragment = afterScheme.substringBefore('#')
            val query = if ('?' in noFragment) noFragment.substringAfter('?').split('&').filter { it.isNotEmpty() } else emptyList()
            val hierarchy = noFragment.substringBefore('?')
            val authority = hierarchy.substringBefore('/')
            val path = if ('/' in hierarchy) "/" + hierarchy.substringAfter('/') else ""
            if ('\\' in authority) return null
            val hostPort = authority.substringAfterLast('@')
            val host: String
            val portText: String?
            if (hostPort.startsWith('[')) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(0, close + 1)
                val tail = hostPort.substring(close + 1)
                if (tail.isNotEmpty() && !tail.startsWith(':')) return null
                portText = tail.removePrefix(":").ifEmpty { null }
            } else {
                host = hostPort.substringBefore(':').trimEnd('.')
                if (!hostChars.matches(host) || host.startsWith('.')) return null
                portText = if (':' in hostPort) hostPort.substringAfter(':').ifEmpty { null } else null
            }
            val port = portText?.let { text -> text.toIntOrNull()?.takeIf { it in 0..65535 } ?: return null }
            return UrlParts(scheme, host.lowercase(), port, path, query, fragment)
        }
    }
}

/** Decodes `%XX` escapes as UTF-8 and `+` as a space, as in a query component. Malformed escapes stay as they are. */
internal fun percentDecode(text: String): String {
    if ('%' !in text && '+' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val high = if (c == '%' && i + 2 < text.length) text[i + 1].digitToIntOrNull(16) else null
        val low = if (high != null) text[i + 2].digitToIntOrNull(16) else null
        when {
            high != null && low != null -> {
                bytes += (high * 16 + low).toByte()
                i += 3
            }
            c == '+' -> {
                bytes += ' '.code.toByte()
                i++
            }
            else -> {
                // Copy a whole code point so surrogate pairs encode correctly.
                val end = if (c.isHighSurrogate() && i + 1 < text.length) i + 2 else i + 1
                text.substring(i, end).encodeToByteArray().forEach { bytes += it }
                i = end
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
