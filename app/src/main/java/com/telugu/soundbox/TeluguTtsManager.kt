package com.telugu.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

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
            25L to "पच्चीस", 30L to "तीस", 40L to "चालीस", 50L to "पचास",
            60L to "साठ", 70L to "सत्तर", 75L to "पिचहत्तर", 80L to "अस्सी", 90L to "नब्बे"
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
            15L to "fifteen", 16L to "sixteen", 17L to "seventeen", 18L to "eighteen",
            19L to "nineteen"
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
     * Converts numeric amount (e.g. 7500) into native, grammatically correct
     * Indian spoken words so Text-To-Speech never reads digit-by-digit or cuts off!
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
                val words = IndianRupeeToWords.toHindi(cleanAmount)
                "$words आपल्या खात्यात जमा झाले आहेत."
            }
            SoundboxLanguage.GUJARATI -> {
                val words = IndianRupeeToWords.toHindi(cleanAmount)
                "$words તમારા ખાતામાં જમા થયા છે."
            }
            SoundboxLanguage.TAMIL -> {
                val words = IndianRupeeToWords.toEnglish(cleanAmount)
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
                    applyLanguage(currentLanguage)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        tts?.setAudioAttributes(audioAttributes)
                    }

                    tts?.setSpeechRate(0.92f)
                    tts?.setPitch(1.0f)

                    isInitialized = true

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
                    Log.w(TAG, "Failed to initialize default TextToSpeech, attempting Google TTS")
                    tts = TextToSpeech(appContext, { fallbackStatus ->
                        if (fallbackStatus == TextToSpeech.SUCCESS) {
                            applyLanguage(currentLanguage)
                            isInitialized = true
                            val callbacksToRun: List<() -> Unit>
                            synchronized(lock) {
                                callbacksToRun = ArrayList(initCallbacks)
                                initCallbacks.clear()
                            }
                            for (cb in callbacksToRun) {
                                try { cb.invoke() } catch (e: Exception) {}
                            }
                        }
                    }, "com.google.android.tts")
                }
            }

            tts = TextToSpeech(appContext, listener)
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

    private fun applyLanguage(language: SoundboxLanguage) {
        val result = tts?.setLanguage(language.locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Voice data for ${language.displayName} missing/unsupported, attempting country fallback")
            val fallbackLocale = Locale(language.code)
            val fallbackResult = tts?.setLanguage(fallbackLocale)
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Fallback locale also unsupported, keeping system default")
                tts?.language = Locale.getDefault()
            }
        } else {
            Log.i(TAG, "TTS language set to ${language.displayName} (${language.locale})")
        }
    }

    fun previewLanguage(context: Context, language: SoundboxLanguage) {
        val appContext = context.applicationContext
        setLanguage(appContext, language)

        wakeUpDevice(appContext)

        mainHandler.post {
            if (!isInitialized || tts == null) {
                init(appContext) {
                    applyLanguage(language)
                    speakText(appContext, language.confirmationGreeting)
                }
            } else {
                applyLanguage(language)
                speakText(appContext, language.confirmationGreeting)
            }
        }
    }

    /**
     * Announce incoming payment with chime and amount-only voice.
     * Uses natural Indian words (e.g. 7500 -> "ఏడు వేల ఐదు వందల రూపాయలు")
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

        val sentence = LanguageManager.getAnnouncementSentence(language, amount)

        // 1. Play crisp two-tone bell chime using native Android hardware ToneGenerator (instant, lockscreen-safe)
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
            mainHandler.postDelayed({
                try {
                    toneGen.release()
                } catch (e: Exception) {}
            }, 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error: ${e.message}")
        }

        // 2. Schedule speech immediately after chime (260ms delay) - Fully reliable on Android 14 and 15
        mainHandler.postDelayed({
            if (!isInitialized || tts == null) {
                init(appContext) {
                    applyLanguage(language)
                    speakText(appContext, sentence, onComplete)
                }
            } else {
                applyLanguage(language)
                speakText(appContext, sentence, onComplete)
            }
        }, 260L)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = "1.0"
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }
    }

    private fun requestAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
