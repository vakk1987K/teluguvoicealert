package com.telugu.soundbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SoundboxForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "upi_soundbox_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_ANNOUNCE = "com.telugu.soundbox.ACTION_ANNOUNCE"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_PAYER = "extra_payer"

        fun startService(context: Context) {
            val intent = Intent(context, SoundboxForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun announce(context: Context, amount: String, payerName: String?) {
            // Direct TTS invocation so playback triggers immediately on locked screen without waiting for power button
            TeluguTtsManager.announcePayment(context, amount, payerName)
            updateStatus(context, "₹$amount Received • Speaking now")
        }

        fun updateStatus(context: Context, statusText: String) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("🔊 UPI Sound Box Active")
                    .setContentText(statusText)
                    .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                notificationManager?.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                // Ignore notification update error
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SoundboxForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Listening for payments (works on lock screen)"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Listening for payments (works on lock screen)"))
        }

        // Keep TTS pre-warmed and ready
        TeluguTtsManager.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ANNOUNCE) {
            val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: ""
            val payer = intent.getStringExtra(EXTRA_PAYER)
            if (amount.isNotBlank()) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(
                    NOTIFICATION_ID,
                    buildNotification("₹$amount Received • Speaking now...")
                )

                // Announce speech with foreground CPU priority
                TeluguTtsManager.announcePayment(
                    context = applicationContext,
                    amount = amount,
                    payerName = payer,
                    onComplete = {
                        notificationManager?.notify(
                            NOTIFICATION_ID,
                            buildNotification("Listening for payments (works on lock screen)")
                        )
                    }
                )
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "UPI Sound Box Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps UPI Sound Box active to announce payment alerts when the phone is locked."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔊 UPI Sound Box Active")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
