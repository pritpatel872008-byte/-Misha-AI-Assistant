package com.misha.assistant

internal object SearchQueryParser {
    private val patterns = listOf(
        Regex("^(?:search(?: the web)? for|search|look up|find online)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^what is the latest on\\s+(.+)$", RegexOption.IGNORE_CASE)
    )
    fun parse(input: String): String? {
        val value = input.trim().removeSuffix(".")
        return patterns.firstNotNullOfOrNull { it.matchEntire(value)?.groupValues?.get(1)?.trim() }
            ?.takeIf { it.length >= 2 && it.length <= 200 }
    }
}

internal object AppCommandParser {
    private val pattern = Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE)
    fun parse(input: String): String? = pattern.matchEntire(input.trim().removeSuffix("."))?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}

internal data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)
