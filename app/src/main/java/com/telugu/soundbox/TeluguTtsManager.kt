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

        // 1. Play crisp chime using native Android hardware ToneGenerator (instant, lockscreen-safe)
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
