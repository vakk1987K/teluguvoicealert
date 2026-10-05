package com.telugu.soundbox

import java.util.regex.Pattern

data class ParsedPayment(
    val isCredit: Boolean,
    val amount: Double,
    val formattedAmount: String,
    val payerName: String?,
    val bank: String,
    val rawText: String
)

object TransactionParser {

    // Unconditional debit and non-transaction rejection terms
    private val STRICT_DEBIT_DISQUALIFIERS = listOf(
        "debited", "debit", " dr.", " dr ", "dr/", "/dr", "dr alert",
        "you paid", "you have paid", "you've paid",
        "you sent", "you have sent", "you've sent",
        "you transferred", "you have transferred",
        "paid successfully to", "successful payment to",
        "spent", "spent on", "spent at",
        "withdrawn", "withdrawal", "atm wdl", "cash withdrawal",
        "purchase at", "purchased at", "buying at",
        "recharge of", "recharge successful", "recharged for",
        "bill paid", "bill payment", "due date", "emi due",
        "requested money", "payment request", "requested from you",
        "otp", "one time password", "verification code", "secret code"
    )

    // Directional debit phrases (unless followed by "you" or "your")
    private val DIRECTIONAL_DEBIT_PHRASES = listOf(
        "paid to",
        "payment to",
        "sent to",
        "money sent to",
        "transferred to"
    )

    // Positive incoming credit keywords - AT LEAST ONE MUST BE PRESENT!
    private val POSITIVE_CREDIT_PHRASES = listOf(
        "credited", "credit", "is credited", "has been credited", "credited with", "credited to",
        "received", "received payment", "payment received", "money received", "received from",
        "paid you", "has paid you", "paid to you", "paid to your",
        "sent you", "has sent you", "sent to you", "sent to your",
        "transferred to you", "transferred to your", "transferred by",
        "deposited into", "deposited to", "added to your",
        "money credited", "amount credited"
    )

    fun parse(smsOrNotificationText: String, defaultProvider: String = ""): ParsedPayment? {
        val lower = smsOrNotificationText.lowercase()

        // 1. REJECT ALL STRICT DEBITS (e.g. debited, spent, withdrawn, you paid, bill, OTP)
        for (debitWord in STRICT_DEBIT_DISQUALIFIERS) {
            if (lower.contains(debitWord)) {
                return null
            }
        }

        // 2. REJECT DIRECTIONAL DEBITS (e.g. "paid to Ramesh", "sent to Kiran")
        for (directional in DIRECTIONAL_DEBIT_PHRASES) {
            if (lower.contains(directional)) {
                // If it specifically says "paid to you" or "paid to your", it's credit; otherwise it is a debit!
                val isToUser = lower.contains("$directional you") || lower.contains("$directional your")
                if (!isToUser) {
                    return null
                }
            }
        }

        // 3. MUST CONTAIN AT LEAST ONE POSITIVE CREDIT CONFIRMATION PHRASE
        val hasPositiveCredit = POSITIVE_CREDIT_PHRASES.any { lower.contains(it) }
        if (!hasPositiveCredit) {
            return null
        }

        // 4. Extract Amount (handles ₹1300, ₹2200, ₹1,300.00, Rs 1300, INR 1300, 1300/-, 1300 Rs)
        val amountPattern = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)|(?:credited\\s+(?:with|by)|received|payment\\s+of|amount\\s+of)\\s+(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)|\\b([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|inr|₹|rupees|\\/-)",
            Pattern.CASE_INSENSITIVE
        )
        val amountMatcher = amountPattern.matcher(smsOrNotificationText)
        if (!amountMatcher.find()) {
            return null
        }

        val rawAmountStr = (amountMatcher.group(1) ?: amountMatcher.group(2) ?: amountMatcher.group(3))?.replace(",", "") ?: return null
        val amount = rawAmountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // 5. Extract Payer Name ("Ramesh paid you", "from Suresh", "by Priya", etc.)
        val payerName = extractPayerName(smsOrNotificationText)

        // 6. Detect Bank / Provider
        val detectedBank = detectBank(smsOrNotificationText)
        val bank = if (detectedBank != "Bank" || defaultProvider.isEmpty()) detectedBank else defaultProvider

        val formattedAmount = if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format("%.2f", amount)
        }

        return ParsedPayment(
            isCredit = true,
            amount = amount,
            formattedAmount = formattedAmount,
            payerName = payerName,
            bank = bank,
            rawText = smsOrNotificationText
        )
    }

    private fun extractPayerName(text: String): String? {
        val patterns = listOf(
            Pattern.compile("([A-Za-z\\s]{2,25})\\s+(?:paid\\s+you|has\\s+paid|sent\\s+you|sent|paid|transferred)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:received\\s+from|transferred\\s+by|sent\\s+by|paid\\s+by|from\\s+user|from)\\s+([A-Za-z\\s]{2,30})(?:\\s+(?:via|through|ref|utr|on|\\.|-|,|\\/)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:by\\s+upi\\s+user|remitter)\\s*[:\\-]?\\s*([A-Za-z\\s]{2,30})(?:\\s+(?:on|ref|utr|\\.)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:from\\s+vpa\\s+|vpa\\s+)([a-zA-Z0-9.\\-_]{2,25}@[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([A-Za-z\\s]{2,25})\\s*[:\\-]\\s*(?:rs\\.?|inr|₹)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: continue

                if (candidate.contains("@")) {
                    val userPart = candidate.substringBefore("@").replace(Regex("[0-9._-]"), " ").trim()
                    if (userPart.length >= 2) {
                        return capitalizeWords(userPart)
                    }
                    return candidate.substringBefore("@")
                }

                val cleaned = candidate.replace(Regex("[^a-zA-Z\\s]"), "").trim()
                if (cleaned.length in 2..25 && !isCommonFalsePositive(cleaned)) {
                    return capitalizeWords(cleaned)
                }
            }
        }
        return null
    }

    private fun isCommonFalsePositive(word: String): Boolean {
        val lower = word.lowercase()
        return lower in setOf(
            "bank", "account", "acct", "your", "upi", "vpa", "inr", "rs", "rupees",
            "ref", "utr", "imps", "neft", "rtgs", "available", "balance", "clearing",
            "credited", "phonepe", "paytm", "gpay", "google pay", "bharatpe", "google",
            "payment", "received", "money", "transfer", "successful"
        )
    }

    private fun detectBank(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("gpay") || lower.contains("google pay") || lower.contains("paisa") -> "Google Pay"
            lower.contains("paytm") -> "Paytm"
            lower.contains("mobikwik") -> "MobiKwik"
            lower.contains("bharatpe") -> "BharatPe"
            lower.contains("bhim") -> "BHIM UPI"
            lower.contains("sbi") || lower.contains("state bank") -> "SBI"
            lower.contains("hdfc") -> "HDFC"
            lower.contains("icici") -> "ICICI"
            lower.contains("axis") -> "Axis Bank"
            lower.contains("kotak") -> "Kotak"
            lower.contains("pnb") || lower.contains("punjab national") -> "PNB"
            lower.contains("bob") || lower.contains("bank of baroda") -> "BOB"
            lower.contains("canara") -> "Canara Bank"
            lower.contains("union") -> "Union Bank"
            lower.contains("andhra") -> "Andhra Bank"
            lower.contains("cred") -> "CRED"
            else -> "Bank"
        }
    }

    private fun capitalizeWords(text: String): String {
        return text.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
