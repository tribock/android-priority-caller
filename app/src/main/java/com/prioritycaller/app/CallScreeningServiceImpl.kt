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
        // Holding the CALL_SCREENING role (API 29+ only, see MainActivity) makes onScreenCall
        // fire for outgoing calls too, not just incoming ones — without this check, calling a
        // priority contact yourself triggered the same loud alarm as them calling you.
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        Log.d(TAG, "onScreenCall: number=$number direction=${callDetails.callDirection} enabled=${ContactPrefs.isEnabled(this)}")

        if (isIncoming && ContactPrefs.isEnabled(this )) {
            val matchedContact = ContactPrefs.getAllContacts(applicationContext)
                .firstOrNull { contact ->
                    contact.numbers.any { saved ->
                        ContactPrefs.numbersMatch(this, saved, number ?: "")
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
