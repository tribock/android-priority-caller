package com.prioritycaller.app

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.content.ContextCompat

/**
 * The system calls onScreenCall() for every incoming call once this app holds the
 * CALL_SCREENING role (requested from MainActivity). We never block/reject the call —
 * we just detect whether it's from the priority contact and, if so, kick off the
 * foreground service that forces ring volume to max and plays the custom ringtone.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart

        val matchedContact = ContactPrefs.getAllContacts(applicationContext)
            .firstOrNull { contact ->
                contact.numbers.any { saved ->
                    android.telephony.PhoneNumberUtils.compare(saved, number ?: "")
                }
            }

        if (matchedContact != null) {
            val serviceIntent = Intent(this, RingtonePlayerService::class.java).apply {
                putExtra("contact_name", matchedContact.name)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }

        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }
}
