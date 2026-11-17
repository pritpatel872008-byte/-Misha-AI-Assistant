package com.misha.assistant

import java.util.Locale

internal object WakeWordPhraseMatcher {
    fun matches(input: String): Boolean {
        val normalized = input.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return Regex("(^| )hey misha($| )").containsMatchIn(normalized)
    }
}
