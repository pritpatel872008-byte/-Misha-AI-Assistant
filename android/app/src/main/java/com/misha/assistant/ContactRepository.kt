package com.misha.assistant

import android.content.Context
import android.provider.ContactsContract
import java.util.Locale

internal data class ContactMatch(
    val id: Long,
    val name: String,
    val numbers: List<String>
)

internal object ContactRepository {
    fun findMatches(context: Context, query: String): List<ContactMatch> {
        val wanted = normalize(query)
        if (wanted.isBlank()) return emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val grouped = linkedMapOf<Long, Pair<String, MutableSet<String>>>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf(query.replace("%", "").replace("_", "") + "%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                val normalizedName = normalize(name)
                val exactPrefixMatch = normalizedName == wanted || normalizedName.startsWith(wanted + " ")
                if (exactPrefixMatch && number.isNotBlank()) {
                    val entry = grouped.getOrPut(id) { name to linkedSetOf() }
                    entry.second.add(number)
                }
            }
        }

        return grouped.map { (id, entry) -> ContactMatch(id, entry.first, entry.second.toList()) }
    }

    fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).trim()
            .replace(Regex("[^\\p{L}\\p{N} ]"), "")
            .replace(Regex("\\s+"), " ")
}
