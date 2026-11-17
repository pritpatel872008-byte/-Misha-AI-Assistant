package com.misha.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal sealed interface MishaCommand {
    data class Torch(val enabled: Boolean?) : MishaCommand
    data class OpenApp(val appName: String) : MishaCommand
    data class Search(val query: String) : MishaCommand
    data class Call(val contactName: String) : MishaCommand
    data class Message(val contactName: String, val appName: String, val body: String) : MishaCommand
    data object Battery : MishaCommand
    data object DateTime : MishaCommand
    data class Conversation(val text: String) : MishaCommand
}

internal object LocalBrain {
    fun parse(input: String): MishaCommand {
        val text = input.trim().replace(Regex("\\s+"), " ")
        val q = text.lowercase(Locale.ROOT).trimEnd('.', '?', '!')
        if (q.isBlank()) return MishaCommand.Conversation("")

        when (q) {
            "turn on the flashlight", "turn the flashlight on", "switch on the flashlight",
            "switch the flashlight on", "turn on torch", "turn the torch on", "flashlight on", "torch on" ->
                return MishaCommand.Torch(true)
            "turn off the flashlight", "turn the flashlight off", "switch off the flashlight",
            "switch the flashlight off", "turn off torch", "turn the torch off", "flashlight off", "torch off" ->
                return MishaCommand.Torch(false)
            "is the flashlight on", "is torch on", "flashlight status", "torch status", "is my torch on" ->
                return MishaCommand.Torch(null)
            "battery", "battery status", "what is my battery level", "what's my battery level", "check battery" ->
                return MishaCommand.Battery
            "date", "time", "what time is it", "what is the date", "what's the date", "today's date", "what is today's date" ->
                return MishaCommand.DateTime
        }

        val withApp = Regex(
            "^(?:send\\s+(?:a\\s+)?message\\s+to|message\\s+to|message|text)\\s+(.+?)\\s+on\\s+([a-z0-9][a-z0-9 ._-]*?)(?:\\s+(?:saying|that says|with message)\\s+|\\s*:\\s*)(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
        if (withApp != null) {
            return MishaCommand.Message(
                withApp.groupValues[1].trim(),
                withApp.groupValues[2].trim(),
                withApp.groupValues[3].trim()
            )
        }

        val sendNamed = Regex(
            "^send\\s+(.+?)\\s+(?:a\\s+)?message\\s+on\\s+([a-z0-9][a-z0-9 ._-]*?)(?:\\s+(?:saying|that says|with message)\\s+|\\s*:\\s*)(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
        if (sendNamed != null) {
            return MishaCommand.Message(
                sendNamed.groupValues[1].trim(),
                sendNamed.groupValues[2].trim(),
                sendNamed.groupValues[3].trim()
            )
        }

        val withoutApp = Regex(
            "^(?:send\\s+(?:a\\s+)?message\\s+to|message\\s+to|message|text)\\s+(.+?)(?:\\s+(?:saying|that says|with message)\\s+|\\s*:\\s*)(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
        if (withoutApp != null) {
            return MishaCommand.Message(withoutApp.groupValues[1].trim(), "Choose app", withoutApp.groupValues[2].trim())
        }

        val sendWithoutApp = Regex(
            "^send\\s+(.+?)\\s+(?:a\\s+)?message(?:\\s+(?:saying|that says|with message)\\s+|\\s*:\\s*)(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
        if (sendWithoutApp != null) {
            return MishaCommand.Message(sendWithoutApp.groupValues[1].trim(), "Choose app", sendWithoutApp.groupValues[2].trim())
        }

        val callMatch = Regex("^(?:please\\s+)?(?:call|phone|dial)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (callMatch != null) return MishaCommand.Call(callMatch.groupValues[1].trim())

        val searchMatch = Regex(
            "^(?:please\\s+)?(?:search(?:\\s+the\\s+web)?\\s+for|look\\s+up|find\\s+online)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(text)
        if (searchMatch != null) return MishaCommand.Search(searchMatch.groupValues[1].trim())

        val openMatch = Regex("^(?:please\\s+)?(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(text)
        if (openMatch != null) return MishaCommand.OpenApp(openMatch.groupValues[1].trim())

        return MishaCommand.Conversation(text)
    }

    fun ordinal(input: String): Int? {
        val value = input.lowercase(Locale.ROOT).trim()
            .removePrefix("number ")
            .removePrefix("option ")
            .removePrefix("choice ")
            .removeSuffix(".")
        value.toIntOrNull()?.let { return it }
        return mapOf(
            "one" to 1, "first" to 1, "two" to 2, "second" to 2,
            "three" to 3, "third" to 3, "four" to 4, "fourth" to 4,
            "five" to 5, "fifth" to 5, "six" to 6, "sixth" to 6,
            "seven" to 7, "seventh" to 7, "eight" to 8, "eighth" to 8,
            "nine" to 9, "ninth" to 9, "ten" to 10, "tenth" to 10,
            "eleven" to 11, "twelve" to 12, "thirteen" to 13,
            "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
            "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20
        )[value]
    }

    fun reply(input: String): String {
        val q = input.lowercase(Locale.ROOT).trim()
        return when {
            q.matches(Regex("^(hi|hello|hey)( misha)?[.! ]*$")) -> "Hello! I'm Misha. What would you like me to do?"
            "your name" in q || q == "who are you" -> "I'm Misha, your local Android assistant."
            "how are you" in q -> "I'm ready to help. Give me a command whenever you are."
            "what time" in q || q == "time" -> "The time is " + SimpleDateFormat("hh:mm a", Locale.US).format(Date()) + "."
            "what date" in q || "today's date" in q -> "Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date()) + "."
            "help" in q || "what can you do" in q ->
                "I can open supported apps, search online, call contacts, prepare messages, control the flashlight, and check battery status. Try: Open YouTube; Search the web for Android security; Call [exact contact name]; Turn on the flashlight."
            "thank" in q -> "You're welcome!"
            else -> "I can handle phone commands and basic offline conversation. For current information, say: Search the web for your question."
        }
    }
}
