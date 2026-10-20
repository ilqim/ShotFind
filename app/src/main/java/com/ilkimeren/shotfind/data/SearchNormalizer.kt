package com.ilkimeren.shotfind.data

import java.text.Normalizer
import java.util.Locale

/**
 * Makes text easy to compare for search: lowercase, Turkish letters mapped to
 * their plain Latin forms ("Şekerli İçecek" -> "sekerli icecek").
 * Used both for stored text and for the user's query.
 */
object SearchNormalizer {

    private val combiningMarks = Regex("\\p{Mn}+")

    fun normalize(text: String): String {
        // Handle the dotted/dotless i family first; generic lowercasing gets these wrong
        val fixedI = text
            .replace('İ', 'i')
            .replace('I', 'i')
            .replace('ı', 'i')
        val lower = fixedI.lowercase(Locale.ROOT)
        // Split letters like "ş" into "s" + accent mark, then drop the marks
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
    }

    /** Builds the stored search text from everything a screenshot can be found by. */
    fun buildSearchText(ocrText: String?, note: String?, sourceApp: String?): String? =
        normalize(listOfNotNull(ocrText, note, sourceApp).joinToString("\n"))
            .ifBlank { null }
}