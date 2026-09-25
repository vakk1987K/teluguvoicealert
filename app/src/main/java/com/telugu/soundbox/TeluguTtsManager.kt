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

object TeluguTtsManager {
    private const val TAG = "TeluguTtsManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
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
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val teluguLocale = Locale("te", "IN")
                val result = tts?.setLanguage(teluguLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Telugu voice data not installed, falling back to default locale")
                    tts?.language = Locale.getDefault()
                } else {
                    Log.i(TAG, "Telugu TTS engine initialized successfully!")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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

    fun announcePayment(
        context: Context,
        amount: String,
        payerName: String?,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext

        // Wake the CPU and screen so sound plays instantly on lock screen
        wakeUpDevice(appContext)

        val teluguSentence = if (!payerName.isNullOrBlank()) {
            "$payerName నుండి $amount రూపాయలు మీ ఖాతాలో జమ అయ్యాయి."
        } else {
            "మీ ఖాతాలో $amount రూపాయలు జమ అయ్యాయి."
        }

        CoroutineScope(Dispatchers.IO).launch {
            playPaymentChime(appContext)
            CoroutineScope(Dispatchers.Main).launch {
                if (!isInitialized || tts == null) {
                    init(appContext) {
                        speakText(appContext, teluguSentence, onComplete)
                    }
                } else {
                    speakText(appContext, teluguSentence, onComplete)
                }
            }
        }
    }

    fun speakText(context: Context, text: String, onComplete: (() -> Unit)? = null) {
        if (tts == null || !isInitialized) {
            synchronized(pendingQueue) {
                pendingQueue.add(Pair(text, onComplete))
            }
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
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            val map = HashMap<String, String>()
            map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            map[TextToSpeech.Engine.KEY_PARAM_STREAM] = AudioManager.STREAM_ALARM.toString()
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
                    acquire(15000L)
                }
            }

            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "TeluguSoundbox::ScreenWake"
            )
            screenWakeLock.acquire(4000L)
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
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = focusRequest
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
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
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
            Thread.sleep(durationMs.toLong() + 50)
            audioTrack.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack chime playback error: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
