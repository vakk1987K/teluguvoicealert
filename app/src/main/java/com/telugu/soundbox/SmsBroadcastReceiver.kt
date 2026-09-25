package com.telugu.soundbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log

class SmsBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        const val ACTION_NEW_PAYMENT = "com.telugu.soundbox.ACTION_NEW_PAYMENT"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_PAYER = "extra_payer"
        const val EXTRA_BANK = "extra_bank"
        const val EXTRA_TEXT = "extra_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // CRITICAL FOR LOCK SCREEN:
        // goAsync() signals Android to keep this receiver alive during speech
        val pendingResult = goAsync()

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TeluguSoundbox::SmsWakeLock"
        )
        wakeLock.acquire(15000L) // 15 seconds safety timeout

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
                return
            }

            val fullBodyBuilder = StringBuilder()
            var sender = ""

            for (sms in messages) {
                fullBodyBuilder.append(sms.messageBody ?: "")
                if (sender.isEmpty()) {
                    sender = sms.originatingAddress ?: "Bank"
                }
            }

            val fullText = fullBodyBuilder.toString()
            Log.d(TAG, "Incoming SMS received from $sender: $fullText")

            val payment = TransactionParser.parse(fullText)
            if (payment != null && payment.isCredit && payment.amount > 0) {
                Log.i(TAG, "Credit payment detected! Amount: ₹${payment.formattedAmount}, Payer: ${payment.payerName}")

                // Broadcast to MainActivity UI to display in the live payments feed
                val uiIntent = Intent(ACTION_NEW_PAYMENT).apply {
                    putExtra(EXTRA_AMOUNT, payment.formattedAmount)
                    putExtra(EXTRA_PAYER, payment.payerName ?: "")
                    putExtra(EXTRA_BANK, payment.bank)
                    putExtra(EXTRA_TEXT, payment.rawText)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(uiIntent)

                // Trigger the Telugu Voice Alert and release lock when speech finishes
                TeluguTtsManager.announcePayment(
                    context = context,
                    amount = payment.formattedAmount,
                    payerName = payment.payerName,
                    onComplete = {
                        try {
                            if (wakeLock.isHeld) wakeLock.release()
                            pendingResult.finish()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error finishing pendingResult: ${e.message}")
                        }
                    }
                )
            } else {
                if (wakeLock.isHeld) wakeLock.release()
                pendingResult.finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS: ${e.message}", e)
            if (wakeLock.isHeld) wakeLock.release()
            pendingResult.finish()
        }
    }
}
