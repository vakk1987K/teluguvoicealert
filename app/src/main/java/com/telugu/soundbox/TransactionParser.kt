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

    private val IGNORED_WORDS = setOf(
        "otp", "one time password", "verification code", "secret code",
        "debited", "spent", "withdrawn", "paid to", "transferred to",
        "purchase", "atm wdl", "card ending", "due date", "emi due", "bill due", "request"
    )

    fun parse(smsOrNotificationText: String, defaultProvider: String = ""): ParsedPayment? {
        val lower = smsOrNotificationText.lowercase()

        for (ignored in IGNORED_WORDS) {
            if (lower.contains(ignored)) {
                return null
            }
        }

        val isCreditKeyword = lower.contains("credited") ||
                lower.contains("received") ||
                lower.contains("paid you") ||
                lower.contains("has paid") ||
                lower.contains("sent you") ||
                lower.contains("payment of") ||
                lower.contains("deposited") ||
                lower.contains("added to your account") ||
                lower.contains("received on phonepe") ||
                lower.contains("received on paytm") ||
                lower.contains("received on gpay")

        if (!isCreditKeyword) {
            return null
        }

        val amountPattern = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val amountMatcher = amountPattern.matcher(smsOrNotificationText)
        if (!amountMatcher.find()) {
            return null
        }

        val rawAmountStr = amountMatcher.group(1)?.replace(",", "") ?: return null
        val amount = rawAmountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

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
            Pattern.compile("([A-Za-z\\s]{3,25})\\s+(?:paid you|has paid|sent you)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:received\\s+from|transferred\\s+by|sent\\s+by|paid\\s+by|from\\s+user|from)\\s+([A-Za-z\\s]{3,30})(?:\\s+(?:via|through|ref|utr|on|\\.|-|,|\\/)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:by\\s+upi\\s+user|remitter)\\s*[:\\-]?\\s*([A-Za-z\\s]{3,30})(?:\\s+(?:on|ref|utr|\\.)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:from\\s+vpa\\s+|vpa\\s+)([a-zA-Z0-9.\\-_]{2,25}@[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("upi\\/[a-zA-Z0-9_-]+\\/[a-zA-Z0-9_-]+\\/([A-Za-z\\s]{3,30})", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: continue

                if (candidate.contains("@")) {
                    val userPart = candidate.substringBefore("@").replace(Regex("[0-9._-]"), " ").trim()
                    if (userPart.length >= 3) {
                        return capitalizeWords(userPart)
                    }
                    return candidate.substringBefore("@")
                }

                val cleaned = candidate.replace(Regex("[^a-zA-Z\\s]"), "").trim()
                if (cleaned.length in 3..25 && !isCommonFalsePositive(cleaned)) {
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
            "credited", "phonepe", "paytm", "gpay", "google pay", "bharatpe"
        )
    }

    private fun detectBank(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("gpay") || lower.contains("google pay") -> "Google Pay"
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
