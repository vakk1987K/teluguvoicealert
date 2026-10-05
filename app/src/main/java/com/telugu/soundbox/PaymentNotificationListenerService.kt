package com.telugu.soundbox

import android.app.Notification
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

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

        // Cache recently processed notification keys to prevent duplicate triggers from Android notification updates
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

        // Deduplication of identical notification updates:
        val notifKey = "${sbn.key}_${sbn.id}"
        val now = System.currentTimeMillis()
        synchronized(recentNotificationKeys) {
            val lastSeen = recentNotificationKeys[notifKey]
            if (lastSeen != null && (now - lastSeen) < 15000L) {
                Log.d(TAG, "Skipping notification update for existing key $notifKey")
                return
            }
            recentNotificationKeys[notifKey] = now
            if (recentNotificationKeys.size > 100) {
                recentNotificationKeys.clear()
            }
        }

        // Check if from UPI / merchant / banking app OR general notification
        val isMonitoredApp = MONITORED_PACKAGES.contains(packageName) ||
                packageName.contains("upi", ignoreCase = true) ||
                packageName.contains("pay", ignoreCase = true) ||
                packageName.contains("bank", ignoreCase = true) ||
                packageName.contains("paisa", ignoreCase = true)

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

        Log.d(TAG, "Processing notification from [$packageName]: $combinedContent")

        val defaultProvider = when {
            packageName.contains("phonepe") -> "PhonePe"
            packageName.contains("paisa") || packageName.contains("google") -> "Google Pay"
            packageName.contains("paytm") -> "Paytm"
            packageName.contains("mobikwik") -> "MobiKwik"
            packageName.contains("bharatpe") -> "BharatPe"
            packageName.contains("bhim") -> "BHIM UPI"
            else -> ""
        }

        // Parse payment details (strictly positive credit only!)
        val payment = TransactionParser.parse(combinedContent, defaultProvider)
        if (payment != null && payment.isCredit && payment.amount > 0) {
            // Deduplication: if another app (PhonePe, GPay, Paytm, MobiKwik) or Bank SMS already alerted within 45s, skip!
            if (PaymentStorage.isDuplicate(applicationContext, payment.amount, payment.formattedAmount)) {
                Log.d(TAG, "Duplicate payment detected from notification (₹${payment.formattedAmount}), skipping voice alert")
                return
            }

            // Save to lightweight storage. If addPayment returns null, it was an existing duplicate -> skip!
            val addedPayment = PaymentStorage.addPayment(
                context = applicationContext,
                amount = payment.amount,
                formattedAmount = payment.formattedAmount,
                payerName = payment.payerName,
                bank = payment.bank,
                rawText = combinedContent
            )

            if (addedPayment == null) {
                Log.d(TAG, "Payment of ₹${payment.formattedAmount} was already added, skipping duplicate voice & UI update")
                return
            }

            Log.i(TAG, "Credit detected via notification! Amount: ₹${payment.formattedAmount}, Payer: ${payment.payerName}, App: ${payment.bank}")

            // 1. Keep CPU and Screen awake so TTS audio plays loud and clear on lock screen (Android 14, 15, 16)
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "TeluguSoundbox::NotificationWakeLock"
            )
            wakeLock.acquire(20000L) // 20s safety limit

            // 2. Ensure foreground service is running
            SoundboxForegroundService.startService(applicationContext)

            // 3. Update UI
            val uiIntent = Intent(PaymentStorage.ACTION_NEW_PAYMENT).apply {
                putExtra(PaymentStorage.EXTRA_AMOUNT, payment.formattedAmount)
                putExtra(PaymentStorage.EXTRA_PAYER, payment.payerName ?: "")
                putExtra(PaymentStorage.EXTRA_BANK, payment.bank)
                putExtra(PaymentStorage.EXTRA_TEXT, combinedContent)
                setPackage(applicationContext.packageName)
            }
            sendBroadcast(uiIntent)

            try {
                val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                applicationContext.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not bring MainActivity to lockscreen: ${e.message}")
            }

            // 4. Speak out loud only if alerts are enabled by the merchant
            if (PaymentStorage.isAlertsEnabled(applicationContext)) {
                TeluguTtsManager.announcePayment(
                    context = applicationContext,
                    amount = payment.formattedAmount,
                    payerName = payment.payerName,
                    onComplete = {
                        try {
                            if (wakeLock.isHeld) wakeLock.release()
                        } catch (_: Exception) {}
                    }
                )
            } else {
                Log.d(TAG, "Voice alerts currently disabled by merchant, skipping TTS")
                try {
                    if (wakeLock.isHeld) wakeLock.release()
                } catch (_: Exception) {}
            }
        }
    }
}
