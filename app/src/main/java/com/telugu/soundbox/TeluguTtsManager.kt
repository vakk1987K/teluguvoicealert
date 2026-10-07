package com.telugu.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

enum class SoundboxLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val locale: Locale,
    val bannerSubtitle: String,
    val todayLabel: String,
    val confirmationGreeting: String
) {
    TELUGU(
        code = "te",
        displayName = "Telugu",
        nativeName = "తెలుగు",
        locale = Locale("te", "IN"),
        bannerSubtitle = "తెలుగు చెల్లింపు సౌండ్‌బాక్స్",
        todayLabel = "Today (ఈరోజు)",
        confirmationGreeting = "తెలుగు సౌండ్‌బాక్స్ సిద్ధంగా ఉంది."
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi",
        nativeName = "हिन्दी",
        locale = Locale("hi", "IN"),
        bannerSubtitle = "हिंदी पेमेंट साउंडबॉक्स",
        todayLabel = "Today (आज)",
        confirmationGreeting = "हिंदी साउंडबॉक्स तैयार है।"
    ),
    MARATHI(
        code = "mr",
        displayName = "Marathi",
        nativeName = "मराठी",
        locale = Locale("mr", "IN"),
        bannerSubtitle = "मराठी पेमेंट साउंडबॉक्स",
        todayLabel = "Today (आज)",
        confirmationGreeting = "मराठी साउंडबॉक्स तयार आहे."
    ),
    GUJARATI(
        code = "gu",
        displayName = "Gujarati",
        nativeName = "ગુજરાતી",
        locale = Locale("gu", "IN"),
        bannerSubtitle = "ગુજરાતી પેમેન્ટ સાઉન્ડબૉક્સ",
        todayLabel = "Today (આજે)",
        confirmationGreeting = "ગુજરાતી સાઉન્ડબૉક્સ તૈયાર છે."
    ),
    TAMIL(
        code = "ta",
        displayName = "Tamil",
        nativeName = "தமிழ்",
        locale = Locale("ta", "IN"),
        bannerSubtitle = "தமிழ் கட்டண சவுண்ட்பாக்ஸ்",
        todayLabel = "Today (இன்று)",
        confirmationGreeting = "தமிழ் சவுண்ட்பாக்ஸ் தயார்."
    ),
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeName = "English",
        locale = Locale("en", "IN"),
        bannerSubtitle = "All-in-One Payment Soundbox",
        todayLabel = "Today",
        confirmationGreeting = "Payment Soundbox is ready."
    );

    companion object {
        fun fromCode(code: String): SoundboxLanguage {
            return values().firstOrNull { it.code.equals(code, ignoreCase = true) } ?: TELUGU
        }
    }
}

object IndianRupeeToWords {

    fun toTelugu(amountDouble: Double): String {
        val amount = amountDouble.toLong()
        if (amount == 0L) return "సున్నా రూపాయలు"
        if (amount == 1L) return "ఒక రూపాయి"

        val parts = mutableListOf<String>()

        val crores = amount / 10000000L
        var remainder = amount % 10000000L
        if (crores > 0) {
            parts.add(if (crores == 1L) "ఒక కోటి" else "${convertUnderThousandTelugu(crores)} కోట్ల")
        }

        val lakhs = remainder / 100000L
        remainder %= 100000L
        if (lakhs > 0) {
            parts.add(if (lakhs == 1L) "ఒక లక్ష" else "${convertUnderThousandTelugu(lakhs)} లక్షల")
        }

        val thousands = remainder / 1000L
        remainder %= 1000L
        if (thousands > 0) {
            if (thousands == 1L && parts.isEmpty()) {
                parts.add("వెయ్యి")
            } else if (thousands == 1L) {
                parts.add("ఒక వెయ్యి")
            } else {
                parts.add("${convertUnderHundredTelugu(thousands)} వేల")
            }
        }

        val hundreds = remainder / 100L
        remainder %= 100L
        if (hundreds > 0) {
            if (hundreds == 1L && remainder == 0L) {
                parts.add("వంద")
            } else if (hundreds == 1L) {
                parts.add("నూట")
            } else {
                val hMap = mapOf(
                    2L to "రెండు వందల", 3L to "మూడు వందల", 4L to "నాలుగు వందల",
                    5L to "ఐదు వందల", 6L to "ఆరు వందల", 7L to "ఏడు వందల",
                    8L to "ఎనిమిది వందల", 9L to "తొమ్మిది వందల"
                )
                parts.add(hMap[hundreds] ?: "${convertUnderHundredTelugu(hundreds)} వందల")
            }
        }

        if (remainder > 0) {
            parts.add(convertUnderHundredTelugu(remainder))
        }

        val paise = ((amountDouble - amount) * 100).toInt()
        val baseWords = parts.joinToString(" ").trim()
        return if (paise > 0) {
            "$baseWords రూపాయల $paise పైసలు"
        } else {
            "$baseWords రూపాయలు"
        }
    }

    private fun convertUnderThousandTelugu(n: Long): String {
        val hundreds = n / 100L
        val rem = n % 100L
        val p = mutableListOf<String>()
        if (hundreds > 0) {
            val hMap = mapOf(
                1L to "వంద", 2L to "రెండు వందల", 3L to "మూడు వందల", 4L to "నాలుగు వందల",
                5L to "ఐదు వందల", 6L to "ఆరు వందల", 7L to "ఏడు వందల",
                8L to "ఎనిమిది వందల", 9L to "తొమ్మిది వందల"
            )
            p.add(hMap[hundreds] ?: "${convertUnderHundredTelugu(hundreds)} వందల")
        }
        if (rem > 0) {
            p.add(convertUnderHundredTelugu(rem))
        }
        return p.joinToString(" ").trim()
    }

    private fun convertUnderHundredTelugu(n: Long): String {
        if (n <= 0) return ""
        val units = mapOf(
            1L to "ఒకటి", 2L to "రెండు", 3L to "మూడు", 4L to "నాలుగు",
            5L to "ఐదు", 6L to "ఆరు", 7L to "ఏడు", 8L to "ఎనిమిది", 9L to "తొమ్మిది",
            10L to "పది", 11L to "పదకొండు", 12L to "పన్నెండు", 13L to "పదమూడు",
            14L to "పద్నాలుగు", 15L to "పదిహేను", 16L to "పదహారు", 17L to "పదిహేడు",
            18L to "పద్దెనిమిది", 19L to "పందొమ్మిది"
        )
        if (n in 1L..19L) return units[n] ?: ""

        val tens = mapOf(
            20L to "ఇరవై", 30L to "ముప్పై", 40L to "నలభై", 50L to "యాభై",
            60L to "అరవై", 70L to "డెబ్బై", 80L to "ఎనభై", 90L to "తొంభై"
        )
        val tenPart = (n / 10L) * 10L
        val unitPart = n % 10L
        val tenStr = tens[tenPart] ?: ""
        return if (unitPart == 0L) {
            tenStr
        } else {
            "$tenStr ${units[unitPart] ?: ""}"
        }
    }

    fun toHindi(amountDouble: Double): String {
        val amount = amountDouble.toLong()
        if (amount == 0L) return "शून्य रुपये"
        if (amount == 1L) return "एक रुपया"

        val parts = mutableListOf<String>()

        val crores = amount / 10000000L
        var remainder = amount % 10000000L
        if (crores > 0) parts.add(convertUnderHundredHindi(crores) + " करोड़")

        val lakhs = remainder / 100000L
        remainder %= 100000L
        if (lakhs > 0) parts.add(convertUnderHundredHindi(lakhs) + " लाख")

        val thousands = remainder / 1000L
        remainder %= 1000L
        if (thousands > 0) parts.add(convertUnderHundredHindi(thousands) + " हज़ार")

        val hundreds = remainder / 100L
        remainder %= 100L
        if (hundreds > 0) parts.add(convertUnderHundredHindi(hundreds) + " सौ")

        if (remainder > 0) parts.add(convertUnderHundredHindi(remainder))

        val paise = ((amountDouble - amount) * 100).toInt()
        val baseWords = parts.joinToString(" ").trim()
        return if (paise > 0) "$baseWords रुपये $paise पैसे" else "$baseWords रुपये"
    }

    private fun convertUnderHundredHindi(n: Long): String {
        val map = mapOf(
            1L to "एक", 2L to "दो", 3L to "तीन", 4L to "चार", 5L to "पाँच",
            6L to "छह", 7L to "सात", 8L to "आठ", 9L to "नौ", 10L to "दस",
            11L to "ग्यारह", 12L to "बारह", 13L to "तेरह", 14L to "चौदह", 15L to "पंद्रह",
            16L to "सोलह", 17L to "सत्रह", 18L to "अठारह", 19L to "उन्नीस", 20L to "बीस",
            21L to "इक्कीस", 22L to "बाईस", 23L to "तेईस", 24L to "चौबीस", 25L to "पच्चीस",
            30L to "तीस", 40L to "चालीस", 50L to "पचास",
            60L to "साठ", 70L to "सत्तर", 75L to "पिचहत्तर", 80L to "अस्सी", 90L to "नब्बे"
        )
        if (map.containsKey(n)) return map[n]!!
        val ten = (n / 10L) * 10L
        val u = n % 10L
        return "${map[ten] ?: ""} ${map[u] ?: ""}".trim()
    }

    fun toMarathi(amountDouble: Double): String {
        val amount = amountDouble.toLong()
        if (amount == 0L) return "शून्य रुपये"
        if (amount == 1L) return "एक रुपया"

        val parts = mutableListOf<String>()

        val crores = amount / 10000000L
        var remainder = amount % 10000000L
        if (crores > 0) parts.add(convertUnderHundredMarathi(crores) + " कोटी")

        val lakhs = remainder / 100000L
        remainder %= 100000L
        if (lakhs > 0) parts.add(convertUnderHundredMarathi(lakhs) + " लाख")

        val thousands = remainder / 1000L
        remainder %= 1000L
        if (thousands > 0) parts.add(convertUnderHundredMarathi(thousands) + " हजार")

        val hundreds = remainder / 100L
        remainder %= 100L
        if (hundreds > 0) parts.add(if (hundreds == 1L) "एकशे" else convertUnderHundredMarathi(hundreds) + "शे")

        if (remainder > 0) parts.add(convertUnderHundredMarathi(remainder))

        val baseWords = parts.joinToString(" ").trim()
        return "$baseWords रुपये"
    }

    private fun convertUnderHundredMarathi(n: Long): String {
        val map = mapOf(
            1L to "एक", 2L to "दोन", 3L to "तीन", 4L to "चार", 5L to "पाच",
            6L to "सहा", 7L to "सात", 8L to "आठ", 9L to "नऊ", 10L to "दहा",
            11L to "अकरा", 12L to "बारा", 13L to "तेरा", 14L to "चौदा", 15L to "पंधरा",
            16L to "सोळा", 17L to "सतरा", 18L to "अठरा", 19L to "एकोणीस", 20L to "वीस",
            21L to "एकवीस", 22L to "बावीस", 23L to "तेवीस", 24L to "चोवीस", 25L to "पंचवीस",
            30L to "तीस", 40L to "चाळीस", 50L to "पन्नास",
            60L to "साठ", 70L to "सत्तर", 80L to "ऐंशी", 90L to "नव्वद"
        )
        if (map.containsKey(n)) return map[n]!!
        val ten = (n / 10L) * 10L
        val u = n % 10L
        return "${map[ten] ?: ""} ${map[u] ?: ""}".trim()
    }

    fun toGujarati(amountDouble: Double): String {
        val amount = amountDouble.toLong()
        if (amount == 0L) return "શૂન્ય રૂપિયા"
        if (amount == 1L) return "એક રૂપિયો"

        val parts = mutableListOf<String>()

        val crores = amount / 10000000L
        var remainder = amount % 10000000L
        if (crores > 0) parts.add(convertUnderHundredGujarati(crores) + " કરોડ")

        val lakhs = remainder / 100000L
        remainder %= 100000L
        if (lakhs > 0) parts.add(convertUnderHundredGujarati(lakhs) + " લાખ")

        val thousands = remainder / 1000L
        remainder %= 1000L
        if (thousands > 0) parts.add(convertUnderHundredGujarati(thousands) + " હજાર")

        val hundreds = remainder / 100L
        remainder %= 100L
        if (hundreds > 0) parts.add(if (hundreds == 1L) "એકસો" else convertUnderHundredGujarati(hundreds) + "સો")

        if (remainder > 0) parts.add(convertUnderHundredGujarati(remainder))

        val baseWords = parts.joinToString(" ").trim()
        return "$baseWords રૂપિયા"
    }

    private fun convertUnderHundredGujarati(n: Long): String {
        val map = mapOf(
            1L to "એક", 2L to "બે", 3L to "ત્રણ", 4L to "ચાર", 5L to "પાંચ",
            6L to "છ", 7L to "સાત", 8L to "આઠ", 9L to "નવ", 10L to "દસ",
            11L to "અગિયાર", 12L to "બાર", 13L to "તેર", 14L to "ચૌદ", 15L to "પંદર",
            16L to "સોળ", 17L to "સત્તર", 18L to "અઢાર", 19L to "ઓગણીસ", 20L to "વીસ",
            25L to "પચીસ", 30L to "ત્રીસ", 40L to "ચાલીસ", 50L to "પચાસ",
            60L to "સાઈઠ", 70L to "સિત્તેર", 80L to "એંસી", 90L to "નેવું"
        )
        if (map.containsKey(n)) return map[n]!!
        val ten = (n / 10L) * 10L
        val u = n % 10L
        return "${map[ten] ?: ""} ${map[u] ?: ""}".trim()
    }

    fun toTamil(amountDouble: Double): String {
        val amount = amountDouble.toLong()
        if (amount == 0L) return "பூஜ்யம் ரூபாய்"
        if (amount == 1L) return "ஒரு ரூபாய்"

        val parts = mutableListOf<String>()

        val crores = amount / 10000000L
        var remainder = amount % 10000000L
        if (crores > 0) parts.add(convertUnderHundredTamil(crores) + " கோடி")

        val lakhs = remainder / 100000L
        remainder %= 100000L
        if (lakhs > 0) parts.add(convertUnderHundredTamil(lakhs) + " லட்சம்")

        val thousands = remainder / 1000L
        remainder %= 1000L
        if (thousands > 0) parts.add(if (thousands == 1L) "ஆயிரத்து" else convertUnderHundredTamil(thousands) + " ஆயிரம்")

        val hundreds = remainder / 100L
        remainder %= 100L
        if (hundreds > 0) parts.add(if (hundreds == 1L) "நூற்று" else convertUnderHundredTamil(hundreds) + " நூறு")

        if (remainder > 0) parts.add(convertUnderHundredTamil(remainder))

        val baseWords = parts.joinToString(" ").trim()
        return "$baseWords ரூபாய்"
    }

    private fun convertUnderHundredTamil(n: Long): String {
        val map = mapOf(
            1L to "ஒன்று", 2L to "இரண்டு", 3L to "மூன்று", 4L to "நான்கு", 5L to "ஐந்து",
            6L to "ஆறு", 7L to "ஏழு", 8L to "எட்டு", 9L to "ஒன்பது", 10L to "பத்து",
            11L to "பதினொன்று", 12L to "பன்னிரண்டு", 13L to "பதிமூன்று", 14L to "பதினான்கு",
            15L to "பதினைந்து", 16L to "பதினாறு", 17L to "பதினேழு", 18L to "பதினெட்டு",
            19L to "பத்தொன்பது", 20L to "இருபது", 30L to "முப்பது", 40L to "நாற்பது",
            50L to "ஐம்பது", 60L to "அறுபது", 70L to "எழுபது", 80L to "எண்பது", 90L to "தொண்ணூறு"
        )
        if (map.containsKey(n)) return map[n]!!
        val ten = (n / 10L) * 10L
        val u = n % 10L
        return "${map[ten] ?: ""} ${map[u] ?: ""}".trim()
    }

    fun toEnglish(amountDouble: Double): String {
        val amount = amountDouble.toLong()
        if (amount == 0L) return "Zero rupees"
        if (amount == 1L) return "One rupee"

        val parts = mutableListOf<String>()

        val crores = amount / 10000000L
        var remainder = amount % 10000000L
        if (crores > 0) parts.add(convertUnderThousandEnglish(crores) + " crore")

        val lakhs = remainder / 100000L
        remainder %= 100000L
        if (lakhs > 0) parts.add(convertUnderThousandEnglish(lakhs) + " lakh")

        val thousands = remainder / 1000L
        remainder %= 1000L
        if (thousands > 0) parts.add(convertUnderThousandEnglish(thousands) + " thousand")

        val hundreds = remainder / 100L
        remainder %= 100L
        if (hundreds > 0) parts.add(convertUnderThousandEnglish(hundreds) + " hundred")

        if (remainder > 0) parts.add(convertUnderHundredEnglish(remainder))

        val paise = ((amountDouble - amount) * 100).toInt()
        val baseWords = parts.joinToString(" ").trim()
        return if (paise > 0) "$baseWords rupees and $paise paise" else "$baseWords rupees"
    }

    private fun convertUnderThousandEnglish(n: Long): String {
        val hundreds = n / 100L
        val rem = n % 100L
        val p = mutableListOf<String>()
        if (hundreds > 0) {
            p.add(convertUnderHundredEnglish(hundreds) + " hundred")
        }
        if (rem > 0) {
            p.add(convertUnderHundredEnglish(rem))
        }
        return p.joinToString(" ").trim()
    }

    private fun convertUnderHundredEnglish(n: Long): String {
        val units = mapOf(
            1L to "one", 2L to "two", 3L to "three", 4L to "four", 5L to "five",
            6L to "six", 7L to "seven", 8L to "eight", 9L to "nine", 10L to "ten",
            11L to "eleven", 12L to "twelve", 13L to "thirteen", 14L to "fourteen",
            15L to "fifteen", 16L to "sixteen", 17L to "seventeen", 18L to "eighteen", 19L to "nineteen"
        )
        if (n in 1L..19L) return units[n] ?: ""
        val tens = mapOf(
            20L to "twenty", 30L to "thirty", 40L to "forty", 50L to "fifty",
            60L to "sixty", 70L to "seventy", 80L to "eighty", 90L to "ninety"
        )
        val ten = (n / 10L) * 10L
        val u = n % 10L
        val tenStr = tens[ten] ?: ""
        return if (u == 0L) tenStr else "$tenStr ${units[u] ?: ""}".trim()
    }
}

object LanguageManager {
    private const val PREFS_NAME = "soundbox_lang_prefs"
    private const val KEY_LANG = "soundbox_language"

    fun getLanguage(context: Context): SoundboxLanguage {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_LANG, "te") ?: "te"
        return SoundboxLanguage.fromCode(code)
    }

    fun setLanguage(context: Context, language: SoundboxLanguage) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, language.code).apply()
        TeluguTtsManager.setLanguage(context, language)
    }

    /**
     * Converts numeric amount (e.g. 120, 2200) into native, grammatically correct
     * Indian spoken words so Text-To-Speech clearly announces the amount in the chosen language.
     */
    fun getAnnouncementSentence(
        language: SoundboxLanguage,
        amountStr: String
    ): String {
        val cleanAmount = amountStr.replace(",", "").toDoubleOrNull() ?: 0.0

        return when (language) {
            SoundboxLanguage.TELUGU -> {
                val words = IndianRupeeToWords.toTelugu(cleanAmount)
                "$words మీ ఖాతాలో జమ అయ్యాయి."
            }
            SoundboxLanguage.HINDI -> {
                val words = IndianRupeeToWords.toHindi(cleanAmount)
                "$words आपके खाते में प्राप्त हुए हैं।"
            }
            SoundboxLanguage.MARATHI -> {
                val words = IndianRupeeToWords.toMarathi(cleanAmount)
                "$words आपल्या खात्यात जमा झाले आहेत."
            }
            SoundboxLanguage.GUJARATI -> {
                val words = IndianRupeeToWords.toGujarati(cleanAmount)
                "$words તમારા ખાતામાં જમા થયા છે."
            }
            SoundboxLanguage.TAMIL -> {
                val words = IndianRupeeToWords.toTamil(cleanAmount)
                "$words உங்கள் கணக்கில் வரவு வைக்கப்பட்டது."
            }
            SoundboxLanguage.ENGLISH -> {
                val words = IndianRupeeToWords.toEnglish(cleanAmount)
                "$words received successfully."
            }
        }
    }
}

object TeluguTtsManager {
    private const val TAG = "SoundboxTtsManager"
    private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false
    private val initCallbacks = mutableListOf<() -> Unit>()
    private var currentLanguage: SoundboxLanguage = SoundboxLanguage.TELUGU
    private val pendingQueue = mutableListOf<Pair<String, (() -> Unit)?>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var activeWakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: Any? = null

    fun init(context: Context, onReady: (() -> Unit)? = null) {
        val appContext = context.applicationContext

        synchronized(lock) {
            if (isInitialized && tts != null) {
                onReady?.invoke()
                return
            }
            if (onReady != null) {
                initCallbacks.add(onReady)
            }
            if (isInitializing) {
                return
            }
            isInitializing = true
        }

        currentLanguage = LanguageManager.getLanguage(appContext)

        mainHandler.post {
            val listener = TextToSpeech.OnInitListener { status ->
                synchronized(lock) {
                    isInitializing = false
                }
                if (status == TextToSpeech.SUCCESS) {
                    configureAudioAttributes()
                    tts?.setSpeechRate(0.95f)
                    tts?.setPitch(1.0f)

                    applyLanguage(currentLanguage)
                    isInitialized = true
                    Log.i(TAG, "TextToSpeech initialized successfully on Android ${Build.VERSION.SDK_INT}")

                    val callbacksToRun: List<() -> Unit>
                    synchronized(lock) {
                        callbacksToRun = ArrayList(initCallbacks)
                        initCallbacks.clear()
                    }
                    for (cb in callbacksToRun) {
                        try {
                            cb.invoke()
                        } catch (e: Exception) {
                            Log.w(TAG, "Init callback error: ${e.message}")
                        }
                    }

                    synchronized(pendingQueue) {
                        for ((text, callback) in pendingQueue) {
                            speakText(appContext, text, callback)
                        }
                        pendingQueue.clear()
                    }
                } else {
                    Log.w(TAG, "TextToSpeech init status failed: $status, attempting fallback to default engine")
                    // If preferred Google engine failed, retry with default constructor
                    try {
                        tts = TextToSpeech(appContext) { fallbackStatus ->
                            if (fallbackStatus == TextToSpeech.SUCCESS) {
                                configureAudioAttributes()
                                applyLanguage(currentLanguage)
                                isInitialized = true
                                Log.i(TAG, "Fallback TextToSpeech initialized successfully")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback TextToSpeech error: ${e.message}")
                    }
                }
            }

            try {
                // On Android 14 (API 34), Android 15 (API 35), and Android 16 (API 36+):
                // Prefer Google TTS engine if available (bypasses vendor restrictions on Samsung/MIUI)
                var initializedWithGoogle = false
                try {
                    val tempTts = TextToSpeech(appContext, null)
                    val engines = tempTts.engines
                    tempTts.shutdown()
                    val hasGoogleTts = engines.any { it.name.equals(GOOGLE_TTS_PACKAGE, ignoreCase = true) }
                    if (hasGoogleTts) {
                        Log.d(TAG, "Found Google TTS engine, binding directly for reliable speech")
                        tts = TextToSpeech(appContext, listener, GOOGLE_TTS_PACKAGE)
                        initializedWithGoogle = true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Engine probe check: ${e.message}")
                }

                if (!initializedWithGoogle) {
                    tts = TextToSpeech(appContext, listener)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error instantiating TextToSpeech: ${e.message}")
                synchronized(lock) {
                    isInitializing = false
                }
            }
        }
    }

    private fun configureAudioAttributes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val sdk = Build.VERSION.SDK_INT
            val audioAttributes = when {
                sdk >= 36 -> {
                    // Android 16+ future-proof sonification with audibility enforced
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                }
                sdk in 34..35 -> {
                    // Android 14 & 15 high-priority sonification that pierces locked screens
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                }
                else -> {
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                }
            }
            try {
                tts?.setAudioAttributes(audioAttributes)
            } catch (e: Exception) {
                Log.w(TAG, "setAudioAttributes error: ${e.message}")
            }
        }
    }

    fun setLanguage(context: Context, language: SoundboxLanguage) {
        currentLanguage = language
        if (isInitialized && tts != null) {
            applyLanguage(language)
        } else {
            init(context)
        }
    }

    fun isLanguageSupported(language: SoundboxLanguage): Boolean {
        return try {
            val avail = tts?.isLanguageAvailable(language.locale)
            avail != null && (avail == TextToSpeech.LANG_AVAILABLE ||
                    avail == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    avail == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Attempts to apply language. If the phone does NOT support the regional language pack,
     * falls back safely to Indian/US English to prevent any silent crash or missing speech!
     */
    fun applyLanguage(language: SoundboxLanguage): Boolean {
        return try {
            val result = tts?.setLanguage(language.locale)
            val isSupported = result != null &&
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            if (!isSupported) {
                Log.w(TAG, "Language ${language.code} not directly supported (code $result), trying fallback English")
                val enInResult = tts?.setLanguage(Locale("en", "IN"))
                if (enInResult == TextToSpeech.LANG_MISSING_DATA || enInResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val usResult = tts?.setLanguage(Locale.US)
                    if (usResult == TextToSpeech.LANG_MISSING_DATA || usResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.ENGLISH
                    }
                }
            }
            isSupported
        } catch (e: Exception) {
            try { tts?.language = Locale.ENGLISH } catch (ex: Exception) {}
            false
        }
    }

    fun previewLanguage(context: Context, language: SoundboxLanguage) {
        val appContext = context.applicationContext
        setLanguage(appContext, language)

        wakeUpDevice(appContext)

        mainHandler.post {
            if (!isInitialized || tts == null) {
                init(appContext) {
                    val supported = applyLanguage(language)
                    val greeting = if (supported) {
                        language.confirmationGreeting
                    } else {
                        "${language.displayName} sound box is ready."
                    }
                    speakText(appContext, greeting)
                }
            } else {
                val supported = applyLanguage(language)
                val greeting = if (supported) {
                    language.confirmationGreeting
                } else {
                    "${language.displayName} sound box is ready."
                }
                speakText(appContext, greeting)
            }
        }
    }

    /**
     * Announce incoming payment with chime and natural voice alert.
     * Uses USAGE_ASSISTANCE_SONIFICATION so audio ALWAYS plays even if phone is locked,
     * muted, or in background on Android 14, 15, and 16.
     * Guaranteed never to be silent!
     */
    fun announcePayment(
        context: Context,
        amount: String,
        payerName: String? = null,
        language: SoundboxLanguage = LanguageManager.getLanguage(context),
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext

        wakeUpDevice(appContext)

        // Ensure Media and System volume is clearly audible (Safely unmutes without DND restrictions)
        try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                try {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                } catch (e: Exception) {}

                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (currentVol < (maxVol * 0.5)) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Volume boost error: ${e.message}")
        }

        // Haptic feedback alert
        try {
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200L)
                }
            }
        } catch (e: Exception) {}

        // 1. Play crisp bell chime. Finished and released BEFORE speech to avoid hardware audio conflicts!
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            mainHandler.postDelayed({
                try {
                    toneGen.release()
                } catch (e: Exception) {}
            }, 180L)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error: ${e.message}")
        }

        // 2. Schedule speech at 220ms (Right after chime completes and releases audio channel)
        mainHandler.postDelayed({
            val deliverSpeech = {
                val supported = isLanguageSupported(language)
                val textToSpeak: String

                if (supported || language == SoundboxLanguage.ENGLISH) {
                    applyLanguage(language)
                    textToSpeak = LanguageManager.getAnnouncementSentence(language, amount)
                } else {
                    // Intelligent fallback: If phone lacks regional voice data (e.g. Samsung TTS missing Telugu),
                    // speak in crystal clear English so the merchant NEVER experiences silence!
                    val cleanNum = amount.replace(",", "")
                    val cleanDouble = cleanNum.toDoubleOrNull() ?: 0.0
                    val englishWords = IndianRupeeToWords.toEnglish(cleanDouble)
                    val payer = payerName?.ifBlank { "UPI" } ?: "UPI"

                    val enSupported = applyLanguage(SoundboxLanguage.ENGLISH)
                    if (!enSupported) {
                        try { tts?.language = Locale.US } catch (e: Exception) {}
                    }
                    textToSpeak = "Received $englishWords on $payer."
                }

                Log.i(TAG, "Delivering speech: '$textToSpeak' in ${language.displayName}")
                speakText(appContext, textToSpeak, onComplete)
            }

            if (!isInitialized || tts == null) {
                init(appContext) {
                    deliverSpeech()
                }
            } else {
                deliverSpeech()
            }
        }, 220L)
    }

    fun speakText(context: Context, text: String, onComplete: (() -> Unit)? = null) {
        if (tts == null || !isInitialized) {
            synchronized(pendingQueue) {
                pendingQueue.add(Pair(text, onComplete))
            }
            init(context)
            return
        }

        val appContext = context.applicationContext
        requestAudioFocus(appContext)

        val utteranceId = "SoundboxAlert_${System.currentTimeMillis()}"

        val timeoutRunnable = Runnable {
            Log.w(TAG, "Utterance timeout reached, releasing locks")
            cleanupSpeech(appContext)
            onComplete?.invoke()
        }
        mainHandler.postDelayed(timeoutRunnable, 14000L)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d(TAG, "Started speaking: $id")
            }

            override fun onDone(id: String?) {
                Log.d(TAG, "Completed speaking: $id")
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post {
                    cleanupSpeech(appContext)
                    onComplete?.invoke()
                }
            }

            override fun onError(id: String?) {
                Log.e(TAG, "TTS Utterance error for: $id")
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post {
                    cleanupSpeech(appContext)
                    onComplete?.invoke()
                }
            }
        })

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (speakResult != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak() returned error code: $speakResult, retrying without params")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun requestAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun cleanupSpeech(context: Context) {
        releaseWakeLock()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (audioFocusRequest as? AudioFocusRequest)?.let {
                audioManager.abandonAudioFocusRequest(it)
                audioFocusRequest = null
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun wakeUpDevice(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                if (activeWakeLock?.isHeld == true) {
                    activeWakeLock?.release()
                }
                @Suppress("DEPRECATION")
                activeWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "TeluguSoundbox:TtsWakeLock"
                )
                activeWakeLock?.acquire(20000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquire failed: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (activeWakeLock?.isHeld == true) {
                activeWakeLock?.release()
            }
            activeWakeLock = null
        } catch (e: Exception) {}
    }
}
