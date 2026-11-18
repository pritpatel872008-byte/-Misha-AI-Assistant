package com.misha.assistant

import java.util.Locale

internal sealed interface ContactResolution {
    data object NoMatch : ContactResolution
    data class Single(val contact: ContactMatch) : ContactResolution
    data class Multiple(val contacts: List<ContactMatch>) : ContactResolution
}

/**
 * Shared recipient-selection policy used by both calling and every message app.
 * It intentionally returns only the matches supplied by ContactRepository.
 */
internal object ContactSelectionPolicy {
    fun resolve(matches: List<ContactMatch>): ContactResolution =
        when (matches.size) {
            0 -> ContactResolution.NoMatch
            1 -> ContactResolution.Single(matches.first())
            else -> ContactResolution.Multiple(matches.toList())
        }

    /** Contact lookup is exact-name only; "Rahul" must not match "Rahul Bhai". */
    fun isExactNameMatch(displayName: String, query: String): Boolean =
        normalize(displayName) == normalize(query)

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).trim()
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(Regex("\\s+"), " ")
}
