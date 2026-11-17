package com.misha.assistant

import org.junit.Assert.assertEquals
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
}
