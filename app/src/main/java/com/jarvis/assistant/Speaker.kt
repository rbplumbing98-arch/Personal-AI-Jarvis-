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

/**
 * Speaks with your ElevenLabs voice when an ElevenLabs API key is set (audio is cached on the phone so
 * repeated phrases also work offline). Falls back to Android's built-in British voice otherwise.
 */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null
    private var app: Context? = null
    private var player: MediaPlayer? = null
    private var busy = false
    private var gen = 0
    private val queue = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    var onState: ((Boolean) -> Unit)? = null
    var onInitFailed: (() -> Unit)? = null

    fun init(context: Context) {
        app = context.applicationContext
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Locale.UK)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale.getDefault())
                }
                t.setPitch(0.85f)
                t.setSpeechRate(0.95f)
                t.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onState?.invoke(true) }
                    override fun onDone(utteranceId: String?) { onState?.invoke(false) }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) { onState?.invoke(false) }
                })
                ready = true
                pending?.let { p -> pending = null; speakLocal(p, true) }
            } else {
                onInitFailed?.invoke()
            }
        }
    }

    private fun speakLocal(text: String, flush: Boolean) {
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "j" + System.nanoTime())
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
            } else {
                sayLocal(text, flush)
            }
        }
    }

    fun stop() {
        main.post { queue.clear(); releasePlayer() }
    }

    private fun releasePlayer() {
        gen++
        busy = false
        player?.let { try { it.stop() } catch (e: Exception) { } ; it.release() }
        player = null
        tts?.stop()
        onState?.invoke(false)
    }

    private fun next() {
        val ctx = app ?: return
        if (queue.isEmpty()) return
        val text = queue.removeFirst()
        busy = true
        val g = gen
        val store = Store(ctx)
        val key = store.elevenKey
        val voice = store.voiceId
        io.execute {
            val f = try { fetch(ctx, key, voice, text) } catch (e: Exception) { null }
            main.post {
                if (g != gen) return@post
                if (f == null) {
                    busy = false
                    sayLocal(text, false)
                    next()
                } else {
                    play(f, g)
                }
            }
        }
    }

    private fun play(file: File, g: Int) {
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener { it.start(); onState?.invoke(true) }
            mp.setOnCompletionListener {
                it.release()
                if (g == gen) { player = null; busy = false; onState?.invoke(false); next() }
            }
            mp.setOnErrorListener { m, _, _ ->
                m.release()
                file.delete()
                if (g == gen) { player = null; busy = false; onState?.invoke(false); next() }
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (e: Exception) {
            busy = false
            next()
        }
    }

    private fun fetch(ctx: Context, key: String, voice: String, raw: String): File? {
        val text = if (raw.length > 600) raw.substring(0, 600) else raw
        val dir = File(ctx.cacheDir, "tts")
        dir.mkdirs()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$voice|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val f = File(dir, "$hash.mp3")
        if (f.exists() && f.length() > 0) return f
        val files = dir.listFiles()
        if (files != null && files.size > 200) files.sortedBy { it.lastModified() }.take(60).forEach { it.delete() }
        val c = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice?output_format=mp3_44100_64")
            .openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.doOutput = true
            c.setRequestProperty("xi-api-key", key)
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("accept", "audio/mpeg")
            val body = JSONObject().put("text", text).put("model_id", "eleven_flash_v2_5")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val tmp = File(dir, "$hash.tmp")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() == 0L) { tmp.delete(); return null }
            tmp.renameTo(f)
            return f
        } finally {
            c.disconnect()
        }
    }
}
