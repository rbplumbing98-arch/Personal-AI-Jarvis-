package com.jarvis.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider

/** Google Cast sender configuration using Google's default media receiver. */
class JarvisCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: android.content.Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
    override fun getAdditionalSessionProviders(context: android.content.Context): List<com.google.android.gms.cast.framework.SessionProvider> =
        emptyList()
}

/** Lightweight CastPlay screen. Casts user-supplied, directly playable media URLs to a selected Cast device. */
class CastPlayActivity : Activity() {
    private var status: TextView? = null
    private var cast: CastContext? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val gold = Color.rgb(229, 177, 76)
    private val pale = Color.rgb(245, 226, 190)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(22), dp(24), dp(22), dp(20))
            setBackgroundColor(Color.rgb(5, 6, 7))
        }
        root.addView(TextView(this).apply {
            text = "CASTPLAY"
            textSize = 23f; letterSpacing = .18f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(pale)
        })
        root.addView(TextView(this).apply {
            text = "WIRELESS MEDIA • GOOGLE CAST"
            textSize = 10f; letterSpacing = .14f; setTextColor(gold)
            setPadding(0, dp(5), 0, dp(20))
        })
        val route = MediaRouteButton(this)
        root.addView(route, LinearLayout.LayoutParams(dp(64), dp(56)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val url = EditText(this).apply {
            hint = "Paste a direct video or audio URL"
            setHintTextColor(Color.rgb(135, 124, 103)); setTextColor(pale)
            textSize = 14f; setSingleLine(true)
            background = GradientDrawable().apply {
                setColor(Color.rgb(19, 17, 13)); setStroke(dp(1), Color.rgb(112, 83, 38)); cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(url, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(18) })
        val play = actionButton("CAST / PLAY")
        root.addView(play, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(12) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; }
        val pause = actionButton("PAUSE")
        val resume = actionButton("RESUME")
        val stop = actionButton("STOP")
        listOf(pause, resume, stop).forEach { controls.addView(it, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), dp(12), dp(3), 0) }) }
        root.addView(controls)
        status = TextView(this).apply {
            text = "Choose the Cast icon to find a TV or Chromecast."
            textSize = 12f; setTextColor(pale); setPadding(0, dp(20), 0, 0)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Supports direct media URLs accepted by the selected receiver. DRM-protected streaming apps may require their own Cast support."
            textSize = 11f; setTextColor(Color.rgb(153, 145, 128)); setPadding(0, dp(14), 0, 0)
        })
        setContentView(root)

        try {
            cast = CastContext.getSharedInstance(this)
            com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(this@CastPlayActivity, route)
        } catch (e: Exception) {
            status?.text = "Google Cast is unavailable on this device: ${e.message ?: "Google Play services may need updating"}"
        }
        play.setOnClickListener {
            val mediaUrl = url.text.toString().trim()
            if (mediaUrl.isBlank() || !(mediaUrl.startsWith("https://") || mediaUrl.startsWith("http://"))) {
                url.error = "Enter a valid direct media URL"
                return@setOnClickListener
            }
            try {
                val client = cast?.sessionManager?.currentCastSession?.remoteMediaClient
                if (client == null) {
                    status?.text = "Connect to a Cast device first using the Cast icon."
                    return@setOnClickListener
                }
                val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
                    putString(MediaMetadata.KEY_TITLE, "J.A.R.V.I.S. CastPlay")
                }
                val mime = when {
                    mediaUrl.substringBefore("?").endsWith(".mp4", true) -> "video/mp4"
                    mediaUrl.substringBefore("?").endsWith(".m3u8", true) -> "application/x-mpegURL"
                    mediaUrl.substringBefore("?").endsWith(".mp3", true) -> "audio/mpeg"
                    mediaUrl.substringBefore("?").endsWith(".m4a", true) -> "audio/mp4"
                    else -> "application/octet-stream"
                }
                val info = MediaInfo.Builder(mediaUrl).setContentType(mime)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setMetadata(metadata).build()
                client.load(info, MediaLoadOptions.Builder().setAutoplay(true).build())
                status?.text = "Sending media to your Cast device…"
            } catch (e: Exception) {
                status?.text = "Could not start casting: ${e.message ?: "unknown error"}"
            }
        }
        pause.setOnClickListener { withClient { it.pause(); status?.text = "Playback paused." } }
        resume.setOnClickListener { withClient { it.play(); status?.text = "Playback resumed." } }
        stop.setOnClickListener { withClient { it.stop(); status?.text = "Playback stopped." } }
    }

    private fun actionButton(label: String) = Button(this).apply {
        text = label; textSize = 10f; isAllCaps = false; setTextColor(pale)
        background = GradientDrawable().apply { setColor(Color.rgb(27, 22, 14)); setStroke(dp(1), gold); cornerRadius = dp(10).toFloat() }
    }
    private fun withClient(action: (RemoteMediaClient) -> Unit) {
        val client = cast?.sessionManager?.currentCastSession?.remoteMediaClient
        if (client == null) status?.text = "No Cast device is connected." else action(client)
    }
}
