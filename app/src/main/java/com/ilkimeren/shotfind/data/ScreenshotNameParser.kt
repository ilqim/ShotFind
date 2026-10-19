package com.ilkimeren.shotfind.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ScreenshotNameParser {

    // Samsung formats (separator changed between One UI versions):
    //   Screenshot_20261018-005226_Instagram.jpg
    //   Screenshot_20261020_000137_YouTube.jpg
    // The app part at the end is optional.
    private val samsungPattern = Regex(
        """^Screenshot_(\d{8})[-_](\d{6})(?:_(.+))?\.(jpe?g|png)$""",
        RegexOption.IGNORE_CASE
    )

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /** Returns the app name at the end of the file name, or null if there is none. */
    fun parseSourceApp(displayName: String): String? =
        samsungPattern.find(displayName)
            ?.groups?.get(3)
            ?.value
            ?.takeIf { it.isNotBlank() }

    /** Returns the capture time (epoch ms) written in the file name, or null. */
    fun parseCapturedAt(displayName: String): Long? {
        val match = samsungPattern.find(displayName) ?: return null
        val date = match.groupValues[1]
        val time = match.groupValues[2]
        return try {
            LocalDateTime.parse(date + time, timestampFormat)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}