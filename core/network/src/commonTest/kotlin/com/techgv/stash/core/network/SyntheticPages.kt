package com.techgv.stash.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/** Client over a MockEngine; [handler] sees every request, including each redirect hop. */
internal fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
    createStashHttpClient(MockEngine(handler))

internal fun MockRequestHandleScope.html(
    body: String,
    contentType: String = "text/html; charset=utf-8",
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(body, status, headersOf(HttpHeaders.ContentType, contentType))

internal fun MockRequestHandleScope.redirect(location: String, status: HttpStatusCode = HttpStatusCode.Found): HttpResponseData =
    respond("", status, headersOf(HttpHeaders.Location, location))

/** Hand-written pages shaped like real ones. None of them is a copy of a real site. */
internal object SyntheticPages {
    /** Exactly [length] chars of prose with no leading or trailing space, for threshold edges. */
    fun prose(length: Int): String {
        val text = "the keeper writes the weather and the ships in pencil ".repeat(length / 50 + 1).take(length)
        return if (text.last() == ' ') text.dropLast(1) + "s" else text
    }

    /** An `<article>` page whose paragraphs have exactly the given lengths, with no whitespace between tags. */
    fun articleOf(vararg paragraphLengths: Int, extra: String = ""): String =
        "<html><head><title>Edge</title></head><body><article>$extra" +
            paragraphLengths.joinToString("") { "<p>${prose(it)}</p>" } + "</article></body></html>"

    val bodyParagraphs = listOf(
        "The lighthouse keeper rose before dawn, as she had every day for eleven years, and climbed the ninety-two " +
            "steps to the lamp room with a thermos of tea and a notebook full of weather.",
        "Most visitors imagine the work is lonely, but the keeper describes it differently: a conversation with the " +
            "sea, the gulls, the radio operators, and the ships that pass without ever knowing her name.",
        "When the automated beacon arrived last spring, the council promised that nothing would change. Within a " +
            "month, the logbook had gone quiet, and the village realised how much it had relied on her daily notes.",
        "She still walks the headland each evening, counting the vessels on the horizon, and writes their names in " +
            "pencil, because, as she puts it, somebody ought to remember who came by.",
    )

    val bodyText: String = bodyParagraphs.joinToString(" ")

    /** A news article with page chrome, a share bar, a sidebar, comments and a footer around the body. */
    val newsArticle = """
        <!doctype html>
        <html lang="en-GB">
        <head>
          <title>The last lighthouse keeper | Coastal Times</title>
          <meta property="og:title" content="The last lighthouse keeper">
          <meta property="og:description" content="Eleven years of dawns on the headland.">
          <meta property="og:site_name" content="Coastal Times">
          <script>window.tracker = 'pageview';</script>
          <style>.ad { display: block; }</style>
        </head>
        <body>
          <header class="site-header"><a href="/">Coastal Times</a><nav><a href="/news">News</a><a href="/sport">Sport</a></nav></header>
          <div class="share-bar"><a href="https://social.example/share">Share this story</a></div>
          <main>
            <article>
              <h1>The last lighthouse keeper</h1>
              ${bodyParagraphs.joinToString("\n") { "<p>$it</p>" }}
            </article>
            <aside class="sidebar"><h3>Most read</h3><ul><li><a href="/a">Sidebar story one</a></li><li><a href="/b">Sidebar story two</a></li></ul></aside>
            <section id="comments"><h3>Comments</h3><p>Commenter: What a lovely piece about the keeper and her lamp!</p></section>
          </main>
          <footer><p>Footer: © Coastal Times. All rights reserved.</p></footer>
        </body>
        </html>
    """.trimIndent()

    /** A section front: nothing but headline links. */
    val linkFarm = """
        <html><head><title>Coastal Times — Home</title></head>
        <body><main>
          ${(1..40).joinToString("\n") { "<div class=\"teaser\"><a href=\"/story/$it\">Headline number $it about the coast and the sea</a></div>" }}
        </main></body></html>
    """.trimIndent()

    /** A section front: many teasers, each with a short unlinked blurb, so links are well under half the text. */
    val sectionFront = """
        <html><head><title>Coastal Times — Home</title></head>
        <body><main>
          ${(1..30).joinToString("\n") {
            "<div class=\"card\"><h3><a href=\"/story/$it\">Story $it</a></h3>" +
                "<p>Blurb for story $it: a single short line of teaser text under the headline, as fronts do.</p></div>"
        }}
        </main></body></html>
    """.trimIndent()

    val stub = """
        <html><head><title>Short note</title></head>
        <body><article><p>This page has moved. Please update your bookmarks.</p></article></body></html>
    """.trimIndent()

    val cookieWall = """
        <html><head><title>Before you continue</title></head>
        <body>
          <div class="consent-wall">
            <h1>We value your privacy</h1>
            <p>We and our partners use cookies to store and access information on your device, to personalise content and
            ads, and to measure audiences. You can accept or manage your choices below.</p>
            <button>Accept all</button><button>Manage options</button>
          </div>
        </body></html>
    """.trimIndent()

    /** An article body full of things the sanitizer must remove, plus relative links and images. */
    val hostileArticle = """
        <html><head><title>Hostile</title></head>
        <body><article>
          <p onclick="steal()">${bodyParagraphs[0]} <a href="javascript:alert(1)">bad link</a></p>
          <script>document.write('injected')</script>
          <style>p { color: red }</style>
          <iframe src="https://ads.example/frame"></iframe>
          <p style="color: red">${bodyParagraphs[1]} <a href="/archive/older">older story</a></p>
          <img src="/images/keeper.jpg" alt="The keeper" onerror="steal()">
          <img src="https://pixel.example/track.gif" width="1" height="1">
          <img src="data:image/png;base64,iVBORw0KGgo=" alt="inline">
          <img src="http://insecure.example/photo.jpg" alt="insecure">
          <p>${bodyParagraphs[2]} <a href="../related/next">next</a></p>
        </article></body></html>
    """.trimIndent()
}
