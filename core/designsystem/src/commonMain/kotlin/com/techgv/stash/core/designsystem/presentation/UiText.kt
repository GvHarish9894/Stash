package com.techgv.stash.core.designsystem.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text that is resolved from string resources where it is shown, so ViewModels and pure mappers never hold a language.
 * An argument may itself be a [UiText]; it is resolved first.
 */
@Immutable
sealed interface UiText {
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    /** [count] picks the plural form; [args] fill the placeholders (usually the count first). */
    data class Plural(val res: PluralStringResource, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** User data shown as it is, such as a source name. */
    data class Plain(val value: String) : UiText

    /** [parts] joined with [separator], for lists inside a sentence. */
    data class Joined(val parts: List<UiText>, val separator: UiText) : UiText
}

fun text(res: StringResource, vararg args: Any): UiText = UiText.Res(res, args.toList())

fun plural(res: PluralStringResource, count: Int, vararg args: Any): UiText =
    UiText.Plural(res, count, if (args.isEmpty()) listOf(count) else args.toList())

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(res, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Plural -> pluralStringResource(res, count, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Plain -> value
    is UiText.Joined -> {
        val separator = separator.resolve()
        parts.map { it.resolve() }.joinToString(separator)
    }
}

/** Outside composition: notifications, announcements, tests. */
suspend fun UiText.load(): String = when (this) {
    is UiText.Res -> getString(res, *args.map { if (it is UiText) it.load() else it }.toTypedArray())
    is UiText.Plural -> getPluralString(res, count, *args.map { if (it is UiText) it.load() else it }.toTypedArray())
    is UiText.Plain -> value
    is UiText.Joined -> {
        val separator = separator.load()
        parts.map { it.load() }.joinToString(separator)
    }
}
