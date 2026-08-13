package com.prioritycaller.app

import android.content.Context
import android.telephony.PhoneNumberUtils

/**
 * Stores MULTIPLE priority contacts. Each contact is serialized as:
 *   "<name>::<num1>|<num2>|<num3>"
 * and all contacts are kept in a SharedPreferences StringSet.
 */
object ContactPrefs {

    private const val PREFS = "priority_caller_prefs"
    private const val KEY_CONTACTS = "priority_contacts_v2"

    data class PriorityContact(val name: String, val numbers: List<String>)

    private fun serialize(contact: PriorityContact): String =
        "${contact.name}::${contact.numbers.joinToString("|")}"

    private fun deserialize(raw: String): PriorityContact? {
        val parts = raw.split("::", limit = 2)
        if (parts.size != 2) return null
        val numbers = parts[1].split("|").filter { it.isNotBlank() }
        return PriorityContact(parts[0], numbers)
    }

    fun getAllContacts(context: Context): List<PriorityContact> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CONTACTS, emptySet()) ?: emptySet()
        return raw.mapNotNull { deserialize(it) }
    }

    /** Adds a contact, replacing any existing entry with the same name+numbers. */
    fun addContact(context: Context, name: String, numbers: List<String>) {
        val normalized = numbers.map { normalize(it) }.distinct()
        val current = getAllContacts(context).toMutableList()
        // avoid exact duplicates (same name, same numbers)
        current.removeAll { it.name == name && it.numbers == normalized }
        current.add(PriorityContact(name, normalized))
        saveAll(context, current)
    }

    fun removeContact(context: Context, contact: PriorityContact) {
        val current = getAllContacts(context).toMutableList()
        current.removeAll { it.name == contact.name && it.numbers == contact.numbers }
        saveAll(context, current)
    }

    private fun saveAll(context: Context, contacts: List<PriorityContact>) {
        val serialized = contacts.map { serialize(it) }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_CONTACTS, serialized)
            .apply()
    }

    /** True if the incoming number belongs to ANY saved priority contact. */
    fun isPriorityNumber(context: Context, incomingNumber: String?): Boolean {
        if (incomingNumber.isNullOrBlank()) return false
        val target = normalize(incomingNumber)
        return getAllContacts(context).any { contact ->
            contact.numbers.any { saved ->
                PhoneNumberUtils.compare(saved, target) || saved == target
            }
        }
    }

    private fun normalize(number: String): String =
        number.replace(Regex("[^0-9+]"), "")
}