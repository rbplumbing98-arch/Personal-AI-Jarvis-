package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var brain: Brain
    private lateinit var hud: HudView
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var you: TextView
    private lateinit var voiceStatus: TextView
    private var voice: VoiceInput? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var clockView: TextView? = null
    private var weatherView: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() {
            clockView?.text = SimpleDateFormat("EEEE, d MMM yyyy\\nh:mm:ss a", Locale.UK).format(Date())
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        brain = Brain(this, store)
        Speaker.onInitFailed = {
            runOnUiThread {
                if (::caption.isInitialized) caption.text =
                    "My voice engine did not start, sir. Install or enable Google Speech Services (text-to-speech) in Android settings, then reopen me."
            }
        }
        Speaker.onVoiceError = { message ->
            runOnUiThread { if (::voiceStatus.isInitialized) voiceStatus.text = "VOICE FALLBACK • $message" }
        }
        Speaker.init(this)

        val blue = Color.parseColor("#D99A24")
        val light = Color.parseColor("#F5D58A")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#04080C"))
            setPadding(px(16), px(12), px(16), px(12))
            fitsSystemWindows = true
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = TextView(this).apply { text = "J.A.R.V.I.S.  •  ONLINE"; setTextColor(blue); textSize = 11f; letterSpacing = 0.15f }
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Button(this).apply {
            text = "UPDATE"; textSize = 11f; setTextColor(light); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { checkForUpdates() }
        })
        top.addView(Button(this).apply {
            text = "SETTINGS"; textSize = 11f; setTextColor(light); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { openSettings() }
        })
        root.addView(top)

        hud = HudView(this)
        val hubLabel = TextView(this).apply {
            text = "QUANTUM CORE  /  3D HOLOGRAPHIC INTERFACE"
            setTextColor(Color.parseColor("#69DDF4")); textSize = 9f; letterSpacing = 0.18f
            gravity = Gravity.CENTER; setPadding(0, px(10), 0, px(2))
        }
        root.addView(hubLabel)
        root.addView(hud, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(270)))

        val indicators = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, px(6), 0, px(10))
        }
        fun indicator(label: String, value: String, color: Int): TextView = TextView(this).apply {
            text = label + "\n" + value; textSize = 9f; gravity = Gravity.CENTER
            setTextColor(color); setPadding(px(5), px(7), px(5), px(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#101D27")); setStroke(px(1), Color.parseColor("#244252"))
                cornerRadius = px(8).toFloat()
            }
        }
        indicators.addView(indicator("VOICE", "READY", light), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(4), 0) })
        val activeKeyMissing = if (store.aiProvider == "nvidia") store.nvidiaApiKey.isBlank() else store.apiKey.isBlank()
        indicators.addView(indicator("AI CORE", if (activeKeyMissing) "KEY NEEDED" else store.aiProvider.uppercase(Locale.ROOT), if (activeKeyMissing) Color.parseColor("#E5A33A") else light), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(4), 0) })
        indicators.addView(indicator("MEMORY", "LOCAL", light), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(4), 0) })
        val network = (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork != null
        indicators.addView(indicator("NETWORK", if (network) "CONNECTED" else "OFFLINE", if (network) light else Color.parseColor("#E5A33A")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(indicators)

        val panels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val weatherCard = panel("LIVE WEATHER • CENTURION", "Connecting to weather feed…")
        weatherView = weatherCard.getChildAt(1) as TextView
        panels.addView(weatherCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(5), 0) })
        val timeCard = panel("PRETORIA • LOCAL TIME", "Loading local time…")
        clockView = timeCard.getChildAt(1) as TextView
        panels.addView(timeCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(5), 0) })
        panels.addView(panel("TASKS / MEMORY", store.notes().size.toString() + " SAVED NOTES\\nOn-device memory"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(panels)
        clockTick.run()
        weatherView?.let { refreshWeather(it) }

        voiceStatus = TextView(this).apply {
            text = if (store.elevenKey.isBlank()) "VOICE • Add your ElevenLabs key in Settings" else "VOICE • ElevenLabs configured"
            setTextColor(blue); textSize = 10f; setPadding(px(4), px(4), px(4), 0)
        }
        root.addView(voiceStatus)
        caption = TextView(this).apply { setTextColor(light); textSize = 16f; minLines = 2; setPadding(px(4), px(8), px(4), px(4)) }
        you = TextView(this).apply { setTextColor(Color.parseColor("#C58A1B")); textSize = 12f; setPadding(px(4), 0, px(4), px(8)) }
        root.addView(caption)
        root.addView(you)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = "Speak or type a command…"
            setHintTextColor(Color.parseColor("#765015"))
            setTextColor(light)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { val t = v.text.toString(); v.text = ""; ask(t); true } else false
            }
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "◉ MIC"; setTextColor(light); setBackgroundColor(Color.parseColor("#38270D"))
            setOnClickListener { micTapped() }
        }, LinearLayout.LayoutParams(px(72), px(56)))
        root.addView(row)

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, px(8), 0, px(8)) }
        fun quickAction(label: String, prompt: String) {
            quick.addView(Button(this).apply {
                text = label; textSize = 9f; setTextColor(light)
                setBackgroundColor(Color.parseColor("#182A35"))
                setOnClickListener { input.setText(prompt); input.setSelection(input.text.length); input.requestFocus() }
            }, LinearLayout.LayoutParams(0, px(44), 1f).apply { setMargins(0, 0, px(4), 0) })
        }
        quickAction("MUSIC", "play music")
        quickAction("NAV", "navigate to ")
        quickAction("REMIND", "remind me to ")
        quickAction("CALL", "call ")
        root.addView(quick)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#04080C"))
            addView(root)
        })

        Speaker.onState = { on ->
            runOnUiThread { hud.mode = if (on) 2 else 0; status.text = if (on) "SPEAKING" else "ONLINE" }
        }
        voice = VoiceInput(
            this,
            { t -> runOnUiThread { ask(t) } },
            { on -> runOnUiThread { hud.mode = if (on) 1 else 0; status.text = if (on) "LISTENING" else "ONLINE" } },
            { msg -> runOnUiThread { show(msg) } }
        )

        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
        show("$hello, ${store.name.ifBlank { "sir" }}. All systems are online. How may I help?")
    }

    private fun checkForUpdates() {
        status.text = "CHECKING UPDATES"
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://api.github.com/repos/rbplumbing98-arch/Personal-AI-Jarvis-/releases/latest")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 12000
                connection.readTimeout = 12000
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("No published signed update is available yet.")
                }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = release.optString("tag_name")
                val releaseUrl = release.optString("html_url")
                val latestCode = tag.substringAfterLast('-').toLongOrNull() ?: 0L
                val installedCode = packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                val assets = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                val downloadUrl = apkUrl
                runOnUiThread {
                    status.text = "ONLINE"
                    if (latestCode > installedCode && !downloadUrl.isNullOrBlank()) {
                        AlertDialog.Builder(this)
                            .setTitle("J.A.R.V.I.S. update available")
                            .setMessage("A newer signed update is ready. Download it and install it over your existing JARVIS app. Your saved settings and keys should remain in place when the signing certificate matches.")
                            .setPositiveButton("Download update") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("J.A.R.V.I.S. updates")
                            .setMessage(if (latestCode > 0L) "You are running the latest published update." else "No signed update has been published yet.")
                            .setPositiveButton("OK", null)
                            .setNeutralButton("Release page") { _, _ ->
                                if (releaseUrl.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "ONLINE"
                    AlertDialog.Builder(this)
                        .setTitle("Update check unavailable")
                        .setMessage(e.message ?: "Please try again later.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun refreshWeather(target: TextView) {
        Thread {
            var result = "Weather feed unavailable\\nCheck your internet connection"
            var connection: HttpURLConnection? = null
            try {
                val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=-25.8601&longitude=28.1878&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=Africa%2FJohannesburg"
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val data = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val current = data.getJSONObject("current")
                val temperature = current.optDouble("temperature_2m")
                val feels = current.optDouble("apparent_temperature")
                val humidity = current.optInt("relative_humidity_2m")
                val wind = current.optDouble("wind_speed_10m")
                val code = current.optInt("weather_code")
                val condition = when (code) {
                    0 -> "Clear sky"
                    1 -> "Mainly clear"
                    2 -> "Partly cloudy"
                    3 -> "Overcast"
                    45, 48 -> "Foggy"
                    51, 53, 55 -> "Drizzle"
                    56, 57 -> "Freezing drizzle"
                    61, 63, 65 -> "Rain"
                    66, 67 -> "Freezing rain"
                    71, 73, 75, 77 -> "Snow"
                    80, 81, 82 -> "Rain showers"
                    85, 86 -> "Snow showers"
                    95, 96, 99 -> "Thunderstorms"
                    else -> "Current conditions"
                }
                result = String.format(Locale.UK, "%.1f°C • %s\\nFeels %.1f°C • Humidity %d%%\\nWind %.0f km/h", temperature, condition, feels, humidity, wind)
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
            runOnUiThread { target.text = result }
        }.start()
    }

    private fun show(text: String) {
        caption.text = text
        Speaker.say(text)
    }

    private fun ask(t: String) {
        if (t.isBlank()) return
        you.text = "You: $t"
        hud.mode = 3
        status.text = "THINKING"
        brain.handle(t) { show(it) }
    }

    private fun micTapped() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            Speaker.stop()
            voice?.start()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voice?.start()
        } else {
            show("I need microphone permission to hear you, sir. You can still type.")
        }
    }


    private fun panel(title: String, body: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(9), px(10), px(7), px(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0B1721"))
                setStroke(px(1), Color.parseColor("#294452"))
                cornerRadius = px(10).toFloat()
            }
        }
        card.addView(TextView(this).apply {
            text = title; textSize = 9f; letterSpacing = 0.08f
            setTextColor(Color.parseColor("#E2B65F"))
        })
        card.addView(TextView(this).apply {
            text = body; textSize = 10f; setTextColor(Color.parseColor("#B6C9D2"))
            setPadding(0, px(6), 0, 0)
        })
        return card
    }

    private fun openSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), px(8)) }
        fun field(hint: String, value: String, secret: Boolean = false): EditText {
            val e = EditText(this)
            e.hint = hint
            e.setText(value)
            if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(e)
            return e
        }
        val name = field("Your name", store.name)
        val area = field("Location area", store.area)
        val provider = field("AI provider: anthropic or nvidia", store.aiProvider)
        val key = field("Anthropic API key (optional)", store.apiKey, true)
        val nvidiaKey = field("NVIDIA NIM API key", store.nvidiaApiKey, true)
        val nvidiaModel = field("NVIDIA model ID", store.nvidiaModel)
        val elKey = field("ElevenLabs API key (Jarvis voice)", store.elevenKey, true)
        val vid = field("ElevenLabs voice ID", store.voiceId)
        val spotify = CheckBox(this).apply { text = "Use Spotify for music"; isChecked = store.useSpotify }
        val read = CheckBox(this).apply { text = "Read incoming messages aloud"; isChecked = store.readAloud }
        val access = Button(this).apply {
            text = "Grant notification access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val testVoice = Button(this).apply {
            text = "TEST J.A.R.V.I.S. VOICE"
            setOnClickListener {
                store.elevenKey = elKey.text.toString().trim()
                store.voiceId = vid.text.toString().trim()
                voiceStatus.text = "VOICE • Testing ElevenLabs…"
                Speaker.testVoice()
            }
        }
        box.addView(spotify)
        box.addView(read)
        box.addView(access)
        box.addView(testVoice)
        AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S. SETTINGS")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                store.name = name.text.toString().trim()
                store.area = area.text.toString().trim()
                store.aiProvider = provider.text.toString().trim().lowercase(Locale.ROOT)
                store.apiKey = key.text.toString().trim()
                store.nvidiaApiKey = nvidiaKey.text.toString().trim()
                store.nvidiaModel = nvidiaModel.text.toString().trim()
                store.elevenKey = elKey.text.toString().trim()
                store.voiceId = vid.text.toString().trim()
                store.useSpotify = spotify.isChecked
                store.readAloud = read.isChecked
                voiceStatus.text = if (store.elevenKey.isBlank()) "VOICE • Android fallback active" else "VOICE • ElevenLabs configured"
                show("Settings saved, sir.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(clockTick)
        voice?.stop()
        super.onDestroy()
    }
}
