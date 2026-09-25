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

    private val STRICT_DEBIT_WORDS = setOf(
        "otp", "one time password", "verification code", "secret code",
        "debited", "spent", "withdrawn", "atm wdl", "card ending",
        "due date", "emi due", "bill due", "payment request", "requested money"
    )

    fun parse(smsOrNotificationText: String, defaultProvider: String = ""): ParsedPayment? {
        val lower = smsOrNotificationText.lowercase()

        // 1. Safety check: Ignore OTPs, debits, bills, or payment requests
        for (ignored in STRICT_DEBIT_WORDS) {
            if (lower.contains(ignored)) {
                return null
            }
        }

        // Safety check for "paid to" - only ignore if not "paid to you" or "paid to your"
        if (lower.contains("paid to") && !lower.contains("paid to you") && !lower.contains("paid to your")) {
            return null
        }
        if (lower.contains("sent to") && !lower.contains("sent to you") && !lower.contains("sent to your")) {
            return null
        }

        // 2. Extract Amount first (handles ₹210, Rs. 210, 210 Rs, INR 210, ₹ 2,000, 210/-, etc.)
        val amountPattern = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)|([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|inr|₹|rupees|\\/-)",
            Pattern.CASE_INSENSITIVE
        )
        val amountMatcher = amountPattern.matcher(smsOrNotificationText)
        if (!amountMatcher.find()) {
            return null
        }

        val rawAmountStr = (amountMatcher.group(1) ?: amountMatcher.group(2))?.replace(",", "") ?: return null
        val amount = rawAmountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // 3. Check for Credit Keywords (Bank SMS & UPI Apps: PhonePe, GPay, Paytm)
        val isCreditKeyword = lower.contains("credited") ||
                lower.contains("received") ||
                lower.contains("paid") ||
                lower.contains("sent") ||
                lower.contains("transfer") ||
                lower.contains("payment") ||
                lower.contains("deposited") ||
                lower.contains("added") ||
                lower.contains("successful") ||
                lower.contains("cleared") ||
                defaultProvider.isNotBlank()

        if (!isCreditKeyword) {
            return null
        }

        val payerName = extractPayerName(smsOrNotificationText)

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
