package com.telugu.soundbox

import android.content.Context
import android.content.SharedPreferences
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
    val thirtyDays: PeriodSummary
)

object PaymentStorage {
    private const val PREFS_NAME = "telugu_soundbox_temp_storage"
    private const val KEY_PAYMENTS = "cached_payments_list"
    private const val MAX_RECORDS = 250

    private var lastProcessedAmount: String = ""
    private var lastProcessedTime: Long = 0L

    @Synchronized
    fun isDuplicate(amount: String): Boolean {
        val now = System.currentTimeMillis()
        if (lastProcessedAmount == amount && (now - lastProcessedTime) < 8000L) {
            return true
        }
        lastProcessedAmount = amount
        lastProcessedTime = now
        return false
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
    ): StoredPayment {
        val currentList = getPayments(context).toMutableList()

        val newRecord = StoredPayment(
            id = System.currentTimeMillis().toString(),
            amount = amount,
            formattedAmount = formattedAmount,
            payerName = payerName ?: "",
            bank = bank,
            timestamp = System.currentTimeMillis(),
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
        return list
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
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000)

        var todaySum = 0.0
        var todayCount = 0

        var sevenDaySum = 0.0
        var sevenDayCount = 0

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
            if (p.timestamp >= thirtyDaysAgo) {
                thirtyDaySum += p.amount
                thirtyDayCount++
            }
        }

        return PaymentSummary(
            today = PeriodSummary(todaySum, todayCount),
            sevenDays = PeriodSummary(sevenDaySum, sevenDayCount),
            thirtyDays = PeriodSummary(thirtyDaySum, thirtyDayCount)
        )
    }

    @Synchronized
    fun clearAll(context: Context) {
        getPrefs(context).edit().remove(KEY_PAYMENTS).apply()
        lastProcessedAmount = ""
        lastProcessedTime = 0L
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
