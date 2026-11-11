package com.techgv.stash.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons ported 1:1 from the design's inline 24×24 stroke SVGs. */
object StashIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx} ${y}h${w - 2 * rx}a$rx $rx 0 0 1 $rx ${rx}v${h - 2 * rx}a$rx $rx 0 0 1 ${-rx} ${rx}" +
            "h${-(w - 2 * rx)}a$rx $rx 0 0 1 ${-rx} ${-rx}v${-(h - 2 * rx)}a$rx $rx 0 0 1 $rx ${-rx}z"

    private fun icon(name: String, stroke: Float, vararg d: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            d.forEach {
                addPath(
                    addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private val cache = mutableMapOf<String, ImageVector>()
    private fun get(key: String, stroke: Float, vararg d: String) =
        cache.getOrPut("$key@$stroke") { icon(key, stroke, *d) }

    fun archive(s: Float = 1.8f) = get("archive", s, rect(3f, 4f, 18f, 5f, 1f), "M5 9v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9M10 13h4")
    fun sliders(s: Float = 1.7f) = get(
        "sliders", s, "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0",
        circle(16f, 6f, 2f), circle(10f, 12f, 2f), circle(18f, 18f, 2f),
    )
    fun share(s: Float = 1.8f) = get(
        "share", s, circle(18f, 5f, 2.5f), circle(6f, 12f, 2.5f), circle(18f, 19f, 2.5f),
        "M8.2 10.8l7.6-4.4M8.2 13.2l7.6 4.4",
    )
    fun search(s: Float = 1.8f) = get("search", s, circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    fun hand(s: Float = 1.8f) = get(
        "hand", s,
        "M9 11V5a2 2 0 0 1 4 0v5l4.5.9a2 2 0 0 1 1.6 2.2l-.6 4.3A3 3 0 0 1 15.5 20H11a3 3 0 0 1-2.4-1.2L5 14l1.3-1.3a1.5 1.5 0 0 1 2-.1z",
    )
    fun close(s: Float = 2.4f) = get("close", s, "M6 6l12 12M18 6L6 18")
    fun check(s: Float = 2f) = get("check", s, "M5 12l5 5 9-10")
    fun dot(s: Float = 2f) = get("dot", s, circle(12f, 12f, 5f))
    fun torn(s: Float = 2f) = get("torn", s, "M6 3h12v8l-2 2 2 2-2 2 2 2v2H6z")
    fun restore(s: Float = 2f) = get("restore", s, "M4 12a8 8 0 1 0 2.3-5.7M4 4v5h5")
    fun trash(s: Float = 2f) = get("trash", s, "M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13")
    fun chevronRight(s: Float = 1.8f) = get("chevR", s, "M9 6l6 6-6 6")
    fun back(s: Float = 1.8f) = get("back", s, "M15 5l-7 7 7 7")
    fun bookmarkAdd(s: Float = 1.8f) = get("bmAdd", s, "M6 3h12v18l-6-4-6 4z", "M12 8v6M9 11h6")
    fun clipboard(s: Float = 1.8f) = get("clip", s, rect(7f, 4f, 10f, 16f, 2f), "M10 4h4v2h-4z")
    fun alert(s: Float = 2f) = get("alert", s, circle(12f, 12f, 9f), "M12 7v6M12 16.5v.5")
    fun clock(s: Float = 2f) = get("clock", s, circle(12f, 12f, 8f), "M12 8v4l3 2")
    fun github(s: Float = 1.7f) = get(
        "github", s,
        "M9 19c-4 1.5-4-2-6-2.5M15 21v-3.5a3 3 0 0 0-.9-2.4c3-.3 6-1.5 6-6.6a5.2 5.2 0 0 0-1.4-3.6 4.8 4.8 0 0 0-.1-3.6s-1.1-.3-3.7 1.4a12.6 12.6 0 0 0-6.6 0C5.7 1.1 4.6 1.4 4.6 1.4a4.8 4.8 0 0 0-.1 3.6A5.2 5.2 0 0 0 3 8.6c0 5.1 3 6.3 6 6.6a3 3 0 0 0-.9 2.4V21",
    )
    fun external(s: Float = 1.8f) = get("ext", s, "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5")
    fun copy(s: Float = 1.8f) = get("copy", s, rect(8f, 8f, 12f, 12f, 2f), "M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3")

    const val FLAME = "M12 1.8c1.9 2.3 2.8 4 2.8 5.4A2.8 2.8 0 0 1 12 10a2.8 2.8 0 0 1-2.8-2.8c0-1.4.9-3.1 2.8-5.4z"
    const val FLAME_CORE = "M12 5c.9 1.1 1.3 1.9 1.3 2.6a1.3 1.3 0 0 1-2.6 0c0-.7.4-1.5 1.3-2.6z"
}
