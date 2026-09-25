package com.telugu.soundbox

import android.app.Notification
import android.content.Intent
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class PaymentNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentNotificationListener"

        private val MONITORED_PACKAGES = setOf(
            "com.phonepe.app",
            "com.phonepe.merchant",
            "com.google.android.apps.nbu.paisa.user",
            "com.google.android.apps.nbu.paisa.merchant",
            "net.one97.paytm",
            "com.paytm.business",
            "com.bharatpe.app",
            "in.org.npci.upiapp", // BHIM
            "com.dreamplug.androidapp", // CRED
            "com.whatsapp",
            "in.amazon.mShop.android.shopping"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return

        val isMonitoredApp = MONITORED_PACKAGES.contains(packageName) ||
                packageName.contains("upi", ignoreCase = true) ||
                packageName.contains("pay", ignoreCase = true) ||
                packageName.contains("bank", ignoreCase = true)

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val combinedContent = "$title $text $bigText".trim()
        if (combinedContent.isBlank()) return

        Log.d(TAG, "Notification received from [$packageName]: $combinedContent")

        val defaultProvider = when {
            packageName.contains("phonepe") -> "PhonePe"
            packageName.contains("paisa") -> "Google Pay"
            packageName.contains("paytm") -> "Paytm"
            packageName.contains("bharatpe") -> "BharatPe"
            packageName.contains("bhim") -> "BHIM UPI"
            else -> ""
        }

        val payment = TransactionParser.parse(combinedContent, defaultProvider)
        if (payment != null && payment.isCredit && payment.amount > 0) {
            // Deduplication: if bank SMS already triggered this within 8s, skip
            if (PaymentStorage.isDuplicate(payment.formattedAmount)) {
                Log.d(TAG, "Duplicate payment detected from notification, skipping voice alert")
                return
            }

            Log.i(TAG, "Credit detected via notification! Amount: ₹${payment.formattedAmount}, Payer: ${payment.payerName}, App: ${payment.bank}")

            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TeluguSoundbox::NotificationWakeLock"
            )
            wakeLock.acquire(15000L)

            PaymentStorage.addPayment(
                context = applicationContext,
                amount = payment.amount,
                formattedAmount = payment.formattedAmount,
                payerName = payment.payerName,
                bank = payment.bank,
                rawText = combinedContent
            )

            val uiIntent = Intent(SmsBroadcastReceiver.ACTION_NEW_PAYMENT).apply {
                putExtra(SmsBroadcastReceiver.EXTRA_AMOUNT, payment.formattedAmount)
                putExtra(SmsBroadcastReceiver.EXTRA_PAYER, payment.payerName ?: "")
                putExtra(SmsBroadcastReceiver.EXTRA_BANK, payment.bank)
                putExtra(SmsBroadcastReceiver.EXTRA_TEXT, combinedContent)
                setPackage(packageName)
            }
            sendBroadcast(uiIntent)

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
        }
    }
}
