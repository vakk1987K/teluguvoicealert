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

    // Deduplication window: 8 seconds ensures identical simultaneous notifications from UPI app + Bank SMS
    // don't double-trigger, while allowing separate consecutive payments (e.g. testing ₹120 twice)
    private val recentProcessedAmounts = mutableMapOf<String, Long>()
    private const val DEDUPLICATION_WINDOW_MS = 8000L

    @Synchronized
    fun isDuplicate(context: Context, amount: Double, formattedAmount: String): Boolean {
        val now = System.currentTimeMillis()
        val normalized = normalizeAmount(formattedAmount)

        // 1. Clean up entries older than 30 seconds
        val iterator = recentProcessedAmounts.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > 30000L) {
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

        // Record this payment timestamp so simultaneous duplicate broadcasts within 8s are ignored
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
                        formattedAmount = obj.optString("formattedAmount", ""),
                        payerName = obj.optString("payerName", ""),
                        bank = obj.optString("bank", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        rawText = obj.optString("rawText", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading stored payments: ${e.message}")
        }
        return list
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

    @Synchronized
    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_PAYMENTS).apply()
        recentProcessedAmounts.clear()
    }

    fun getSummary(context: Context): PaymentSummary {
        val list = getPayments(context)
        val now = System.currentTimeMillis()

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis

        val sevenDaysAgo = now - (7L * 24 * 60 * 60 * 1000)
        val fifteenDaysAgo = now - (15L * 24 * 60 * 60 * 1000)
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        var todayAmt = 0.0
        var todayCount = 0

        var sevenAmt = 0.0
        var sevenCount = 0

        var fifteenAmt = 0.0
        var fifteenCount = 0

        var thirtyAmt = 0.0
        var thirtyCount = 0

        for (p in list) {
            if (p.timestamp >= startOfToday) {
                todayAmt += p.amount
                todayCount++
            }
            if (p.timestamp >= sevenDaysAgo) {
                sevenAmt += p.amount
                sevenCount++
            }
            if (p.timestamp >= fifteenDaysAgo) {
                fifteenAmt += p.amount
                fifteenCount++
            }
            if (p.timestamp >= thirtyDaysAgo) {
                thirtyAmt += p.amount
                thirtyCount++
            }
        }

        return PaymentSummary(
            today = PeriodSummary(todayAmt, todayCount),
            sevenDays = PeriodSummary(sevenAmt, sevenCount),
            fifteenDays = PeriodSummary(fifteenAmt, fifteenCount),
            thirtyDays = PeriodSummary(thirtyAmt, thirtyCount)
        )
    }
}
