package com.jarvis.assistant

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.provider.MediaStore

/** Controls whichever music app is active (Spotify included) through Android media sessions. */
object MediaControl {
    private fun controller(ctx: Context): MediaController? {
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = msm.getActiveSessions(ComponentName(ctx, JarvisNotificationListener::class.java))
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        } catch (e: SecurityException) {
            null
        }
    }

    fun play(ctx: Context): Boolean { val c = controller(ctx) ?: return false; c.transportControls.play(); return true }
    fun pause(ctx: Context): Boolean { val c = controller(ctx) ?: return false; c.transportControls.pause(); return true }
    fun next(ctx: Context): Boolean { val c = controller(ctx) ?: return false; c.transportControls.skipToNext(); return true }
    fun previous(ctx: Context): Boolean { val c = controller(ctx) ?: return false; c.transportControls.skipToPrevious(); return true }

    fun nowPlaying(ctx: Context): String? {
        val md = controller(ctx)?.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
        return if (artist.isNullOrBlank()) "Now playing $title, sir." else "Now playing $title by $artist, sir."
    }

    fun playFromSearch(ctx: Context, query: String, spotify: Boolean): Boolean {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            if (spotify) setPackage("com.spotify.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(i); true
        } catch (e: Exception) {
            try {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/" + Uri.encode(query)))
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(web); true
            } catch (e2: Exception) { false }
        }
    }
}
