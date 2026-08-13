package com.prioritycaller.app

import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The system calls onScreenCall() for every incoming call once this app holds the
 * CALL_SCREENING role (requested from MainActivity). We never block/reject the call —
 * we just detect whether it's from the priority contact and, if so, kick off the
 * foreground service that forces ring volume to max and plays the custom ringtone.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    companion object {
        private const val TAG = "CallScreeningService"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        Log.d(TAG, "onScreenCall: number=$number enabled=${ContactPrefs.isEnabled(this)}")

        if (ContactPrefs.isEnabled(this )) {
            val matchedContact = ContactPrefs.getAllContacts(applicationContext)
                .firstOrNull { contact ->
                    contact.numbers.any { saved ->
                        ContactPrefs.numbersMatch(saved, number ?: "")
                    }
                }

            Log.d(TAG, "matchedContact=${matchedContact?.name}")

            if (matchedContact != null) {
                val serviceIntent = Intent(this, RingtonePlayerService::class.java).apply {
                    putExtra("contact_name", matchedContact.name)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
            }
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
