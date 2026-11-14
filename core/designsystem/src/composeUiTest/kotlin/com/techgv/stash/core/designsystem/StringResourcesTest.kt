package com.techgv.stash.core.designsystem

import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.allPluralStringResources
import com.techgv.stash.core.resources.allStringResources
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards placeholder mismatches: a `%n$` with no argument throws at runtime. Every string and plural loads with as many
 * dummy arguments as its English text uses, and every translation uses exactly the English placeholders.
 */
class StringResourcesTest {
    private val placeholder = Regex("""%(\d+)\$[ds]""")
    private fun indices(text: String) = placeholder.findAll(text).map { it.groupValues[1].toInt() }.toSet()

    // Compose resources pack each locale's strings as lines "string|key|base64" / "plurals|key|cat:base64,…".
    // shortcut: reads the packed format of components-resources 1.12; update the parser if the plugin changes it.
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun locale(dir: String): Map<String, List<String>> {
        val files = listOf("strings", "strings_shelf", "strings_search", "strings_reader", "strings_settings", "strings_m4_a11y", "strings_m4_wiring")
        return files.flatMap { file ->
            val text = try {
                Res.readBytes("$dir/$file.commonMain.cvr").decodeToString()
            } catch (e: Exception) {
                return@flatMap emptyList()
            }
            text.lines().filter { '|' in it }.map { line ->
                val (type, key, data) = line.split('|', limit = 3)
                key to if (type == "plurals") {
                    data.split(',').map { Base64.decode(it.substringAfter(':')).decodeToString() }
                } else {
                    listOf(Base64.decode(data).decodeToString())
                }
            }
        }.toMap()
    }

    @Test
    fun everyStringLoadsWithItsArgumentsInEveryLocale() = runTest {
        val english = locale("values")
        assertTrue(english.size >= Res.allStringResources.size + Res.allPluralStringResources.size, "English strings were not read")
        val needed = english.mapValues { (_, forms) -> forms.flatMap(::indices).maxOrNull() ?: 0 }
        for ((key, res) in Res.allStringResources) {
            val args = Array<Any>(needed.getValue(key)) { "x${it + 1}" }
            assertFalse(placeholder.containsMatchIn(getString(res, *args)), key)
        }
        for ((key, res) in Res.allPluralStringResources) {
            val args = Array<Any>(needed.getValue(key)) { it + 1 }
            for (n in listOf(0, 1, 2, 5)) assertFalse(placeholder.containsMatchIn(getPluralString(res, n, *args)), "$key ($n)")
        }
        for (dir in listOf("values-es", "values-hi", "values-ta")) {
            val translated = locale(dir)
            assertTrue(translated.isNotEmpty(), "$dir was not read")
            for ((key, forms) in translated) {
                val englishForms = english[key] ?: fail("$dir/$key has no English string")
                val expected = englishForms.flatMap(::indices).toSet()
                for (form in forms) {
                    assertEquals(expected, indices(form), "$dir/$key uses other placeholders than English")
                    val args = List(needed.getValue(key)) { "x" }
                    assertFalse(placeholder.containsMatchIn(placeholder.replace(form) { args[it.groupValues[1].toInt() - 1] }), "$dir/$key")
                }
            }
        }
    }
}
