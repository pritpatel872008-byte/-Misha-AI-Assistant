package com.misha.assistant

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import java.util.Locale

internal class MessagingController(
    private val activity: ComponentActivity,
    private val reply: (String) -> Unit
) {
    private data class Contact(val id: Long, val name: String, val numbers: List<String>)
    private var request: MessageRequest? = null
    private var recipients: List<Contact> = emptyList()
    private var numberContact: Contact? = null
    private var expiresAt = 0L

    fun resolve(message: MessageRequest) {
        clear()
        request = message
        val query = message.recipient.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val grouped = linkedMapOf<Long, Pair<String, MutableList<String>>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        activity.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex).orEmpty()
                val digits = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
                val number = cursor.getString(numberIndex).orEmpty()
                if (query.isNotBlank() && digits.contains(query) && number.isNotBlank()) {
                    val entry = grouped.getOrPut(id) { name to mutableListOf() }
                    if (entry.second.none { it.filter { ch -> ch.isDigit() } == number.filter { it.isDigit() } }) entry.second.add(number)
                }
            }
        }
        val matches = grouped.map { (id, pair) -> Contact(id, pair.first, pair.second.toList()) }
        if (matches.isEmpty()) {
            request = null
            reply("I couldn't find a contact matching ${message.recipient}. Check the name or save the number in Contacts.")
            return
        }
        expiresAt = System.currentTimeMillis() + 120_000L
        if (matches.size == 1) choose(matches.first()) else {
            recipients = matches
            reply("I found ${matches.size} matching contacts. Say or type a number:\n" +
                matches.mapIndexed { index, c -> "${index + 1}. ${c.name}" + if (c.numbers.size > 1) " (${c.numbers.size} numbers)" else " •••• ${c.numbers.first().takeLast(4)}" }.joinToString("\n"))
        }
    }

    fun handleSelection(input: String): Boolean {
        if (System.currentTimeMillis() > expiresAt || (recipients.isEmpty() && numberContact == null)) return false
        if (input.trim().equals("cancel", true)) {
            clear()
            request = null
            reply("Message cancelled.")
            return true
        }
        val value = input.trim().lowercase(Locale.ROOT).removePrefix("number ").removePrefix("option ").removeSuffix(".")
        val choice = value.toIntOrNull() ?: when (value) {
            "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5; else -> null
        }
        if (choice == null) {
            reply("Please choose one of the numbered options, or say cancel.")
            return true
        }
        val currentNumberContact = numberContact
        if (currentNumberContact != null) {
            if (choice !in 1..currentNumberContact.numbers.size) {
                reply("That number is not in the current list. Choose from 1 to ${currentNumberContact.numbers.size}.")
                return true
            }
            val selectedNumber = currentNumberContact.numbers[choice - 1]
            val msg = request
            clear()
            if (msg != null) confirm(currentNumberContact, selectedNumber, msg)
            return true
        }
        if (choice !in 1..recipients.size) {
            reply("That number is not in the current list. Choose from 1 to ${recipients.size}.")
            return true
        }
        val selected = recipients[choice - 1]
        recipients = emptyList()
        choose(selected)
        return true
    }

    private fun choose(contact: Contact) {
        val msg = request ?: return
        if (contact.numbers.size == 1) confirm(contact, contact.numbers.first(), msg)
        else {
            numberContact = contact
            expiresAt = System.currentTimeMillis() + 120_000L
            reply("I found ${contact.numbers.size} numbers for ${contact.name}. Which one should I use?\n" +
                contact.numbers.mapIndexed { index, number -> "${index + 1}. •••• ${number.takeLast(4)}" }.joinToString("\n"))
        }
    }

    private fun confirm(contact: Contact, number: String, message: MessageRequest) {
        clear()
        request = null
        AlertDialog.Builder(activity)
            .setTitle("Review message")
            .setMessage("To: ${contact.name}\nApp: ${message.appName ?: "Choose an app"}\nMessage: ${message.body}\n\nMisha will open the app with your message prepared. Tap Send inside that app to send.")
            .setNegativeButton("Cancel") { _, _ -> reply("Message cancelled.") }
            .setPositiveButton("Open app") { _, _ -> openApp(contact, number, message) }
            .show()
    }

    private fun openApp(contact: Contact, number: String, message: MessageRequest) {
        val digits = number.filter { it.isDigit() }
        val app = message.appName?.lowercase(Locale.ROOT)?.trim()
        try {
            when (app) {
                "whatsapp", "whats app", "whatsapp business" -> {
                    val international = if (digits.length == 10) "91${digits}" else digits
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${international}?text=${Uri.encode(message.body)}")))
                }
                "telegram" -> activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?to=${Uri.encode(digits)}&text=${Uri.encode(message.body)}")))
                "sms", "messages", "text message" -> {
                    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
                    sms.putExtra("sms_body", message.body)
                    activity.startActivity(sms)
                }
                else -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message.body)
                        putExtra(Intent.EXTRA_TITLE, "Message to ${contact.name}")
                        packageFor(app)?.let { setPackage(it) }
                    }
                    activity.startActivity(Intent.createChooser(send, if (app == null) "Choose an app to message ${contact.name}" else "Continue in ${message.appName}"))
                }
            }
            reply("Message prepared for ${contact.name}. Review it in the selected app and tap Send when ready.")
        } catch (_: ActivityNotFoundException) {
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.body)
                    putExtra(Intent.EXTRA_TITLE, "Message to ${contact.name}")
                }
                activity.startActivity(Intent.createChooser(fallback, "Choose an installed messaging app"))
                reply("The requested app could not open directly. I opened the Android app chooser instead.")
            } catch (_: Exception) {
                reply("No compatible messaging app was found. Install an app that supports text sharing and try again.")
            }
        } catch (_: Exception) {
            reply("I couldn't open the requested app. Check that it is installed, then try again.")
        }
    }

    private fun packageFor(app: String?): String? = when (app) {
        "whatsapp" -> "com.whatsapp"
        "whatsapp business" -> "com.whatsapp.w4b"
        "telegram" -> "org.telegram.messenger"
        "signal" -> "org.thoughtcrime.securesms"
        "messenger", "facebook messenger" -> "com.facebook.orca"
        "instagram" -> "com.instagram.android"
        "discord" -> "com.discord"
        "slack" -> "com.Slack"
        else -> null
    }

    private fun clear() {
        recipients = emptyList()
        numberContact = null
        expiresAt = 0L
    }
}
