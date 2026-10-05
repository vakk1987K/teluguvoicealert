package com.telugu.soundbox

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class StoredPayment(
    val id: String,
    val amount: Double,
    val formattedAmount: String,
    val payerName: String,
    val bank: String,
    val timestamp: Long,
    val rawText: String
)

data class PeriodSummary(
    val totalAmount: Double,
    val totalOrders: Int
)

data class PaymentSummary(
    val today: PeriodSummary,
    val sevenDays: PeriodSummary,
    val fifteenDays: PeriodSummary,
    val thirtyDays: PeriodSummary
)

object PaymentStorage {
    private const val TAG = "PaymentStorage"
    private const val PREFS_NAME = "telugu_soundbox_temp_storage"
    private const val KEY_PAYMENTS = "cached_payments_list"
    private const val KEY_ALERTS_ENABLED = "soundbox_alerts_active"
    private const val MAX_RECORDS = 250

    const val ACTION_NEW_PAYMENT = "com.telugu.soundbox.ACTION_NEW_PAYMENT"
    const val EXTRA_AMOUNT = "extra_amount"
    const val EXTRA_PAYER = "extra_payer"
    const val EXTRA_BANK = "extra_bank"
    const val EXTRA_TEXT = "extra_text"

    fun isAlertsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ALERTS_ENABLED, true)
    }

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
    }

    // Deduplication window: 45 seconds ensures that if PhonePe, Google Pay, Paytm, MobiKwik,
    // or Bank SMS all alert for the same payment, only ONE announcement and ONE record is stored.
    private val recentProcessedAmounts = mutableMapOf<String, Long>()
    private const val DEDUPLICATION_WINDOW_MS = 45000L

    @Synchronized
    fun isDuplicate(context: Context, amount: Double, formattedAmount: String): Boolean {
        val now = System.currentTimeMillis()
        val normalized = normalizeAmount(formattedAmount)

        // 1. Clean up entries older than 90 seconds
        val iterator = recentProcessedAmounts.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > 90000L) {
                iterator.remove()
            }
        }

        // 2. Check in-memory map
        val lastTime = recentProcessedAmounts[normalized]
        if (lastTime != null && (now - lastTime) < DEDUPLICATION_WINDOW_MS) {
            Log.d(TAG, "Duplicate check TRUE for amount ₹$normalized (processed ${now - lastTime}ms ago)")
            return true
        }

        // 3. Check persistent storage payments
        val currentList = getPayments(context)
        val existingDuplicate = currentList.firstOrNull { existing ->
            Math.abs(existing.amount - amount) < 0.01 && (now - existing.timestamp) < DEDUPLICATION_WINDOW_MS
        }
        if (existingDuplicate != null) {
            Log.d(TAG, "Duplicate check TRUE for amount ₹$amount found in storage (${now - existingDuplicate.timestamp}ms ago)")
            recentProcessedAmounts[normalized] = now
            return true
        }

        // Record this payment timestamp so subsequent duplicate alerts are ignored
        recentProcessedAmounts[normalized] = now
        return false
    }

    @Synchronized
    fun isDuplicate(amount: String): Boolean {
        val now = System.currentTimeMillis()
        val normalized = normalizeAmount(amount)
        val lastTime = recentProcessedAmounts[normalized]
        if (lastTime != null && (now - lastTime) < DEDUPLICATION_WINDOW_MS) {
            return true
        }
        recentProcessedAmounts[normalized] = now
        return false
    }

    private fun normalizeAmount(amount: String): String {
        return amount.replace(",", "").toDoubleOrNull()?.let {
            if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.2f", it)
        } ?: amount.trim()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun addPayment(
        context: Context,
        amount: Double,
        formattedAmount: String,
        payerName: String?,
        bank: String,
        rawText: String
    ): StoredPayment? {
        val currentList = getPayments(context).toMutableList()
        val now = System.currentTimeMillis()

        val existingDuplicate = currentList.firstOrNull { existing ->
            Math.abs(existing.amount - amount) < 0.01 && (now - existing.timestamp) < DEDUPLICATION_WINDOW_MS
        }

        if (existingDuplicate != null) {
            Log.w(TAG, "Prevented duplicate payment insertion for ₹$formattedAmount. Existing record ID: ${existingDuplicate.id}")
            return null
        }

        val newRecord = StoredPayment(
            id = System.currentTimeMillis().toString(),
            amount = amount,
            formattedAmount = formattedAmount,
            payerName = payerName ?: "",
            bank = bank,
            timestamp = now,
            rawText = rawText
        )

        currentList.add(0, newRecord)

        if (currentList.size > MAX_RECORDS) {
            currentList.removeAt(currentList.lastIndex)
        }

        saveList(context, currentList)
        return newRecord
    }

    @Synchronized
    fun getPayments(context: Context): List<StoredPayment> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_PAYMENTS, null) ?: return emptyList()

        val list = mutableListOf<StoredPayment>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    StoredPayment(
                        id = obj.optString("id", ""),
                        amount = obj.optDouble("amount", 0.0),
                        formattedAmount = obj.optString("formattedAmount", "0"),
                        payerName = obj.optString("payerName", ""),
                        bank = obj.optString("bank", "Bank"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        rawText = obj.optString("rawText", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val deduped = mutableListOf<StoredPayment>()
        for (item in list) {
            val isDupe = deduped.any { existing ->
                Math.abs(existing.amount - item.amount) < 0.01 &&
                        Math.abs(existing.timestamp - item.timestamp) < DEDUPLICATION_WINDOW_MS
            }
            if (!isDupe) {
                deduped.add(item)
            }
        }

        return deduped
    }

    @Synchronized
    fun getSummary(context: Context): PaymentSummary {
        val payments = getPayments(context)
        val now = System.currentTimeMillis()

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
        val fifteenDaysAgo = now - (15L * 24 * 60 * 60 * 1000)
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        var todaySum = 0.0
        var todayCount = 0

        var sevenDaySum = 0.0
        var sevenDayCount = 0

        var fifteenDaySum = 0.0
        var fifteenDayCount = 0

        var thirtyDaySum = 0.0
        var thirtyDayCount = 0

        for (p in payments) {
            if (p.timestamp >= startOfToday) {
                todaySum += p.amount
                todayCount++
            }
            if (p.timestamp >= sevenDaysAgo) {
                sevenDaySum += p.amount
                sevenDayCount++
            }
            if (p.timestamp >= fifteenDaysAgo) {
                fifteenDaySum += p.amount
                fifteenDayCount++
            }
            if (p.timestamp >= thirtyDaysAgo) {
                thirtyDaySum += p.amount
                thirtyDayCount++
            }
        }

        return PaymentSummary(
            today = PeriodSummary(todaySum, todayCount),
            sevenDays = PeriodSummary(sevenDaySum, sevenDayCount),
            fifteenDays = PeriodSummary(fifteenDaySum, fifteenDayCount),
            thirtyDays = PeriodSummary(thirtyDaySum, thirtyDayCount)
        )
    }

    @Synchronized
    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_PAYMENTS).apply()
        recentProcessedAmounts.clear()
    }

    private fun saveList(context: Context, list: List<StoredPayment>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("amount", item.amount)
                put("formattedAmount", item.formattedAmount)
                put("payerName", item.payerName)
                put("bank", item.bank)
                put("timestamp", item.timestamp)
                put("rawText", item.rawText)
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_PAYMENTS, jsonArray.toString()).apply()
    }
}
