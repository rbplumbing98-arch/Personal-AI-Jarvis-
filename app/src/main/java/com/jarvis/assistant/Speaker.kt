package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/** ElevenLabs speech with on-device phrase caching and visible fallback errors. */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var busy = false
    private var generation = 0
    private val queue = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    var onState: ((Boolean) -> Unit)? = null
    var onInitFailed: (() -> Unit)? = null
    var onVoiceError: ((String) -> Unit)? = null

    fun init(context: Context) {
        app = context.applicationContext
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                val result = engine.setLanguage(Locale.UK)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) engine.setLanguage(Locale.getDefault())
                engine.setPitch(.92f)
                engine.setSpeechRate(.88f)
                engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onState?.invoke(true) }
                    override fun onDone(utteranceId: String?) { onState?.invoke(false) }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) { onState?.invoke(false) }
                })
                ready = true
                pending?.let { pending = null; speakLocal(it, true) }
            } else onInitFailed?.invoke()
        }
    }

    private fun speakLocal(text: String, flush: Boolean) {
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "jarvis-" + System.nanoTime())
    }
    private fun sayLocal(text: String, flush: Boolean) {
        if (!ready) { pending = text; return }
        speakLocal(text, flush)
    }

    fun say(text: String, flush: Boolean = true) {
        main.post {
            val ctx = app
            if (ctx != null && Store(ctx).elevenKey.isNotBlank()) {
                if (flush) { queue.clear(); releasePlayer() }
                queue.addLast(text)
                if (!busy) next()
            } else sayLocal(text, flush)
        }
    }

    fun testVoice() = say("Good evening. All systems are online. How may I assist you today?")
    fun stop() { main.post { queue.clear(); releasePlayer() } }

    private fun releasePlayer() {
        generation++
        busy = false
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        tts?.stop()
        onState?.invoke(false)
    }

    private fun next() {
        val ctx = app ?: return
        if (queue.isEmpty()) return
        val phrase = queue.removeFirst()
        busy = true
        val current = generation
        val store = Store(ctx)
        io.execute {
            var failure: String? = null
            val audio = try { fetch(ctx, store.elevenKey, store.voiceId, phrase) }
            catch (e: Exception) { failure = e.message ?: "Network or audio error"; null }
            main.post {
                if (current != generation) return@post
                if (audio == null) {
                    busy = false
                    onVoiceError?.invoke(failure ?: "ElevenLabs did not return audio. Check API key, voice ID and account quota.")
                    sayLocal(phrase, false)
                    next()
                } else play(audio, current)
            }
        }
    }

    private fun play(file: File, current: Int) {
        try {
            val media = MediaPlayer()
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            media.setDataSource(file.absolutePath)
            media.setOnPreparedListener { it.start(); onState?.invoke(true) }
            media.setOnCompletionListener {
                it.release()
                if (current == generation) { player = null; busy = false; onState?.invoke(false); next() }
            }
            media.setOnErrorListener { m, _, _ ->
                m.release(); file.delete()
                if (current == generation) {
                    player = null; busy = false
                    onVoiceError?.invoke("Downloaded voice audio could not be played. Using the phone voice instead.")
                    next()
                }
                true
            }
            player = media
            media.prepareAsync()
        } catch (e: Exception) {
            busy = false
            onVoiceError?.invoke("Audio playback failed: " + (e.message ?: "unknown error"))
            next()
        }
    }

    private fun fetch(ctx: Context, key: String, voice: String, raw: String): File? {
        if (key.isBlank() || voice.isBlank()) return null
        val text = raw.take(600)
        val model = "eleven_multilingual_v2"
        val settingsKey = "stability=.42;similarity=.78;style=.18;speaker=true"
        val dir = File(ctx.cacheDir, "tts").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest("$model|$voice|$settingsKey|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        val file = File(dir, "$hash.mp3")
        if (file.exists() && file.length() > 0) return file
        dir.listFiles()?.takeIf { it.size > 200 }?.sortedBy { it.lastModified() }?.take(60)?.forEach { it.delete() }

        val connection = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=mp3_44100_128")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("xi-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "audio/mpeg")
            val voiceSettings = JSONObject().put("stability", .42).put("similarity_boost", .78)
                .put("style", .18).put("use_speaker_boost", true)
            val body = JSONObject().put("text", text).put("model_id", model).put("voice_settings", voiceSettings)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                val detail = try { connection.errorStream?.bufferedReader()?.readText()?.take(200) } catch (_: Exception) { null }
                main.post { onVoiceError?.invoke("ElevenLabs HTTP ${connection.responseCode}. " + (detail ?: "Check key permissions and quota.")) }
                return null
            }
            val temp = File(dir, "$hash.tmp")
            connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
            if (temp.length() == 0L) { temp.delete(); return null }
            if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
            return file
        } finally { connection.disconnect() }
    }
}
