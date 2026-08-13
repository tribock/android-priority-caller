package com.prioritycaller.app

import android.content.Context
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Stores MULTIPLE priority contacts. Each contact is serialized as:
 *   "<name>::<num1>|<num2>|<num3>"
 * and all contacts are kept in a SharedPreferences StringSet.
 */
object ContactPrefs {

    private const val PREFS = "priority_caller_prefs"
    private const val KEY_CONTACTS = "priority_contacts_v2"

    private const val KEY_ENABLED = "true"

    private const val KEY_RINGTONE_URI = "ringtone_uri"

    fun getRingtoneUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RINGTONE_URI, null)
        return raw?.let { Uri.parse(it) }
    }

    fun setRingtoneUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RINGTONE_URI, uri?.toString())
            .apply()
    }

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

    fun isEnabled(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, isPaused: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(KEY_ENABLED, isPaused).apply()
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
            contact.numbers.any { saved -> numbersMatch(context, saved, incomingNumber) }
        }
    }

    private fun normalize(number: String): String =
        number.replace(Regex("[^0-9+]"), "")

    /** SIM country if available (works without READ_PHONE_STATE), else the device locale. */
    private fun defaultCountryIso(context: Context): String {
        val simCountry = (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
            ?.simCountryIso
            ?.uppercase(Locale.ROOT)
        return if (!simCountry.isNullOrBlank()) simCountry else Locale.getDefault().country
    }

    /**
     * Caller-ID-style comparison tolerant of country-code / trunk-prefix formatting differences
     * (e.g. "077 123 45 67" vs "+41 77 123 45 67"). Delegates to the platform's own
     * PhoneNumberUtils.areSamePhoneNumber(), the in-platform successor to the deprecated
     * PhoneNumberUtils.compare().
     */
    internal fun numbersMatch(context: Context, a: String, b: String): Boolean =
        PhoneNumberUtils.areSamePhoneNumber(a, b, defaultCountryIso(context))
}