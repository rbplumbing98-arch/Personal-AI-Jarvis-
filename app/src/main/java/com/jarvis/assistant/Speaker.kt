package com.jarvis.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
    @Volatile private var audioTrack: AudioTrack? = null
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

    fun sayIncremental(text: String) {
        if (text.isBlank()) return
        main.post {
            val ctx = app
            if (ctx != null && Store(ctx).elevenKey.isNotBlank() && Store(ctx).voiceId.isNotBlank()) {
                queue.addLast(text)
                if (!busy) next()
            } else sayLocal(text, false)
        }
    }

    fun testVoice() = say("Good evening. All systems are online. How may I assist you today?")
    fun stop() { main.post { queue.clear(); releasePlayer() } }

    private fun releasePlayer() {
        generation++
        busy = false
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
        audioTrack?.let { try { it.pause(); it.flush(); it.stop() } catch (_: Exception) {}; try { it.release() } catch (_: Exception) {} }
        audioTrack = null
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
            try { streamPcm(ctx, store.elevenKey, store.voiceId, phrase, current) }
            catch (e: Exception) { failure = e.message ?: "ElevenLabs streaming error" }
            main.post {
                if (current != generation) return@post
                audioTrack = null
                busy = false
                if (failure != null) {
                    onVoiceError?.invoke("ElevenLabs streaming failed. Using the phone voice instead. " + failure)
                    sayLocal(phrase, false)
                } else onState?.invoke(false)
                next()
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


    // Request raw PCM and feed AudioTrack as bytes arrive; playback begins before the full audio response downloads.
    private fun streamPcm(ctx: Context, key: String, voice: String, raw: String, current: Int) {
        if (key.isBlank() || voice.isBlank()) throw IllegalStateException("ElevenLabs API key is missing.")
        if (voice.isBlank()) throw IllegalStateException("ElevenLabs voice ID is missing.")
        val text = raw.take(600)
        val connection = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice/stream?output_format=pcm_16000")
            .openConnection() as HttpURLConnection
        var track: AudioTrack? = null
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("xi-api-key", key)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/octet-stream")
            val settings = JSONObject().put("stability", .42).put("similarity_boost", .78)
                .put("style", .18).put("use_speaker_boost", true)
            val body = JSONObject().put("text", text).put("model_id", "eleven_flash_v2_5").put("voice_settings", settings)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = try { connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(180) } catch (_: Exception) { null }
                throw IllegalStateException("ElevenLabs HTTP $code. " + (detail ?: "Check API key, voice ID and quota."))
            }
            val minBuffer = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) throw IllegalStateException("This device cannot initialise PCM audio playback.")
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minBuffer, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track
            val buffer = ByteArray(4096)
            val initial = java.io.ByteArrayOutputStream()
            var started = false
            connection.inputStream.use { input ->
                while (current == generation) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (!started) {
                        initial.write(buffer, 0, count)
                        if (initial.size() < 8192) continue
                        track.play()
                        onState?.invoke(true)
                        val first = initial.toByteArray()
                        var offset = 0
                        while (offset < first.size && current == generation) {
                            offset += track.write(first, offset, first.size - offset, AudioTrack.WRITE_BLOCKING)
                        }
                        initial.reset()
                        started = true
                    } else {
                        var offset = 0
                        while (offset < count && current == generation) {
                            val written = track.write(buffer, offset, count - offset, AudioTrack.WRITE_BLOCKING)
                            if (written < 0) throw IllegalStateException("Audio playback stopped unexpectedly.")
                            offset += written
                        }
                    }
                }
            }
            if (current != generation) return
            if (!started && initial.size() > 0) {
                val first = initial.toByteArray()
                track.play()
                onState?.invoke(true)
                var offset = 0
                while (offset < first.size && current == generation) {
                    val written = track.write(first, offset, first.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) break
                    offset += written
                }
            }
            if (current == generation && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val deadline = System.currentTimeMillis() + 15000
                while (current == generation && track.playbackHeadPosition.toLong() * 2 < track.getPlaybackHeadPosition().toLong() * 2 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(20)
                }
                Thread.sleep(120)
            }
        } finally {
            connection.disconnect()
            try { track?.pause(); track?.flush(); track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            if (audioTrack === track) audioTrack = null
        }
    }

    private fun fetch(ctx: Context, key: String, voice: String, raw: String): File? {
        if (key.isBlank() || voice.isBlank()) return null
        val text = raw.take(600)
        val model = "eleven_flash_v2_5"
        val settingsKey = "stability=.42;similarity=.78;style=.18;speaker=true"
        val dir = File(ctx.cacheDir, "tts").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest("$model|$voice|$settingsKey|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(32)
        val file = File(dir, "$hash.mp3")
        if (file.exists() && file.length() > 0) return file
        dir.listFiles()?.takeIf { it.size > 200 }?.sortedBy { it.lastModified() }?.take(60)?.forEach { it.delete() }

        val connection = URL("https://api.elevenlabs.io/v1/text-to-speech/$voice/stream?output_format=mp3_44100_128")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7000
            connection.readTimeout = 20000
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
