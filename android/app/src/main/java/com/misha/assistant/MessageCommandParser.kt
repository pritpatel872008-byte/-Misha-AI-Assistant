package com.misha.assistant

import java.util.Locale

internal data class MessageRequest(
    val recipient: String,
    val appName: String?,
    val body: String
)

internal object MessageCommandParser {
    private val patterns = listOf(
        Regex("^(?:send|message|text)\\s+(?:a\\s+)?message\\s+to\\s+(.+?)\\s+(?:on|via|using)\\s+([a-z0-9][a-z0-9 ._-]*?)(?:\\s+(?:saying|that says|with text)|:)\\s*(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:send|message|text)\\s+(.+?)\\s+(?:a\\s+)?message\\s+(?:on|via|using)\\s+([a-z0-9][a-z0-9 ._-]*?)(?:\\s+(?:saying|that says|with text)|:)\\s*(.+)$", RegexOption.IGNORE_CASE)
    )
    private val genericPatterns = listOf(
        Regex("^(?:send|message|text)\\s+(?:a\\s+)?message\\s+to\\s+(.+?)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:send|message|text)\\s+(.+?)\\s+(?:a\\s+)?message\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    )

    fun parse(input: String): MessageRequest? {
        val value = input.trim().removeSuffix(".")
        for ((index, pattern) in patterns.withIndex()) {
            val match = pattern.matchEntire(value) ?: continue
            val recipient = match.groupValues[1].trim().trim(',', '.', ' ')
            val app = match.groupValues[2].trim().lowercase(Locale.ROOT)
            val body = match.groupValues[3].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) return MessageRequest(recipient, app, body)
        }
        for (pattern in genericPatterns) {
            val match = pattern.matchEntire(value) ?: continue
            val recipient = match.groupValues[1].trim().trim(',', '.', ' ')
            val body = match.groupValues[2].trim()
            if (recipient.isNotBlank() && body.isNotBlank()) return MessageRequest(recipient, null, body)
        }
        return null
    }
}
