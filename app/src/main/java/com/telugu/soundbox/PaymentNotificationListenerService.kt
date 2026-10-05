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
            "in.org.npci.upiapp",
            "com.bharatpe.app",
            "com.dreamplug.androidapp",
            "com.whatsapp",
            "in.amazon.mShop.android.shopping",
            "com.freecharge.android",
            "com.myairtelapp",
            "com.jio.myjio",
            "com.sbi.lotusintouch",
            "com.sbi.upi",
            "com.snapwork.hdfc",
            "com.csam.icici.bank.imobile",
            "com.axis.mobile",
            "com.msf.kbank.mobile",
            "com.canarabank.mobility",
            "com.fss.pnb",
            "com.bankofbaroda.mconnect",
            "com.unionbank.ecommerce.mobile.android"
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

            if (addedPayment == null) return

            var wakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TeluguSoundbox::NotificationWakeLock"
                )
                wakeLock?.acquire(30000L)
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock error: ${e.message}")
            }

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

            if (PaymentStorage.isAlertsEnabled(applicationContext)) {
                TeluguTtsManager.announcePayment(
                    context = applicationContext,
                    amount = payment.formattedAmount,
                    payerName = payment.payerName,
                    onComplete = {
                        try {
                            if (wakeLock?.isHeld == true) wakeLock.release()
                        } catch (e: Exception) {}
                    }
                )
            } else {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (e: Exception) {}
            }
        }
    }
}
