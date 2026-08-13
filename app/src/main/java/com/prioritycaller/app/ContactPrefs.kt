package com.prioritycaller.app

import android.content.Context
import android.telephony.PhoneNumberUtils

/**
 * Stores the priority contact's name + phone numbers.
 * A contact can have several numbers (mobile/home/work), so we store all of them
 * and match against any.
 */
object ContactPrefs {

    private const val PREFS = "priority_caller_prefs"
    private const val KEY_NAME = "contact_name"
    private const val KEY_NUMBERS = "contact_numbers" // stored pipe-separated

    fun saveContact(context: Context, name: String, numbers: List<String>) {
        val normalized = numbers.map { normalize(it) }.distinct()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name)
            .putString(KEY_NUMBERS, normalized.joinToString("|"))
            .apply()
    }

    fun getContactName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun getContactNumbers(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NUMBERS, null) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    /** True if the incoming number belongs to the saved priority contact. */
    fun isPriorityNumber(context: Context, incomingNumber: String?): Boolean {
        if (incomingNumber.isNullOrBlank()) return false
        val target = normalize(incomingNumber)
        return getContactNumbers(context).any { saved ->
            PhoneNumberUtils.compare(saved, target) || saved == target
        }
    }

    // Strips spaces/dashes/parentheses so comparisons are consistent.
    private fun normalize(number: String): String =
        number.replace(Regex("[^0-9+]"), "")
}
