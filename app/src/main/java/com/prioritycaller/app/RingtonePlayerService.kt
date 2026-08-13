package com.prioritycaller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Runs while the priority contact's call is ringing:
 *  - mutes STREAM_RING so the phone's own ringtone doesn't play alongside ours
 *  - plays our own looping mp3 using AudioAttributes.USAGE_ALARM, which the Android
 *    audio framework routes around Do Not Disturb / silent / bedtime mode the same
 *    way a real alarm clock is never silenced by DND.
 *  - stops itself as soon as the call is answered or ends.
 *
 * NOTE: place your custom ringtone file at
 *   app/src/main/res/raw/priority_ringtone.mp3
 * If that file is missing this falls back to the device's default ringtone URI.
 */
class RingtonePlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var previousRingVolume: Int = -1
    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null

    companion object {
        private const val TAG = "RingtonePlayerService"
        private const val CHANNEL_ID = "priority_call_channel"
        private const val NOTIFICATION_ID = 42
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    private var currentCallerName: String = "Priority contact"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: service started for ${intent?.getStringExtra("contact_name")}")
        currentCallerName = intent?.getStringExtra("contact_name") ?: "Priority contact"
        startForeground(NOTIFICATION_ID, buildNotification())
        muteNativeRingtone()
        startLoopingRingtone()
        watchCallState()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    // ---------- Volume ----------

    /**
     * Silences the phone's own ring stream so only our alarm-stream ringtone below is heard,
     * instead of both playing on top of each other. Requires notification policy (DND) access
     * on API 24+ — without it, setStreamVolume(STREAM_RING, ...) throws SecurityException and
     * the native ringtone keeps playing alongside ours.
     */
    private fun muteNativeRingtone() {
        val am = audioManager ?: return
        previousRingVolume = am.getStreamVolume(AudioManager.STREAM_RING)
        try {
            am.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not mute native ringtone — grant DND/notification policy access", e)
        }
    }

    private fun restoreRingVolume() {
        if (previousRingVolume >= 0) {
            audioManager?.setStreamVolume(AudioManager.STREAM_RING, previousRingVolume, 0)
        }
    }

    // ---------- Ringtone playback ----------

    private fun startLoopingRingtone() {
        val customUri = ContactPrefs.getRingtoneUri(this)
        val resId = resources.getIdentifier("priority_ringtone", "raw", packageName)

        mediaPlayer = MediaPlayer().apply {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // bypasses DND like a real alarm
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setAudioAttributes(attrs)
            isLooping = true

            try {
                if (customUri != null) {
                    // User picked a ringtone via the sound picker; that choice wins.
                    setDataSource(applicationContext, customUri)
                } else if (resId != 0) {
                    Log.d(TAG, "Using bundled raw/priority_ringtone")
                    val afd = resources.openRawResourceFd(resId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                } else {
                    // Fallback: default system ringtone, still on the alarm stream/usage.
                    val defaultUri = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_RINGTONE
                    )
                    setDataSource(applicationContext, defaultUri)
                }
                prepare()

                val am = audioManager
                if (am != null) {
                    val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
                }

                start()
            } catch (e: Exception) {
                restoreRingVolume()
            }
        }
    }

    private fun stopRingtone() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) stop()
            } catch (_: IllegalStateException) {
                // already stopped
            }
            release()
        }
        mediaPlayer = null
    }

    // ---------- Stop when the call is answered / ends ----------

    private fun watchCallState() {
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK ||
                        state == TelephonyManager.CALL_STATE_IDLE
                    ) {
                        stopSelf()
                    }
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                @Deprecated("Deprecated in API 31, used here for the pre-31 code path")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK ||
                        state == TelephonyManager.CALL_STATE_IDLE
                    ) {
                        stopSelf()
                    }
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager?.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun stopEverything() {
        stopRingtone()
        restoreRingVolume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
    }

    // ---------- Foreground notification (required to run a foreground service) ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Priority call alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown while a priority contact's call is ringing"
                setBypassDnd(true)
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Incoming priority call")
            .setContentText("$currentCallerName is calling — ringing at max volume")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .build()
    }
}
