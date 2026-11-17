package com.misha.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBrainTest {
    @Test fun parsesFlashlightCommands() {
        assertEquals(MishaCommand.Torch(true), LocalBrain.parse("Turn on the flashlight"))
        assertEquals(MishaCommand.Torch(false), LocalBrain.parse("Turn off the flashlight"))
        assertEquals(MishaCommand.Torch(null), LocalBrain.parse("Is the flashlight on?"))
    }

    @Test fun parsesSearchAndAppCommands() {
        assertEquals(MishaCommand.Search("cybersecurity news"), LocalBrain.parse("Search the web for cybersecurity news"))
        assertEquals(MishaCommand.OpenApp("YouTube"), LocalBrain.parse("Open YouTube"))
    }

    @Test fun parsesCallingCommand() {
        assertEquals(MishaCommand.Call("Rahul"), LocalBrain.parse("Call Rahul"))
    }

    @Test fun parsesWhatsAppMessageCommand() {
        assertEquals(MishaCommand.Message("Rahul", "WhatsApp", "Hello"), LocalBrain.parse("Send a message to Rahul on WhatsApp saying Hello"))
    }

    @Test fun parsesTelegramMessageWithColon() {
        assertEquals(MishaCommand.Message("Rahul", "Telegram", "I am coming"), LocalBrain.parse("Message Rahul on Telegram: I am coming"))
    }

    @Test fun parsesMessageWithoutExplicitApp() {
        assertEquals(MishaCommand.Message("Rahul", "Choose app", "Call me"), LocalBrain.parse("Message Rahul saying Call me"))
    }

    @Test fun parsesOrdinalSelection() {
        assertEquals(2, LocalBrain.ordinal("number two"))
        assertEquals(3, LocalBrain.ordinal("3"))
        assertEquals(1, LocalBrain.ordinal("first"))
    }
    @Test fun messageRecipientResolutionIsSharedAcrossMessagingApps() {
        val commands = listOf(
            "Message Rahul on WhatsApp saying Hello" to "WhatsApp",
            "Message Rahul on WhatsApp Business saying Hello" to "WhatsApp Business",
            "Message Rahul on Telegram saying Hello" to "Telegram",
            "Message Rahul on Signal saying Hello" to "Signal",
            "Message Rahul on Messenger saying Hello" to "Messenger",
            "Message Rahul on Messages saying Hello" to "Messages"
        )
        commands.forEach { (input, app) ->
            assertEquals(
                MishaCommand.Message("Rahul", app, "Hello"),
                LocalBrain.parse(input)
            )
        }
    }

    @Test fun supportedMessagingAppsHaveExplicitRoutes() {
        assertEquals(MessageAppRoute.WHATSAPP, MessageAppRouter.routeFor("WhatsApp"))
        assertEquals(MessageAppRoute.WHATSAPP_BUSINESS, MessageAppRouter.routeFor("WhatsApp Business"))
        assertEquals(MessageAppRoute.TELEGRAM, MessageAppRouter.routeFor("Telegram"))
        assertEquals(MessageAppRoute.SIGNAL, MessageAppRouter.routeFor("Signal"))
        assertEquals(MessageAppRoute.MESSENGER, MessageAppRouter.routeFor("Messenger"))
        assertEquals(MessageAppRoute.SMS, MessageAppRouter.routeFor("Google Messages"))
    }

    @Test fun telegramDeepLinkContainsSelectedNumberAndDraftText() {
        assertEquals(
            "tg://resolve?phone=%2B919876543210&text=Hello+Rahul",
            MessageAppRouter.telegramChatUri("+919876543210", "Hello Rahul")
        )
        assertEquals(null, MessageAppRouter.telegramChatUri("123", "Hello"))
    }

    @Test fun contactSelectionAutomaticallySelectsOneAndNumbersMultipleMatches() {
        val rahul = ContactMatch(1L, "Rahul", listOf("+919876543210"))
        val rahulBhai = ContactMatch(2L, "Rahul Bhai", listOf("+919876543211"))
        assertEquals(ContactResolution.Single(rahul), ContactSelectionPolicy.resolve(listOf(rahul)))
        assertEquals(
            ContactResolution.Multiple(listOf(rahul, rahulBhai)),
            ContactSelectionPolicy.resolve(listOf(rahul, rahulBhai))
        )
        assertEquals(ContactResolution.NoMatch, ContactSelectionPolicy.resolve(emptyList()))
    }

    @Test fun contactMatchingRequiresExactName() {
        assertTrue(ContactSelectionPolicy.isExactNameMatch("Rahul", "Rahul"))
        assertTrue(ContactSelectionPolicy.isExactNameMatch("RAHUL", "rahul"))
        assertFalse(ContactSelectionPolicy.isExactNameMatch("Rahul Bhai", "Rahul"))
        assertFalse(ContactSelectionPolicy.isExactNameMatch("Rahul Patel", "Rahul"))
    }

    @Test fun wakeWordMatchesOnlyHeyMishaPhrase() {
        assertTrue(WakeWordPhraseMatcher.matches("Hey Misha"))
        assertTrue(WakeWordPhraseMatcher.matches("Hey, Misha!"))
        assertTrue(WakeWordPhraseMatcher.matches("Okay, hey Misha please"))
        assertFalse(WakeWordPhraseMatcher.matches("Hey machine"))
        assertFalse(WakeWordPhraseMatcher.matches("Hello Misha"))
    }

}
