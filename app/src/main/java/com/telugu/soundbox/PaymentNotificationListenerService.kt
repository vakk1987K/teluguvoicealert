package com.telugu.soundbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class PaymentNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SoundboxNotification"

        private val MONITORED_PACKAGES = setOf(
            "com.phonepe.app",
            "com.phonepe.app.business",
            "com.phonepe.merchant",
            "com.google.android.apps.nbu.paisa.user",
            "com.google.android.apps.nbu.paisa.merchant",
            "net.one97.paytm",
            "com.paytm.business",
            "com.mobikwik_new",
            "com.mobikwik.zaakpay.merchant",
            "in.org.npci.upiapp", // BHIM
            "com.bharatpe.app",
            "com.dreamplug.androidapp", // CRED
            "com.whatsapp",
            "in.amazon.mShop.android.shopping",
            "com.freecharge.android",
            "com.myairtelapp",
            "com.jio.myjio",
            "com.sbi.lotusintouch", // YONO SBI
            "com.sbi.upi", // BHIM SBI
            "com.snapwork.hdfc", // HDFC
            "com.csam.icici.bank.imobile", // ICICI
            "com.axis.mobile", // Axis
            "com.msf.kbank.mobile", // Kotak
            "com.canarabank.mobility", // Canara
            "com.fss.pnb", // PNB
            "com.bankofbaroda.mconnect", // BOB
            "com.unionbank.ecommerce.mobile.android" // Union
        )

        private val recentNotificationKeys = mutableMapOf<String, Long>()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "UPI Sound Box Notification Listener CONNECTED successfully to Android OS!")
        SoundboxForegroundService.startService(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "UPI Sound Box Notification Listener disconnected from Android OS")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        val notifKey = "${sbn.key}_${sbn.id}"
        val now = System.currentTimeMillis()
        synchronized(recentNotificationKeys) {
            val lastSeen = recentNotificationKeys[notifKey]
            if (lastSeen != null && (now - lastSeen) < 15000L) {
                return
            }
            recentNotificationKeys[notifKey] = now
            if (recentNotificationKeys.size > 100) {
                recentNotificationKeys.clear()
            }
        }

        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString() ?: ""
        val ticker = notification.tickerText?.toString() ?: ""

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() } ?: ""

        val publicNotification = notification.publicVersion
        val publicText = publicNotification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val publicTitle = publicNotification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

        val combinedContent = "$title $titleBig $text $bigText $subText $lines $ticker $publicTitle $publicText".trim()
        if (combinedContent.isBlank()) return

        val defaultProvider = when {
            packageName.contains("phonepe") -> "PhonePe"
            packageName.contains("paisa") || packageName.contains("google") -> "Google Pay"
            packageName.contains("paytm") -> "Paytm"
            packageName.contains("bharatpe") -> "BharatPe"
            packageName.contains("bhim") -> "BHIM UPI"
            else -> ""
        }

        val payment = TransactionParser.parse(combinedContent, defaultProvider)
        if (payment != null && payment.isCredit && payment.amount > 0) {
            if (PaymentStorage.isDuplicate(applicationContext, payment.amount, payment.formattedAmount)) {
                return
            }

            val addedPayment = PaymentStorage.addPayment(
                context = applicationContext,
                amount = payment.amount,
                formattedAmount = payment.formattedAmount,
                payerName = payment.payerName,
                bank = payment.bank,
                rawText = combinedContent
            )

            if (addedPayment == null) {
                return
            }

            Log.i(TAG, "Credit detected via notification! Amount: ₹${payment.formattedAmount}, Payer: ${payment.payerName}, App: ${payment.bank}")

            // 1. Wake up screen and keep CPU active for speech
            var cpuWakeLock: PowerManager.WakeLock? = null
            var screenWakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
                if (powerManager != null) {
                    cpuWakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "TeluguSoundbox::CpuLock"
                    )
                    cpuWakeLock?.acquire(25000L)

                    @Suppress("DEPRECATION")
                    screenWakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                PowerManager.ON_AFTER_RELEASE,
                        "TeluguSoundbox::ScreenWake"
                    )
                    screenWakeLock?.acquire(6000L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock error: ${e.message}")
            }

            // 2. Post high-priority lock screen notification (100% compliant with Android 14 & 15 background policies)
            try {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val alertChannelId = "upi_payment_alert_channel"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        alertChannelId,
                        "Payment Voice Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Displays payment amount on lock screen"
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        enableVibration(true)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }

                val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    System.currentTimeMillis().toInt(),
                    openIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val alertNotification = NotificationCompat.Builder(applicationContext, alertChannelId)
                    .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                    .setContentTitle("₹${payment.formattedAmount} Received!")
                    .setContentText("${payment.bank} • ${payment.payerName ?: "UPI Customer"}")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                notificationManager?.notify(2002, alertNotification)
            } catch (e: Exception) {
                Log.w(TAG, "Notification post error: ${e.message}")
            }

            // 3. Update in-app UI if app is open
            try {
                val uiIntent = Intent(PaymentStorage.ACTION_NEW_PAYMENT).apply {
                    putExtra(PaymentStorage.EXTRA_AMOUNT, payment.formattedAmount)
                    putExtra(PaymentStorage.EXTRA_PAYER, payment.payerName ?: "")
                    putExtra(PaymentStorage.EXTRA_BANK, payment.bank)
                    putExtra(PaymentStorage.EXTRA_TEXT, combinedContent)
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(uiIntent)
            } catch (e: Exception) {
                Log.w(TAG, "UI broadcast error: ${e.message}")
            }

            // 4. Speak out loud in natural Indian language (Always runs!)
            if (PaymentStorage.isAlertsEnabled(applicationContext)) {
                TeluguTtsManager.announcePayment(
                    context = applicationContext,
                    amount = payment.formattedAmount,
                    payerName = payment.payerName,
                    onComplete = {
                        try {
                            if (cpuWakeLock?.isHeld == true) cpuWakeLock.release()
                            if (screenWakeLock?.isHeld == true) screenWakeLock.release()
                        } catch (e: Exception) {}
                    }
                )
            } else {
                Log.d(TAG, "Voice alerts currently disabled by merchant, skipping TTS")
                try {
                    if (cpuWakeLock?.isHeld == true) cpuWakeLock.release()
                    if (screenWakeLock?.isHeld == true) screenWakeLock.release()
                } catch (e: Exception) {}
            }
        }
    }
}
