package com.misha.assistant

import java.net.URLEncoder
import java.util.Locale

internal enum class MessageAppRoute {
    SMS,
    WHATSAPP,
    WHATSAPP_BUSINESS,
    TELEGRAM,
    SIGNAL,
    MESSENGER,
    SYSTEM_CHOOSER
}

internal object MessageAppRouter {
    fun routeFor(appName: String): MessageAppRoute =
        when (appName.lowercase(Locale.ROOT).trim()) {
            "sms", "messages", "google messages", "text message", "messaging" ->
                MessageAppRoute.SMS
            "whatsapp", "wa" -> MessageAppRoute.WHATSAPP
            "whatsapp business", "wa business" -> MessageAppRoute.WHATSAPP_BUSINESS
            "telegram" -> MessageAppRoute.TELEGRAM
            "signal" -> MessageAppRoute.SIGNAL
            "messenger", "facebook messenger" -> MessageAppRoute.MESSENGER
            else -> MessageAppRoute.SYSTEM_CHOOSER
        }

    fun telegramChatUri(phoneNumber: String, message: String): String? {
        val phone = phoneNumber.trim()
        if (phone.count(Char::isDigit) < 7) return null
        val encodedPhone = URLEncoder.encode(phone, "UTF-8")
        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        return "tg://resolve?phone=$encodedPhone&text=$encodedMessage"
    }

    fun supportsDirectRecipient(route: MessageAppRoute): Boolean =
        route in setOf(
            MessageAppRoute.SMS,
            MessageAppRoute.WHATSAPP,
            MessageAppRoute.WHATSAPP_BUSINESS,
            MessageAppRoute.TELEGRAM
        )

    fun packageName(route: MessageAppRoute): String? =
        when (route) {
            MessageAppRoute.WHATSAPP -> "com.whatsapp"
            MessageAppRoute.WHATSAPP_BUSINESS -> "com.whatsapp.w4b"
            MessageAppRoute.TELEGRAM -> "org.telegram.messenger"
            MessageAppRoute.SIGNAL -> "org.thoughtcrime.securesms"
            MessageAppRoute.MESSENGER -> "com.facebook.orca"
            MessageAppRoute.SMS, MessageAppRoute.SYSTEM_CHOOSER -> null
        }
}
