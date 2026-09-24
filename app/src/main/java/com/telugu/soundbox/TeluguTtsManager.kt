package com.telugu.soundbox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
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
    private val pendingQueue = mutableListOf<String>()

    fun init(context: Context, onReady: (() -> Unit)? = null) {
        if (isInitialized && tts != null) {
            onReady?.invoke()
            return
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val teluguLocale = Locale("te", "IN")
                val result = tts?.setLanguage(teluguLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Telugu voice data not installed, falling back to default locale")
                    tts?.language = Locale.getDefault()
                } else {
                    Log.i(TAG, "Telugu TTS engine initialized successfully!")
                }

                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)

                isInitialized = true
                onReady?.invoke()

                // Process any messages that arrived during startup
                synchronized(pendingQueue) {
                    for (text in pendingQueue) {
                        speakText(text)
                    }
                    pendingQueue.clear()
                }
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech: status=$status")
            }
        }
    }

    fun announcePayment(context: Context, amount: String, payerName: String?) {
        // Construct the Telugu sentence
        val teluguSentence = if (!payerName.isNullOrBlank()) {
            "$payerName నుండి $amount రూపాయలు మీ ఖాతాలో జమ అయ్యాయి."
        } else {
            "మీ ఖాతాలో $amount రూపాయలు జమ అయ్యాయి."
        }

        // Play chime tone, then speak
        CoroutineScope(Dispatchers.IO).launch {
            playPaymentChime()
            CoroutineScope(Dispatchers.Main).launch {
                if (!isInitialized) {
                    init(context) {
                        speakText(teluguSentence)
                    }
                } else {
                    speakText(teluguSentence)
                }
            }
        }
    }

    fun speakText(text: String) {
        if (tts == null || !isInitialized) {
            synchronized(pendingQueue) {
                pendingQueue.add(text)
            }
            return
        }

        val utteranceId = "SoundboxAlert_${System.currentTimeMillis()}"
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
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, map)
        }
    }

    /**
     * Synthesize standard dual-frequency payment chime (880Hz + 1320Hz)
     */
    private fun playPaymentChime() {
        try {
            val sampleRate = 44100
            val durationMs = 300
            val numSamples = (durationMs * sampleRate) / 1000
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Harmonic dual tone for professional POS chime
                val sample = 0.5 * sin(2.0 * Math.PI * 880.0 * t) + 0.5 * sin(2.0 * Math.PI * 1320.0 * t)
                // Linear decay envelope
                val envelope = 1.0 - (i.toDouble() / numSamples)
                buffer[i] = (sample * envelope * Short.MAX_VALUE).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
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
