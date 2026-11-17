package com.misha.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageCommandParserTest {
    @Test fun parsesNamedAppAndRecipient() {
        assertEquals(
            MessageRequest("Rahul", "whatsapp", "Hello Rahul"),
            MessageCommandParser.parse("Send a message to Rahul on WhatsApp saying Hello Rahul")
        )
    }

    @Test fun parsesAlternativeMessageSyntax() {
        assertEquals(
            MessageRequest("Rahul Bhai", "telegram", "I am on my way"),
            MessageCommandParser.parse("Message Rahul Bhai a message via Telegram: I am on my way")
        )
    }

    @Test fun parsesGenericShareRequest() {
        assertEquals(
            MessageRequest("Aarav", null, "Please call me"),
            MessageCommandParser.parse("Send a message to Aarav: Please call me")
        )
    }

    @Test fun rejectsIncompleteMessage() {
        assertNull(MessageCommandParser.parse("Send a message to Rahul on WhatsApp"))
    }
}
