package com.jarvis.assistant

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

/** Makes Jarvis appear in the Android Auto media list with a few voice-driven actions. */
class JarvisAutoService : MediaBrowserServiceCompat() {
    private lateinit var session: MediaSessionCompat
    private lateinit var brain: Brain
    private var voice: VoiceInput? = null
    private val handler = Handler(Looper.getMainLooper())

    private val entries = listOf(
        "ask" to "Ask Jarvis",
        "msgs" to "Read my messages",
        "now" to "What's playing",
        "next" to "Next song",
        "pause" to "Pause music",
        "resume" to "Resume music"
    )

    override fun onCreate() {
        super.onCreate()
        Speaker.init(this)
        brain = Brain(this, Store(this))
        voice = VoiceInput(
            this,
            { text -> brain.handle(text) { Speaker.say(it) } },
            { },
            { msg -> Speaker.say(msg) }
        )
        session = MediaSessionCompat(this, "Jarvis")
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
                .build()
        )
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) { act(mediaId) }
            override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                if (!query.isNullOrBlank()) brain.handle(query) { Speaker.say(it) }
            }
        })
        session.isActive = true
        sessionToken = session.sessionToken
    }

    private fun act(id: String?) {
        val say = { text: String -> brain.handle(text) { Speaker.say(it) } }
        when (id) {
            "ask" -> { Speaker.say("Yes?"); handler.postDelayed({ voice?.start() }, 1000) }
            "msgs" -> say("read my messages")
            "now" -> say("what's playing")
            "next" -> say("next")
            "pause" -> say("pause")
            "resume" -> say("resume")
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? =
        BrowserRoot("jarvis_root", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        val items = entries.map { (id, label) ->
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().setMediaId(id).setTitle(label).build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )
        }.toMutableList()
        result.sendResult(items)
    }

    override fun onDestroy() {
        voice?.stop()
        session.release()
        super.onDestroy()
    }
}
