package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInput(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onFail: (String) -> Unit
) {
    private var rec: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onFail("Voice recognition is not available on this device, sir.")
            return
        }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                onListening(false)
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) onFail("I did not catch that, sir.") else onText(t)
            }
            override fun onError(error: Int) {
                onListening(false)
                onFail(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not catch that, sir."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone permission, sir."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            "Speech recognition needs a connection, sir, or the offline English pack from Google speech settings."
                        SpeechRecognizer.ERROR_AUDIO -> "I could not record from the microphone, sir. Close any app using it and try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy, sir. Please try again."
                        SpeechRecognizer.ERROR_CLIENT -> "The speech service stopped, sir. Please tap the microphone again."
                        12, 13 -> "Offline speech is not installed. Download the English language pack in your Google speech settings, sir."
                        else -> "Voice input failed (code $error), sir."
                    }
                )
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !isOnline())
        r.startListening(i)
    }

    private fun isOnline(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun stop() {
        rec?.destroy()
        rec = null
    }
}
