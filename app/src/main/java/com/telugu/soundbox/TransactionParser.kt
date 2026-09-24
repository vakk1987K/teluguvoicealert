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
        "debited", "spent", "withdrawn", "paid to", "sent to", "transferred to",
        "purchase", "atm wdl", "card ending", "due date", "emi due", "bill due"
    )

    fun parse(smsText: String): ParsedPayment? {
        val lower = smsText.lowercase()

        // 1. Safety check: Ignore OTPs and debit/expenditure SMS
        for (ignored in IGNORED_WORDS) {
            if (lower.contains(ignored)) {
                return null
            }
        }

        // 2. Check for Credit Keywords
        val isCreditKeyword = lower.contains("credited") ||
                lower.contains("received") ||
                lower.contains("deposited") ||
                lower.contains("added to your account") ||
                lower.contains("payment of rs")

        if (!isCreditKeyword) {
            return null
        }

        // 3. Extract Amount (handles Rs. 500, Rs.500.50, INR 1,250, ₹ 2000, etc.)
        val amountPattern = Pattern.compile(
            "(?:rs\\.?|inr|₹)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
        )
        val amountMatcher = amountPattern.matcher(smsText)
        if (!amountMatcher.find()) {
            return null
        }

        val rawAmountStr = amountMatcher.group(1)?.replace(",", "") ?: return null
        val amount = rawAmountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        // 4. Extract Payer Name ("so and so guy")
        val payerName = extractPayerName(smsText)

        // 5. Detect Bank / Provider
        val bank = detectBank(smsText)

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
            rawText = smsText
        )
    }

    private fun extractPayerName(text: String): String? {
        // Regex patterns to capture payer name:
        // e.g., "received from Ramesh Kumar", "from VPA suresh@okhdfcbank", "by UPI user Priya"
        val patterns = listOf(
            Pattern.compile("(?:received\\s+from|transferred\\s+by|sent\\s+by|paid\\s+by|from\\s+user|from)\\s+([A-Za-z\\s]{3,30})(?:\\s+(?:via|through|ref|utr|on|\\.|-|,|\\/)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:by\\s+upi\\s+user|remitter)\\s*[:\\-]?\\s*([A-Za-z\\s]{3,30})(?:\\s+(?:on|ref|utr|\\.)|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:from\\s+vpa\\s+|vpa\\s+)([a-zA-Z0-9.\\-_]{2,25}@[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("upi\\/[a-zA-Z0-9_-]+\\/[a-zA-Z0-9_-]+\\/([A-Za-z\\s]{3,30})", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim() ?: continue

                // If VPA contains @, extract the username part cleanly
                if (candidate.contains("@")) {
                    val userPart = candidate.substringBefore("@").replace(Regex("[0-9._-]"), " ").trim()
                    if (userPart.length >= 3) {
                        return capitalizeWords(userPart)
                    }
                    return candidate.substringBefore("@")
                }

                // Filter out non-person words
                val clean = candidate.replace(Regex("(?i)\\b(on|ref|utr|avl|bal|a/c|account|bank|via|through|upi)\\b.*"), "").trim()
                val invalid = setOf("your", "account", "bank", "branch", "atm", "credit", "debit", "balance", "available")
                if (invalid.contains(clean.lowercase()) || clean.length < 2) {
                    continue
                }

                return capitalizeWords(clean)
            }
        }
        return null
    }

    private fun detectBank(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("sbi") || lower.contains("state bank") -> "SBI"
            lower.contains("hdfc") -> "HDFC Bank"
            lower.contains("icici") -> "ICICI Bank"
            lower.contains("axis") -> "Axis Bank"
            lower.contains("kotak") -> "Kotak Mahindra"
            lower.contains("pnb") || lower.contains("punjab") -> "PNB"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("gpay") || lower.contains("google pay") -> "Google Pay"
            lower.contains("paytm") -> "Paytm"
            lower.contains("bhim") -> "BHIM UPI"
            else -> "Bank UPI"
        }
    }

    private fun capitalizeWords(input: String): String {
        return input.split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }
}
