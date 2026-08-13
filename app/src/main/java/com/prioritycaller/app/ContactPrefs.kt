package com.prioritycaller.app

import android.content.Context

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
        return getAllContacts(context).any { contact ->
            contact.numbers.any { saved -> numbersMatch(saved, incomingNumber) }
        }
    }

    private fun normalize(number: String): String =
        number.replace(Regex("[^0-9+]"), "")

    private const val MIN_MATCHING_DIGITS = 7

    /**
     * Right-aligned digit comparison, tolerant of country-code / formatting differences
     * between two numbers (e.g. "+15551234567" vs "(555) 123-4567"). Replaces the
     * platform's PhoneNumberUtils.compare(), which is deprecated with no in-platform
     * replacement, by mirroring its own right-aligned suffix-matching approach.
     */
    internal fun numbersMatch(a: String, b: String): Boolean {
        val digitsA = a.filter(Char::isDigit)
        val digitsB = b.filter(Char::isDigit)
        if (digitsA.isEmpty() || digitsB.isEmpty()) return false
        val overlap = minOf(digitsA.length, digitsB.length)
        if (overlap < MIN_MATCHING_DIGITS) return digitsA == digitsB
        return digitsA.takeLast(overlap) == digitsB.takeLast(overlap)
    }
}