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
import androidx.core.app.NotificationCompat

/**
 * Runs while the priority contact's call is ringing:
 *  - pushes STREAM_RING to max so the normal ringer is loud too
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
        currentCallerName = intent?.getStringExtra("contact_name") ?: "Priority contact"
        startForeground(NOTIFICATION_ID, buildNotification())
        boostRingVolume()
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

    private fun boostRingVolume() {
        val am = audioManager ?: return
        previousRingVolume = am.getStreamVolume(AudioManager.STREAM_RING)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        try {
            am.setStreamVolume(AudioManager.STREAM_RING, max, 0)
        } catch (e: SecurityException) {
            // Some OEM builds (MIUI included) restrict this without DND access granted.
            // The alarm-stream ringtone below is the real fallback in that case.
        }
    }

    private fun restoreRingVolume() {
        if (previousRingVolume >= 0) {
            audioManager?.setStreamVolume(AudioManager.STREAM_RING, previousRingVolume, 0)
        }
    }

    // ---------- Ringtone playback ----------

    private fun startLoopingRingtone() {
        val resId = resources.getIdentifier("priority_ringtone", "raw", packageName)

        mediaPlayer = MediaPlayer().apply {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM) // bypasses DND like a real alarm
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setAudioAttributes(attrs)
            isLooping = true

            try {
                if (resId != 0) {
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
                // If playback setup fails, at minimum the boosted ring volume above still applies.
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
