package com.techgv.stash.core.domain

import com.techgv.stash.core.model.ContentType
import com.techgv.stash.core.model.SharedPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * SYNTHETIC share strings: written to look like what each app sends (text + subject), not captured from real apps.
 */
private data class ShareCase(
    val name: String,
    val text: String?,
    val subject: String?,
    /** Null means NoLink. */
    val url: String?,
    val title: String? = null,
    val normalized: String? = null,
    val sourceKey: String? = null,
    val type: ContentType? = null,
)

private val SYNTHETIC_SHARES = listOf(
    ShareCase(
        "Chrome: URL in text, page title in subject",
        text = "https://www.theverge.com/2026/11/10/kmp-story", subject = "Kotlin goes everywhere - The Verge",
        url = "https://www.theverge.com/2026/11/10/kmp-story", title = "Kotlin goes everywhere - The Verge",
        sourceKey = "theverge.com", type = ContentType.Article,
    ),
    ShareCase(
        "YouTube app: short link with si share id",
        text = "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123", subject = "Watch \"Spring physics\" on YouTube",
        url = "https://youtu.be/dQw4w9WgXcQ", title = "Watch \"Spring physics\" on YouTube",
        sourceKey = "YouTube", type = ContentType.Video,
    ),
    ShareCase(
        "YouTube web: timestamp t kept, si stripped, query sorted in the key",
        text = "https://www.youtube.com/watch?v=abc123&t=42s&si=xyz", subject = null,
        url = "https://www.youtube.com/watch?v=abc123&t=42s",
        normalized = "https://www.youtube.com/watch?t=42s&v=abc123", sourceKey = "YouTube", type = ContentType.Video,
    ),
    ShareCase(
        "X: post text before the link, s and t stripped",
        text = "Big thread on Kotlin 2.4 https://x.com/kotlin/status/1234567890?s=20&t=AbCdEf", subject = null,
        url = "https://x.com/kotlin/status/1234567890", title = "Big thread on Kotlin 2.4", sourceKey = "X",
    ),
    ShareCase(
        "Twitter legacy host",
        text = "https://twitter.com/jetbrains/status/42?s=46", subject = null,
        url = "https://twitter.com/jetbrains/status/42", sourceKey = "X",
    ),
    ShareCase(
        "Reddit app: utm params and trailing slash",
        text = "https://www.reddit.com/r/androiddev/comments/abc/title_here/?utm_source=share&utm_medium=android_app&utm_name=androidcss&utm_term=1&utm_content=share_button",
        subject = "Title here : androiddev",
        url = "https://www.reddit.com/r/androiddev/comments/abc/title_here/",
        normalized = "https://www.reddit.com/r/androiddev/comments/abc/title_here", sourceKey = "Reddit",
    ),
    ShareCase(
        "Medium: title and author before the link, no subject",
        text = "“Designing for slow reading” by Jane Doe https://medium.com/@jane/designing-for-slow-reading-1a2b3c",
        subject = null,
        url = "https://medium.com/@jane/designing-for-slow-reading-1a2b3c",
        title = "“Designing for slow reading” by Jane Doe", sourceKey = "Medium",
    ),
    ShareCase(
        "Instagram: igsh share id",
        text = "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA==", subject = null,
        url = "https://www.instagram.com/reel/C1a2B3c4D5e/", sourceKey = "Instagram",
    ),
    ShareCase(
        "WhatsApp forward with text around the link",
        text = "Forwarded: you have to read this!! https://example.com/article/42 let me know what you think",
        subject = null,
        url = "https://example.com/article/42", title = "Forwarded: you have to read this!! let me know what you think",
        sourceKey = "example.com",
    ),
    ShareCase(
        "Google search redirect with empty q and the target in url",
        text = "https://www.google.com/url?sa=t&rct=j&q=&esrc=s&source=web&cd=1&url=https%3A%2F%2Fdeveloper.android.com%2Fkotlin%2Fcoroutines%3Fhl%3Den&usg=AOv",
        subject = null,
        url = "https://developer.android.com/kotlin/coroutines?hl=en", sourceKey = "android.com",
    ),
    ShareCase(
        "Google redirect on a country domain with q",
        text = "https://www.google.co.in/url?q=https://example.org/page&sa=U", subject = null,
        url = "https://example.org/page",
    ),
    ShareCase(
        "Facebook redirect wrapping a link with fbclid",
        text = "https://l.facebook.com/l.php?u=https%3A%2F%2Fwww.nytimes.com%2F2026%2F11%2F01%2Ftech%2Fstory.html%3Ffbclid%3DIwAR0abc&h=AT0xyz",
        subject = null,
        url = "https://www.nytimes.com/2026/11/01/tech/story.html", sourceKey = "nytimes.com",
    ),
    ShareCase(
        "Tumblr redirect",
        text = "https://t.umblr.com/redirect?z=https%3A%2F%2Fexample.com%2Fpost%2F1&t=abc", subject = null,
        url = "https://example.com/post/1",
    ),
    ShareCase(
        "Reddit outbound redirect to a co.uk blog",
        text = "https://out.reddit.com/t3_abc?url=https%3A%2F%2Fblog.example.co.uk%2Fp%3Fid%3D7&token=x", subject = null,
        url = "https://blog.example.co.uk/p?id=7", sourceKey = "example.co.uk",
    ),
    ShareCase(
        "Text with two URLs: the last one wins",
        text = "Compare https://a.example.com/one and https://b.example.org/two", subject = null,
        url = "https://b.example.org/two", title = "Compare and",
    ),
    ShareCase(
        "Trailing sentence punctuation",
        text = "Read this: https://example.com/post.", subject = null,
        url = "https://example.com/post",
    ),
    ShareCase(
        "Link in parentheses",
        text = "Great write-up (via https://example.com/x)", subject = null,
        url = "https://example.com/x",
    ),
    ShareCase(
        "Balanced parentheses inside a Wikipedia path stay",
        text = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)", subject = null,
        url = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)", sourceKey = "Wikipedia",
    ),
    ShareCase(
        "Uppercase scheme and host; path case kept",
        text = "HTTPS://WWW.Example.COM/Path/To", subject = null,
        url = "https://www.example.com/Path/To", normalized = "https://www.example.com/Path/To",
    ),
    ShareCase(
        "Default port dropped from the key only",
        text = "https://example.com:443/a", subject = null,
        url = "https://example.com:443/a", normalized = "https://example.com/a",
    ),
    ShareCase(
        "Non-default port kept",
        text = "http://example.com:8080/a/", subject = null,
        url = "http://example.com:8080/a/", normalized = "http://example.com:8080/a",
    ),
    ShareCase(
        "Fragment kept for opening, dropped from the key",
        text = "https://example.com/guide#section-2", subject = null,
        url = "https://example.com/guide#section-2", normalized = "https://example.com/guide",
    ),
    ShareCase(
        "Tracking params mixed with real params",
        text = "https://shop.example.com/item?id=42&utm_source=newsletter&color=red&gclid=abc&fbclid=x&UTM_Campaign=y",
        subject = null,
        url = "https://shop.example.com/item?id=42&color=red", normalized = "https://shop.example.com/item?color=red&id=42",
        sourceKey = "example.com",
    ),
    ShareCase(
        "GitHub repository",
        text = "https://github.com/JetBrains/kotlin", subject = "GitHub - JetBrains/kotlin",
        url = "https://github.com/JetBrains/kotlin", sourceKey = "GitHub", type = ContentType.Repository,
    ),
    ShareCase(
        "URL only in the subject",
        text = "Look at this", subject = "https://example.com/from-subject",
        url = "https://example.com/from-subject", title = "Look at this",
    ),
    ShareCase(
        "Bare URL: the title falls back to it",
        text = "https://example.com/bare", subject = null,
        url = "https://example.com/bare", title = "https://example.com/bare",
    ),
    ShareCase(
        "Curly quotes around the link",
        text = "“https://example.com/q”", subject = null,
        url = "https://example.com/q",
    ),
    ShareCase(
        "Non-http schemes only",
        text = "Call tel:+123, mail mailto:a@b.com or get ftp://files.example.com/a and intent://scan/#Intent;end",
        subject = null, url = null,
    ),
    ShareCase(
        "Hash-routed app: a fragment starting with / stays in the key",
        text = "https://app.example.com/#/inbox/42", subject = null,
        url = "https://app.example.com/#/inbox/42", normalized = "https://app.example.com/#/inbox/42",
    ),
    ShareCase(
        "Hashbang fragment stays in the key",
        text = "https://old.example.com/#!/page/7", subject = null,
        url = "https://old.example.com/#!/page/7", normalized = "https://old.example.com/#!/page/7",
    ),
    ShareCase(
        "YouTube feature and pp stripped",
        text = "https://www.youtube.com/watch?v=abc&feature=share&pp=ygUFa290bGlu", subject = null,
        url = "https://www.youtube.com/watch?v=abc",
    ),
    ShareCase(
        "youtu.be feature stripped",
        text = "https://youtu.be/xyz?feature=shared", subject = null, url = "https://youtu.be/xyz",
    ),
    ShareCase(
        "LinkedIn trk stripped, ref kept",
        text = "https://www.linkedin.com/posts/jane_kotlin-abc?trk=public_post&ref=feed", subject = null,
        url = "https://www.linkedin.com/posts/jane_kotlin-abc?ref=feed",
    ),
    ShareCase(
        "_gl and spm stripped anywhere, feature kept outside YouTube",
        text = "https://shop.example.com/p?_gl=1*abc&spm=a2g0o&feature=wide&ref=home", subject = null,
        url = "https://shop.example.com/p?feature=wide&ref=home",
    ),
    ShareCase(
        "User info dropped from the URL and the key",
        text = "https://user:secret@example.com/a", subject = null,
        url = "https://example.com/a", normalized = "https://example.com/a",
    ),
    ShareCase(
        "Backslash in the authority is rejected",
        text = "https://attacker.com\\@trusted.com/login", subject = null, url = null,
    ),
    ShareCase(
        "Unparsable last URL falls back to an earlier one",
        text = "Mirror https://good.example.com/a or https://bad.example.com\\@x.com", subject = null,
        url = "https://good.example.com/a",
    ),
    ShareCase(
        "Unparsable URL in the text falls back to the subject",
        text = "https://bad.example.com\\@x.com", subject = "https://example.com/from-subject",
        url = "https://example.com/from-subject",
    ),
    ShareCase(
        "Full-width full stop after the link",
        text = "看看这个 https://example.com/zh。", subject = null, url = "https://example.com/zh",
    ),
    ShareCase(
        "Full-width parentheses and corner brackets with no spaces",
        text = "（https://example.com/p）「https://example.com/q」看看", subject = null, url = "https://example.com/q",
    ),
    ShareCase(
        "Full-width comma, colon and question mark with no spaces",
        text = "链接：https://example.com/r，还有https://example.com/s？", subject = null, url = "https://example.com/s",
    ),
    ShareCase(
        "Full-width brackets around a link with text after",
        text = "【https://example.com/t】很好", subject = null, url = "https://example.com/t",
    ),
    ShareCase("No link at all", text = "Just some text to remember", subject = "A note", url = null),
    ShareCase("Empty share", text = null, subject = null, url = null),
    ShareCase("Scheme with no host", text = "see https:// for details", subject = null, url = null),
)

class LinkCapturePolicyTest {
    private fun capture(case: ShareCase) = LinkCapturePolicy.capture(SharedPayload(case.text, case.subject, "app", T0))

    @Test
    fun syntheticShareStrings() {
        assertEquals(true, SYNTHETIC_SHARES.size >= 20)
        for (case in SYNTHETIC_SHARES) {
            val result = capture(case)
            if (case.url == null) {
                assertEquals(LinkCapture.NoLink, result, case.name)
                continue
            }
            val captured = assertIs<LinkCapture.Captured>(result, case.name)
            assertEquals(case.url, captured.url, case.name)
            case.title?.let { assertEquals(it, captured.title, case.name) }
            case.normalized?.let { assertEquals(it, captured.normalizedUrl, case.name) }
            case.sourceKey?.let { assertEquals(it, captured.sourceKey, case.name) }
            case.type?.let { assertEquals(it, captured.contentType, case.name) }
        }
    }

    @Test
    fun normalizationIsIdempotent() {
        for (case in SYNTHETIC_SHARES) {
            val captured = capture(case) as? LinkCapture.Captured ?: continue
            assertEquals(captured.normalizedUrl, LinkCapturePolicy.normalize(captured.normalizedUrl), case.name)
            assertEquals(captured.normalizedUrl, LinkCapturePolicy.normalize(captured.url), case.name)
        }
    }

    @Test
    fun dedupeEquivalenceClasses() {
        val classes = listOf(
            listOf(
                "https://example.com/a",
                "HTTPS://EXAMPLE.COM/a",
                "https://example.com:443/a",
                "https://example.com/a/",
                "https://example.com/a#top",
                "https://example.com/a?utm_source=x&fbclid=y",
                "https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fa",
                "https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Fa%2F%3Futm_medium%3Dsocial",
            ),
            listOf(
                "https://example.com/?b=2&a=1",
                "https://example.com?a=1&b=2",
                "https://example.com:443/?a=1&b=2#f",
            ),
            listOf("http://example.com", "http://example.com/", "http://EXAMPLE.com:80/"),
        )
        val keys = classes.map { members ->
            val normalized = members.map { LinkCapturePolicy.normalize(it) }.toSet()
            assertEquals(1, normalized.size, "$members -> $normalized")
            normalized.single()
        }
        assertEquals(keys.size, keys.toSet().size, "classes stay distinct")

        val distinct = listOf(
            "http://example.com/a" to "https://example.com/a",
            "https://example.com/A" to "https://example.com/a",
            "https://example.com/a?id=1" to "https://example.com/a?id=2",
            "https://www.youtube.com/watch?v=x&t=10" to "https://www.youtube.com/watch?v=x&t=20",
            "https://www.example.com/a" to "https://example.com/a",
        )
        for ((a, b) in distinct) assertNotEquals(LinkCapturePolicy.normalize(a), LinkCapturePolicy.normalize(b), "$a vs $b")
    }

    @Test
    fun normalizeRejectsNonHttpInput() {
        for (input in listOf("ftp://example.com/a", "mailto:a@b.com", "example.com", "", "https://", "https://exa mple.com")) {
            assertEquals(null, LinkCapturePolicy.normalize(input), input)
        }
    }

    @Test
    fun manualAddValidation() {
        val cases = listOf(
            "" to null,
            "   " to null,
            "not a link" to null,
            "hello" to null,
            "example" to null,
            "ftp://example.com/x" to null,
            "http://" to null,
            "example.com/x" to "https://example.com/x",
            "  https://example.com  " to "https://example.com",
            "www.example.com/a?utm_source=x&id=1" to "https://www.example.com/a?id=1",
            "http://sub.example.org:8080/p" to "http://sub.example.org:8080/p",
            "example.com?x=1" to "https://example.com?x=1",
            "web.archive.org/web/2024/https://example.com/" to "https://web.archive.org/web/2024/https://example.com/",
            "https://attacker.com\\@trusted.com" to null,
        )
        for ((input, expected) in cases) {
            val result = LinkCapturePolicy.validateManual(input)
            when {
                input.isBlank() -> assertEquals(ManualLink.Empty, result, input)
                expected == null -> assertEquals(ManualLink.NotALink, result, input)
                else -> {
                    val valid = assertIs<ManualLink.Valid>(result, input)
                    assertEquals(expected, valid.capture.url, input)
                    assertEquals(expected, valid.capture.title, "a typed link's provisional title is the URL")
                }
            }
        }
    }

    @Test
    fun hostDerivesDomainSiteNameSourceAndType() {
        data class Host(val url: String, val domain: String, val siteName: String, val sourceKey: String, val type: ContentType)
        val cases = listOf(
            Host("https://www.youtube.com/watch?v=1", "youtube.com", "youtube.com", "YouTube", ContentType.Video),
            Host("https://m.youtube.com/watch?v=1", "m.youtube.com", "youtube.com", "YouTube", ContentType.Video),
            Host("https://vimeo.com/1", "vimeo.com", "vimeo.com", "Vimeo", ContentType.Video),
            Host("https://gitlab.com/a/b", "gitlab.com", "gitlab.com", "GitLab", ContentType.Repository),
            Host("https://blog.medium.com/x", "blog.medium.com", "medium.com", "Medium", ContentType.Article),
            Host("https://news.bbc.co.uk/x", "news.bbc.co.uk", "bbc.co.uk", "bbc.co.uk", ContentType.Article),
            Host("https://dev.to/x", "dev.to", "dev.to", "dev.to", ContentType.Article),
            Host("http://192.168.1.10:8080/x", "192.168.1.10", "192.168.1.10", "192.168.1.10", ContentType.Article),
        )
        for (case in cases) {
            val captured = assertIs<LinkCapture.Captured>(LinkCapturePolicy.capture(SharedPayload(case.url, null, null, T0)))
            assertEquals(case.domain, captured.domain, case.url)
            assertEquals(case.siteName, captured.siteName, case.url)
            assertEquals(case.sourceKey, captured.sourceKey, case.url)
            assertEquals(case.type, captured.contentType, case.url)
        }
    }

    @Test
    fun provisionalTitleIsCappedAt500Characters() {
        val long = "x".repeat(600)
        val captured = assertIs<LinkCapture.Captured>(LinkCapturePolicy.capture(SharedPayload("https://example.com", long, null, T0)))
        assertEquals(500, captured.title.length)
    }

    @Test
    fun manyTrailingBracketsAreTrimmedInLinearTime() {
        val text = "https://a.co/" + ")".repeat(200_000)
        val captured = assertIs<LinkCapture.Captured>(LinkCapturePolicy.capture(SharedPayload(text, null, null, T0)))
        assertEquals("https://a.co/", captured.url)
    }

    @Test
    fun percentDecodingHandlesUtf8AndMalformedEscapes() {
        assertEquals("café ü", percentDecode("caf%C3%A9+%C3%BC"))
        assertEquals("100%", percentDecode("100%"))
        assertEquals("%zz", percentDecode("%zz"))
        assertEquals("%-1", percentDecode("%-1"))
        assertEquals("a😀 A", percentDecode("a😀+%41"))
    }
}
