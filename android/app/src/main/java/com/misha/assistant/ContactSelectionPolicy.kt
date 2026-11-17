package com.misha.assistant

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
}
