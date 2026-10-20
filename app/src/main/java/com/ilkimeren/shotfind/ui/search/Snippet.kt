package com.ilkimeren.shotfind.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.ilkimeren.shotfind.data.SearchNormalizer

/**
 * Cuts a short piece of [text] around the first match of [query] and highlights
 * every matching word in it.
 */
fun buildSnippet(
    text: String,
    query: String,
    highlight: SpanStyle,
    radius: Int = 40
): AnnotatedString {
    val flat = text.replace('\n', ' ')
    val normalized = SearchNormalizer.normalize(flat)
    val words = SearchNormalizer.normalize(query)
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    // Normalizing usually keeps the length the same, so positions line up.
    // If it doesn't (rare), just show the beginning without highlighting.
    if (normalized.length != flat.length) {
        return AnnotatedString(flat.take(radius * 2))
    }

    val firstMatch = words
        .mapNotNull { word -> normalized.indexOf(word).takeIf { it >= 0 } }
        .minOrNull()
        ?: return AnnotatedString(flat.take(radius * 2))

    val start = (firstMatch - radius).coerceAtLeast(0)
    val end = (firstMatch + radius * 2).coerceAtMost(flat.length)
    val window = normalized.substring(start, end)

    return buildAnnotatedString {
        if (start > 0) append("…")
        val offset = length
        append(flat.substring(start, end))
        if (end < flat.length) append("…")

        for (word in words) {
            var index = window.indexOf(word)
            while (index >= 0) {
                addStyle(highlight, offset + index, offset + index + word.length)
                index = window.indexOf(word, index + word.length)
            }
        }
    }
}