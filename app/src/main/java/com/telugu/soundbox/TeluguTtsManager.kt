package com.telugu.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioFocusRequest
import android.media.AudioManager
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
     * Crisp, clear, amount-only announcement sentence.
     * Does NOT read person names, phone numbers, or sender details.
     */
    fun getAnnouncementSentence(
        language: SoundboxLanguage,
        amount: String
    ): String {
        return when (language) {
            SoundboxLanguage.TELUGU -> {
                "$amount రూపాయలు మీ ఖాతాలో జమ అయ్యాయి."
            }
            SoundboxLanguage.HINDI -> {
                "$amount रुपये आपके खाते में प्राप्त हुए हैं।"
            }
            SoundboxLanguage.MARATHI -> {
                "$amount रुपये आपल्या खात्यात जमा झाले आहेत."
            }
            SoundboxLanguage.GUJARATI -> {
                "$amount રૂપિયા તમારા ખાતામાં જમા થયા છે."
            }
            SoundboxLanguage.TAMIL -> {
                "$amount ரூபாய் உங்கள் கணக்கில் வரவு வைக்கப்பட்டது."
            }
            SoundboxLanguage.ENGLISH -> {
                "$amount rupees credited to your account."
            }
        }
    }
}

object TeluguTtsManager {
    private const val TAG = "SoundboxTtsManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: SoundboxLanguage = SoundboxLanguage.TELUGU
    private val pendingQueue = mutableListOf<Pair<String, (() -> Unit)?>>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeWakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: Any? = null

    fun init(context: Context, onReady: (() -> Unit)? = null) {
        if (isInitialized && tts != null) {
            onReady?.invoke()
            return
        }

        val appContext = context.applicationContext
        currentLanguage = LanguageManager.getLanguage(appContext)

        mainHandler.post {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    applyLanguage(currentLanguage)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                        tts?.setAudioAttributes(audioAttributes)
                    }

                    tts?.setSpeechRate(0.95f)
                    tts?.setPitch(1.0f)

                    isInitialized = true
                    onReady?.invoke()

                    synchronized(pendingQueue) {
                        for ((text, callback) in pendingQueue) {
                            speakText(appContext, text, callback)
                        }
                        pendingQueue.clear()
                    }
                } else {
                    Log.e(TAG, "Failed to initialize TextToSpeech: status=$status")
                }
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
        ensureAudibleVolume(appContext)

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
        ensureAudibleVolume(appContext)

        val sentence = LanguageManager.getAnnouncementSentence(language, amount)

        CoroutineScope(Dispatchers.IO).launch {
            playPaymentChime(appContext)
            mainHandler.post {
                if (!isInitialized || tts == null) {
                    init(appContext) {
                        applyLanguage(language)
                        speakText(appContext, sentence, onComplete)
                    }
                } else {
                    applyLanguage(language)
                    speakText(appContext, sentence, onComplete)
                }
            }
        }
    }

    private fun ensureAudibleVolume(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (currentVol < (maxVol * 0.6)) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.85).toInt(), 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust audio volume: ${e.message}")
        }
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
        mainHandler.postDelayed(timeoutRunnable, 12000L)

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
                Log.e(TAG, "Error speaking utterance: $id")
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post {
                    cleanupSpeech(appContext)
                    onComplete?.invoke()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            val map = HashMap<String, String>()
            map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            map[TextToSpeech.Engine.KEY_PARAM_STREAM] = AudioManager.STREAM_MUSIC.toString()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, map)
        }
    }

    private fun wakeUpDevice(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            if (activeWakeLock == null || activeWakeLock?.isHeld == false) {
                activeWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "TeluguSoundbox::AudioPlaybackLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(20000L)
                }
            }

            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "TeluguSoundbox::ScreenWake"
            )
            screenWakeLock.acquire(6000L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock: ${e.message}")
        }
    }

    private fun requestAudioFocus(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
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
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus error: ${e.message}")
        }
    }

    private fun cleanupSpeech(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}

        try {
            if (activeWakeLock?.isHeld == true) {
                activeWakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun playPaymentChime(context: Context) {
        try {
            val sampleRate = 44100
            val durationMs = 300
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val sample = 0.5 * sin(2.0 * Math.PI * 880.0 * t) + 0.5 * sin(2.0 * Math.PI * 1320.0 * t)
                val envelope = 1.0 - (i.toDouble() / numSamples)
                buffer[i] = (sample * envelope * Short.MAX_VALUE).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            Thread.sleep(durationMs.toLong() + 50L)
            audioTrack.release()
        } catch (e: Exception) {
            Log.w(TAG, "Chime generation failed: ${e.message}")
        }
    }
}
