package com.misha.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class LocalBrain {
    enum class TorchAction { ON, OFF, STATUS }
    fun torchAction(input: String): TorchAction? {
        val q = input.lowercase(Locale.ROOT).trim().removeSuffix("?").removeSuffix(".")
        return when (q) {
            "turn on the flashlight", "turn the flashlight on", "switch on the flashlight", "switch the flashlight on", "turn on torch", "turn the torch on", "flashlight on", "torch on" -> TorchAction.ON
            "turn off the flashlight", "turn the flashlight off", "switch off the flashlight", "switch the flashlight off", "turn off torch", "turn the torch off", "flashlight off", "torch off" -> TorchAction.OFF
            "is the flashlight on", "is torch on", "flashlight status", "torch status", "is my torch on" -> TorchAction.STATUS
            else -> null
        }
    }
    fun reply(input: String): String {
        val q = input.lowercase(Locale.ROOT).trim()
        return when {
            q.matches(Regex("^(hi|hello|hey)( misha)?[.! ]*$")) -> "Hello! I'm Misha. How can I help you?"
            "your name" in q || q == "who are you" -> "My name is Misha AI Assistant."
            "how are you" in q -> "I'm ready to help. What would you like to do?"
            "what time" in q || q == "time" -> "The time is ${SimpleDateFormat("hh:mm a", Locale.US).format(Date())}."
            "what date" in q || "today's date" in q -> "Today is ${SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(Date())}."
            "help" in q || "what can you do" in q -> "I can chat offline and control your flashlight. Say: Turn on the flashlight, or Turn off the flashlight."
            "thank" in q -> "You're welcome!"
            else -> "I don't have a reliable offline answer for that yet. Online search will be connected in a later phase."
        }
    }
}
