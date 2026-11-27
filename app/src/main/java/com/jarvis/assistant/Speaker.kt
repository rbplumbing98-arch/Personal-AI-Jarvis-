package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/**
 * J.A.R.V.I.S. voice router.
 * AUTO uses ElevenLabs online when configured and connected, otherwise an installed
 * on-device English voice. Explicit test methods never silently switch engines.
 */
object Speaker {
    private enum class Engine { AUTO, ONLINE, OFFLINE }
    private data class Request(val text: String, val engine: Engine)

    private var tts: TextToSpeech? = null
    private var ready = false
    private var offlineVoiceAvailable = false
    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var busy = false
    private var generation = 0
    private val queue = ArrayDeque<Request>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    var onState: ((Boolean) -> Unit)? = null
    var onInitFailed: (() -> Unit)? = null
    var onVoiceError: ((String) -> Unit)? = null
    var onVoiceStatus: ((String) -> Unit)? = null

    fun init(context: Context) {
        app = context.applicationContext
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val voices = engine.voices.orEmpty()
                val offlineEnglish = voices.filter {
                    it.locale.language.equals("en", true) && !it.isNetworkConnectionRequired
                }
                val preferred = offlineEnglish.sortedWith(
                    compareByDescending<Voice> { it.name.contains("male", ignoreCase = true) }
                        .thenByDescending { it.locale.country.equals("GB", true) }
                        .thenByDescending { it.quality }
                ).firstOrNull()
                if (preferred != null) {
                    engine.voice = preferred
                    offlineVoiceAvailable = true
                    onVoiceStatus?.invoke("OFFLINE VOICE READY • ${preferred.locale.toLanguageTag()}")
                } else {
                    val languageResult = engine.setLanguage(Locale.UK)
                    offlineVoiceAvailable = false
                    onVoiceStatus?.invoke(
                        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED)
                            "OFFLINE VOICE NOT INSTALLED • DOWNLOAD ENGLISH (UK)"
                        else "OFFLINE VOICE NEEDS AN INSTALLED LOCAL VOICE"
                    )
                }
                engine.setPitch(.94f)
                engine.setSpeechRate(.92f)
                engine.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onState?.invoke(true) }
                    override fun onDone(utteranceId: String?) {
                        main.post {
                            onState?.invoke(false)
                            if (busy) { busy = false; next() }
                        }
                    }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        main.post {
                            onState?.invoke(false)
                            if (busy) { busy = false; onVoiceError?.invoke("The installed offline voice could not speak this text."); next() }
                        }
                    }
                })
                ready = true
                if (queue.isNotEmpty() && !busy) next()
            } else {
                offlineVoiceAvailable = false
                onInitFailed?.invoke()
                onVoiceStatus?.invoke("ANDROID SPEECH ENGINE UNAVAILABLE")
            }
        }
    }

    fun say(text: String, flush: Boolean = true) = enqueue(text, Engine.AUTO, flush)

    fun testOnlineVoice() = enqueue(
        "Online voice test. J.A.R.V.I.S. voice system is connected. How may I assist you?",
        Engine.ONLINE, true
    )

    fun testOfflineVoice() = enqueue(
        "Offline voice test. J.A.R.V.I.S. is running locally on this device.",
        Engine.OFFLINE, true
    )

    fun testVoice() = say("Good evening, sir. All systems are online. How may I assist you today?")

    private fun enqueue(text: String, engine: Engine, flush: Boolean) {
        if (text.isBlank()) return
        main.post {
            if (flush) {
                queue.clear()
                releasePlayback()
            }
            queue.addLast(Request(text.take(600), engine))
            if (!busy) next()
        }
    }

    fun stop() {
        main.post {
            queue.clear()
            releasePlayback()
        }
    }

    private fun releasePlayback() {
        generation++
        busy = false
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
        tts?.stop()
        onState?.invoke(false)
    }

    private fun hasInternet(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { false }
    }

    private fun next() {
        val context = app ?: return
        if (busy || queue.isEmpty()) return
        val request = queue.removeFirst()
        val store = Store(context)
        val selected = when (request.engine) {
            Engine.AUTO -> if (store.elevenKey.isNotBlank() && store.voiceId.isNotBlank() && hasInternet(context))
                Engine.ONLINE else Engine.OFFLINE
            else -> request.engine
        }

        if (selected == Engine.OFFLINE) {
            if (!ready) {
                busy = false
                onVoiceStatus?.invoke("STARTING LOCAL VOICE ENGINE…")
                queue.addFirst(request)
                return
            }
            if (!offlineVoiceAvailable) {
                onVoiceStatus?.invoke("OFFLINE VOICE NOT READY")
                onVoiceError?.invoke("No downloadable offline English voice is installed. Open Android Text-to-Speech settings and install an English (UK) voice.")
                next()
                return
            }
            onVoiceStatus?.invoke("OFFLINE • ON-DEVICE VOICE")
            busy = true
            onState?.invoke(true)
            tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-local-" + System.nanoTime())
            return
        }

        if (store.elevenKey.isBlank() || store.voiceId.isBlank()) {
            onVoiceStatus?.invoke("ELEVENLABS NOT CONFIGURED")
            onVoiceError?.invoke("Add your ElevenLabs API key and voice ID in Settings before testing the online voice.")
            next()
            return
        }
        if (!hasInternet(context)) {
            onVoiceStatus?.invoke("NO INTERNET • ONLINE TEST NOT RUN")
            onVoiceError?.invoke("The online voice test needs an internet connection. The offline voice is a separate test.")
            next()
            return
        }

        busy = true
        onVoiceStatus?.invoke("ONLINE • ELEVENLABS")
        val current = generation
        io.execute {
            var failure: String? = null
            val audio = try { fetch(context, store.elevenKey, store.voiceId, request.text) }
            catch (e: Exception) { failure = e.message ?: "Network or audio error"; null }
            main.post {
                if (current != generation) return@post
                if (audio == null) {
                    busy = false
                    onVoiceStatus?.invoke(if (request.engine == Engine.AUTO) "OFFLINE FALLBACK • ELEVENLABS UNAVAILABLE" else "ONLINE VOICE TEST FAILED")
                    onVoiceError?.invoke(failure ?: "ElevenLabs returned no audio. Check the key, voice ID, permissions and account quota.")
                    if (request.engine == Engine.AUTO && offlineVoiceAvailable) {
                        busy = true
                        tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-fallback-" + System.nanoTime())
                    } else next()
                } else play(audio, current, request)
            }
        }
    }

    private fun play(file: File, current: Int, request: Request) {
        try {
            val media = MediaPlayer()
            media.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            media.setDataSource(file.absolutePath)
            media.setOnPreparedListener { it.start(); onState?.invoke(true) }
            media.setOnCompletionListener {
                it.release()
                if (current == generation) {
                    player = null
                    busy = false
                    onState?.invoke(false)
                    onVoiceStatus?.invoke("ONLINE • ELEVENLABS READY")
                    next()
                }
            }
            media.setOnErrorListener { m, _, _ ->
                m.release()
                if (current == generation) {
                    player = null
                    busy = false
                    onVoiceError?.invoke("ElevenLabs audio was received but Android could not play it.")
                    if (request.engine == Engine.AUTO && offlineVoiceAvailable) {
                        onVoiceStatus?.invoke("OFFLINE FALLBACK • AUDIO PLAYBACK ERROR")
                        busy = true
                        tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-fallback-" + System.nanoTime())
                    } else {
                        onVoiceStatus?.invoke("ONLINE AUDIO PLAYBACK FAILED")
                        next()
                    }
                }
                true
            }
            player = media
            media.prepareAsync()
        } catch (e: Exception) {
            busy = false
            onVoiceError?.invoke("Audio playback failed: ${e.message ?: "unknown error"}")
            if (request.engine == Engine.AUTO && offlineVoiceAvailable) {
                busy = true
                tts?.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-fallback-" + System.nanoTime())
            } else next()
        }
    }

    private fun fetch(context: Context, key: String, voice: String, text: String): File {
        val model = "eleven_flash_v2_5"
        val settingsKey = "stability=.48;similarity=.82;style=.12;speaker=true"
        val directory = File(context.cacheDir, "tts").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$model|$voice|$settingsKey|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        val file = File(directory, "$hash.mp3")
        if (file.exists() && file.length() > 0) return file
        directory.listFiles()?.takeIf { it.size > 200 }?.sortedBy { it.lastModified() }
            ?.take(60)?.forEach { it.delete() }

        val connection = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice/stream?output_format=mp3_44100_128")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("xi-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "audio/mpeg")
            val settings = JSONObject().put("stability", .48).put("similarity_boost", .82)
                .put("style", .12).put("use_speaker_boost", true)
            val body = JSONObject().put("text", text).put("model_id", model).put("voice_settings", settings)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val response = connection.responseCode
            if (response !in 200..299) {
                val detail = try { connection.errorStream?.bufferedReader()?.readText()?.take(240) }
                    catch (_: Exception) { null }
                throw IOException("ElevenLabs HTTP $response. ${detail ?: "Check API key, voice ID and account quota."}")
            }
            val temp = File(directory, "$hash.tmp")
            try {
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                if (temp.length() == 0L) throw IOException("ElevenLabs returned an empty audio stream.")
                if (!temp.renameTo(file)) {
                    temp.copyTo(file, overwrite = true)
                    temp.delete()
                }
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
            return file
        } finally {
            connection.disconnect()
        }
    }
}
