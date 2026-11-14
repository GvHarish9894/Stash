package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.SharedPayload

sealed interface LinkCapture {
    /** [url] is unwrapped and free of tracking params; [normalizedUrl] is the dedupe key; [title] is provisional. */
    data class Captured(
        val url: String,
        val normalizedUrl: String,
        val title: String,
        val domain: String,
        val siteName: String,
        val sourceKey: String,
        val contentType: ContentType,
    ) : LinkCapture

    data object NoLink : LinkCapture
}

/** Result of validating a link typed into the Add link sheet. */
sealed interface ManualLink {
    data class Valid(val capture: LinkCapture.Captured) : ManualLink
    data object Empty : ManualLink
    data object NotALink : ManualLink
}

/** Turns shared text or a typed link into a clean URL, its dedupe key and what can be guessed from the host. */
object LinkCapturePolicy {
    private const val MAX_TITLE = 500

    // Full-width punctuation and brackets end a URL: CJK text often puts them right after a link with no space.
    private val urlInText = Regex("""https?://[^\s<>"。，、；：！？（）【】「」『』]+""", RegexOption.IGNORE_CASE)

    // Same shape as the prototype's check, also allowing a query or fragment straight after the host.
    private val typedUrl = Regex("""^(https?://)?([\w-]+\.)+[a-z]{2,}(:\d+)?([/?#]\S*)?$""", RegexOption.IGNORE_CASE)

    private class Redirect(val param: String, val matches: (host: String, path: String) -> Boolean)

    private val redirects = listOf(
        Redirect("q") { h, p -> p == "/url" && h.removePrefix("www.").startsWith("google.") },
        Redirect("url") { h, p -> p == "/url" && h.removePrefix("www.").startsWith("google.") },
        Redirect("u") { h, p -> p == "/l.php" && h in setOf("l.facebook.com", "lm.facebook.com", "l.messenger.com") },
        Redirect("u") { h, _ -> h == "l.instagram.com" },
        Redirect("url") { h, _ -> h == "out.reddit.com" },
        Redirect("z") { h, p -> p == "/redirect" && h == "t.umblr.com" },
        Redirect("q") { h, p -> p == "/redirect" && h in setOf("youtube.com", "www.youtube.com", "m.youtube.com") },
    )

    // `ref` is kept: many sites use it for real content (branches, referral pages).
    private val trackingParams = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid",
        "mc_cid", "mc_eid", "igshid", "igsh", "si", "_hsenc", "_hsmi", "mkt_tok", "ref_src", "ref_url", "_gl", "spm",
    )

    /** Params that are tracking only on some sites, keyed by site name. Elsewhere `t` can be a timestamp, for example. */
    private val trackingParamsBySite = mapOf(
        "x.com" to setOf("s", "t"),
        "twitter.com" to setOf("s", "t"),
        "youtube.com" to setOf("feature", "pp"),
        "youtu.be" to setOf("feature", "pp"),
        "linkedin.com" to setOf("trk"),
    )

    private val trailingPunctuation = ".,;:!?'”’»…".toSet()
    private val closingToOpening = mapOf(')' to '(', ']' to '[', '}' to '{')
    private val startsWithScheme = Regex("""^[a-z][a-z0-9+.-]*://""", RegexOption.IGNORE_CASE)

    private class KnownSource(val name: String, val type: ContentType)

    /** Keyed by site name (registrable domain). */
    private val knownSources = mapOf(
        "youtube.com" to KnownSource("YouTube", ContentType.Video),
        "youtu.be" to KnownSource("YouTube", ContentType.Video),
        "vimeo.com" to KnownSource("Vimeo", ContentType.Video),
        "tiktok.com" to KnownSource("TikTok", ContentType.Video),
        "github.com" to KnownSource("GitHub", ContentType.Repository),
        "gitlab.com" to KnownSource("GitLab", ContentType.Repository),
        "bitbucket.org" to KnownSource("Bitbucket", ContentType.Repository),
        "codeberg.org" to KnownSource("Codeberg", ContentType.Repository),
        "medium.com" to KnownSource("Medium", ContentType.Article),
        "substack.com" to KnownSource("Substack", ContentType.Article),
        "x.com" to KnownSource("X", ContentType.Article),
        "twitter.com" to KnownSource("X", ContentType.Article),
        "reddit.com" to KnownSource("Reddit", ContentType.Article),
        "instagram.com" to KnownSource("Instagram", ContentType.Article),
        "wikipedia.org" to KnownSource("Wikipedia", ContentType.Article),
    )

    // Second-level labels under a two-letter country TLD, for example co.uk or com.au.
    private val countrySecondLevels = setOf("co", "com", "org", "net", "ac", "gov", "edu", "ne", "or")

    /**
     * The last http(s) URL in the shared text that parses wins, then earlier ones; the subject is tried only when the text
     * has none.
     */
    fun capture(payload: SharedPayload): LinkCapture {
        val parts = firstParsed(payload.text) ?: firstParsed(payload.subject) ?: return LinkCapture.NoLink
        return captured(parts, provisionalTitle(payload))
    }

    /** Validates a typed link. Input without a scheme, such as `example.com/x`, gets https. */
    fun validateManual(input: String): ManualLink {
        val value = input.trim()
        if (value.isEmpty()) return ManualLink.Empty
        if (!typedUrl.matches(value)) return ManualLink.NotALink
        val withScheme = if (startsWithScheme.containsMatchIn(value)) value else "https://$value"
        val parts = clean(withScheme) ?: return ManualLink.NotALink
        return ManualLink.Valid(captured(parts, title = null))
    }

    /**
     * The dedupe key: redirects unwrapped, tracking params and user info dropped, scheme and host lowercase, no default
     * port, no trailing slash except the root, query sorted, and no fragment unless it starts with `/` or `!` (hash-routed
     * apps put the page there). Null when [url] is not an http(s) URL.
     * Known limit: an internationalized host and its punycode form (`bücher.de`, `xn--bcher-kva.de`) give different keys.
     */
    fun normalize(url: String): String? = clean(url)?.let(::canonical)

    private fun canonical(parts: UrlParts): String {
        val defaultPort = if (parts.scheme == "https") 443 else 80
        return parts.copy(
            port = parts.port.takeIf { it != defaultPort },
            path = parts.path.trimEnd('/').ifEmpty { "/" },
            query = parts.query.sortedWith(compareBy({ it.substringBefore('=') }, { it })),
            fragment = parts.fragment?.takeIf { it.startsWith('/') || it.startsWith('!') },
        ).toString()
    }

    private fun firstParsed(text: String?): UrlParts? =
        text?.let { urlInText.findAll(it).toList().asReversed().firstNotNullOfOrNull { match -> clean(trimTrailing(match.value)) } }

    /** Drops sentence punctuation and unbalanced closing brackets that follow a URL in prose. Linear in the length. */
    private fun trimTrailing(raw: String): String {
        val counts = HashMap<Char, Int>()
        for (c in raw) if (c in closingToOpening.keys || c in closingToOpening.values) counts[c] = (counts[c] ?: 0) + 1
        var end = raw.length
        while (end > 0) {
            val c = raw[end - 1]
            val open = closingToOpening[c]
            val drop = when {
                c in trailingPunctuation -> true
                open != null -> (counts[c] ?: 0) > (counts[open] ?: 0)
                else -> false
            }
            if (!drop) break
            if (open != null) counts[c] = counts.getValue(c) - 1
            end--
        }
        return raw.substring(0, end)
    }

    private fun clean(raw: String): UrlParts? = UrlParts.parse(raw)?.let(::unwrap)?.let(::stripTracking)

    private fun unwrap(parts: UrlParts): UrlParts {
        var current = parts
        repeat(5) {
            val target = redirects.firstNotNullOfOrNull { rule ->
                if (!rule.matches(current.host, current.path)) return@firstNotNullOfOrNull null
                current.queryValue(rule.param)?.let(UrlParts::parse)
            } ?: return current
            current = target
        }
        return current
    }

    private fun stripTracking(parts: UrlParts): UrlParts {
        val hostParams = trackingParamsBySite[registrableDomain(parts.host.removePrefix("www."))].orEmpty()
        return parts.copy(
            query = parts.query.filterNot {
                val key = percentDecode(it.substringBefore('=')).lowercase()
                key.startsWith("utm_") || key in trackingParams || key in hostParams
            },
        )
    }

    private fun provisionalTitle(payload: SharedPayload): String? {
        val subject = payload.subject?.trim()
        if (!subject.isNullOrEmpty() && !urlInText.containsMatchIn(subject)) return subject.take(MAX_TITLE)
        val rest = payload.text
            ?.replace(urlInText, " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim(' ', '-', '|', ':', '·', '—', '–')
        return rest?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE)
    }

    /** A missing [title] falls back to the URL, as the pending card shows it. */
    private fun captured(parts: UrlParts, title: String?): LinkCapture.Captured {
        val url = parts.toString()
        val domain = parts.host.removePrefix("www.")
        val siteName = registrableDomain(domain)
        val known = knownSources[siteName]
        return LinkCapture.Captured(
            url = url,
            normalizedUrl = canonical(parts),
            title = title ?: url,
            domain = domain,
            siteName = siteName,
            sourceKey = known?.name ?: siteName,
            contentType = known?.type ?: ContentType.Article,
        )
    }

    /** Approximates the registrable domain without the public suffix list. */
    private fun registrableDomain(host: String): String {
        if (host.startsWith("[") || host.all { it.isDigit() || it == '.' }) return host
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val keep = if (labels.last().length == 2 && labels[labels.size - 2] in countrySecondLevels) 3 else 2
        return labels.takeLast(keep).joinToString(".")
    }
}
