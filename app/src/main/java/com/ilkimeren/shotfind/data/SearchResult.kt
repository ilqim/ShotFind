package com.ilkimeren.shotfind.data

/** A screenshot that matched a search, with its text for building the preview snippet. */
data class SearchResult(
    val screenshot: Screenshot,
    val ocrText: String?
)