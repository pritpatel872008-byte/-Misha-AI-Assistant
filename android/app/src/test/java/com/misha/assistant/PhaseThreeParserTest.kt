package com.misha.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhaseThreeParserTest {
    @Test fun parsesSearchCommands() {
        assertEquals("latest cybersecurity news", SearchQueryParser.parse("Search the web for latest cybersecurity news"))
        assertEquals("Android 16 features", SearchQueryParser.parse("look up Android 16 features"))
    }

    @Test fun rejectsIncompleteSearch() {
        assertNull(SearchQueryParser.parse("search"))
    }

    @Test fun parsesAppLaunchCommands() {
        assertEquals("WhatsApp", AppCommandParser.parse("Open WhatsApp"))
        assertEquals("YouTube", AppCommandParser.parse("launch YouTube"))
    }

    @Test fun rejectsNonLaunchCommands() {
        assertNull(AppCommandParser.parse("Turn on the flashlight"))
    }
}
