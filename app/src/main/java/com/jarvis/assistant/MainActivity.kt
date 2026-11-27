package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var brain: Brain
    private lateinit var hud: AureliaView
    private lateinit var status: TextView
    private lateinit var caption: TextView
    private lateinit var you: TextView
    private lateinit var voiceStatus: TextView
    private var eventFeed: TextView? = null
    private var voice: VoiceInput? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var clockView: TextView? = null
    private var weatherView: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() {
            clockView?.text = SimpleDateFormat("EEEE, d MMM yyyy\nh:mm:ss a", Locale.UK).format(Date())
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val MODEL_PICK_REQUEST = 702
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
            runOnUiThread { if (::voiceStatus.isInitialized) voiceStatus.text = "VOICE ISSUE • $message" }
        }
        Speaker.onVoiceStatus = { message ->
            runOnUiThread { if (::voiceStatus.isInitialized) voiceStatus.text = "VOICE SYSTEM • $message" }
        }
        Speaker.init(this)

        val gold = Color.parseColor("#D9A441")
        val light = Color.parseColor("#F5DDA5")
        val muted = Color.parseColor("#B9AE96")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(12), px(12), px(16))
            setBackgroundColor(Color.parseColor("#050709"))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, px(8)) }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(TextView(this).apply { text = "J.A.R.V.I.S."; setTextColor(light); textSize = 21f; letterSpacing = 0.16f; typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL) })
        brand.addView(TextView(this).apply { text = "QUANTUM CORE  •  PERSONAL AI"; setTextColor(gold); textSize = 8f; letterSpacing = 0.18f })
        top.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        status = TextView(this).apply { text = "● ONLINE"; setTextColor(gold); textSize = 10f; gravity = Gravity.CENTER_VERTICAL }
        top.addView(status)
        top.addView(Button(this).apply {
            text = "☰"; textSize = 21f; setTextColor(light); setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Open JARVIS control menu"
            setOnClickListener { showControlMenu() }
        }, LinearLayout.LayoutParams(px(48), px(48)))
        content.addView(top)
        content.addView(View(this).apply { setBackgroundColor(Color.parseColor("#59421F")) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1)))

        val hubLabel = TextView(this).apply {
            text = "VOICE INTERFACE  /  INTELLIGENCE SYSTEM"
            setTextColor(gold); textSize = 10f; letterSpacing = 0.24f
            gravity = Gravity.CENTER; setPadding(0, px(12), 0, 0)
        }
        content.addView(hubLabel)
        hud = AureliaView(this)
        content.addView(hud, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(310))
            .apply { setMargins(0, px(2), 0, 0) })
        content.addView(TextView(this).apply {
            text = "J.A.R.V.I.S.  •  ALWAYS READY"; setTextColor(light); textSize = 9f
            letterSpacing = 0.2f; gravity = Gravity.CENTER; setPadding(0, 0, 0, px(8))
        })

        val indicators = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, px(2), 0, px(12)) }
        fun indicator(label: String, value: String, color: Int): TextView = TextView(this).apply {
            text = "$label\n$value"; textSize = 8f; gravity = Gravity.CENTER
            setTextColor(color); setPadding(px(5), px(7), px(5), px(7))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#15110B")); setStroke(px(1), Color.parseColor("#80602D"))
                cornerRadius = px(8).toFloat()
            }
        }
        val voiceLabel = if (store.elevenKey.isNotBlank() && store.voiceId.isNotBlank()) "ELEVEN CONFIG" else "LOCAL VOICE"
        indicators.addView(indicator("VOICE", voiceLabel, light),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(5), 0) })
        val localReady = LocalAi.hasModel(this)
        val onlineReady = store.nvidiaApiKey.isNotBlank() || store.apiKey.isNotBlank()
        val activeKeyMissing = if (store.aiProvider == "nvidia") store.nvidiaApiKey.isBlank()
            else if (store.aiProvider == "anthropic") store.apiKey.isBlank() else !localReady && !onlineReady
        val aiLabel = if (store.aiProvider == "local") {
            if (localReady) "LOCAL READY" else if (onlineReady) "ONLINE BACKUP" else "MODEL NEEDED"
        } else store.aiProvider.uppercase(Locale.ROOT)
        indicators.addView(indicator("AI CORE", aiLabel, if (activeKeyMissing) Color.parseColor("#E5A33A") else light),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(5), 0) })
        val network = (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork != null
        indicators.addView(indicator("NETWORK", if (network) "CONNECTED" else "OFFLINE",
            if (network) light else Color.parseColor("#E5A33A")),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(indicators)

        val intelligenceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val modelReady = LocalAi.hasModel(this)
        val diagnosticsText = "AI  •  ${store.aiProvider.uppercase(Locale.ROOT)}\nOFFLINE MODEL  •  ${if (modelReady) "READY" else "NOT INSTALLED"}\nNETWORK  •  ${if (network) "CONNECTED" else "OFFLINE"}"
        intelligenceRow.addView(panel("LEFT CONSOLE  /  DIAGNOSTICS", diagnosticsText), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, px(5), 0) })
        val eventCard = panel("RIGHT CONSOLE  /  LIVE ACTIVITY", "QUANTUM CORE INITIALISED\nVOICE INTERFACE READY\nMEMORY CENTRE  •  ${store.memories().size} ENTRIES")
        eventFeed = eventCard.getChildAt(1) as TextView
        intelligenceRow.addView(eventCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(intelligenceRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, px(9)) })

        val weatherCard = panel("LIVE WEATHER  /  CENTURION", "Connecting to weather feed…")
        weatherView = weatherCard.getChildAt(1) as TextView
        content.addView(weatherCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, 0, px(9)) })
        val timeCard = panel("LOCAL TIME  /  AFRICA-JOHANNESBURG", "Loading local time…")
        clockView = timeCard.getChildAt(1) as TextView
        content.addView(timeCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { setMargins(0, 0, 0, px(10)) })
        clockTick.run()
        weatherView?.let { refreshWeather(it) }

        voiceStatus = TextView(this).apply {
            text = if (store.elevenKey.isBlank()) "VOICE SYSTEM  •  AUTO / LOCAL VOICE" else "VOICE SYSTEM  •  AUTO / ONLINE CONFIGURED"
            setTextColor(gold); textSize = 9f; setPadding(px(3), px(4), px(3), 0)
        }
        content.addView(voiceStatus)
        val conversationPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14), px(12), px(14), px(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D0C09"))
                setStroke(px(1), Color.parseColor("#A17A36"))
                cornerRadius = px(14).toFloat()
            }
        }
        conversationPanel.addView(TextView(this).apply {
            text = "LIVE TRANSMISSION  /  J.A.R.V.I.S."
            setTextColor(gold); textSize = 9f; letterSpacing = 0.18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        conversationPanel.addView(View(this).apply { setBackgroundColor(Color.parseColor("#59421F")) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1)).apply {
                setMargins(0, px(8), 0, px(8))
            })
        caption = TextView(this).apply {
            setTextColor(light); textSize = 16f; minLines = 2
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setLineSpacing(px(3).toFloat(), 1f)
            setPadding(px(2), px(3), px(2), px(7))
            text = "Good evening, sir. All systems are standing by."
        }
        you = TextView(this).apply {
            setTextColor(Color.parseColor("#C99A43")); textSize = 11f
            setPadding(px(2), px(5), px(2), 0)
        }
        conversationPanel.addView(caption)
        conversationPanel.addView(you)
        content.addView(conversationPanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, px(4), 0, px(8)) })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#11100D")); setStroke(px(1), Color.parseColor("#70552A"))
                cornerRadius = px(12).toFloat()
            }
            setPadding(px(8), px(2), px(5), px(2))
        }
        val input = EditText(this).apply {
            hint = "Talk to JARVIS or type a command…"; setHintTextColor(Color.parseColor("#8C7955"))
            setTextColor(light); textSize = 14f; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEND
            setBackgroundColor(Color.TRANSPARENT)
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { val t = v.text.toString(); v.text = ""; ask(t); true } else false
            }
        }
        row.addView(input, LinearLayout.LayoutParams(0, px(54), 1f))
        row.addView(Button(this).apply {
            text = "◉"; textSize = 20f; setTextColor(light); setBackgroundColor(Color.parseColor("#38270D"))
            contentDescription = "Activate microphone"; setOnClickListener { micTapped() }
        }, LinearLayout.LayoutParams(px(54), px(48)))
        content.addView(row)
        content.addView(Button(this).apply {
            text = "■  STOP AUDIO"; textSize = 9f; setTextColor(muted); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { Speaker.stop(); hud.mode = 0; status.text = "● ONLINE" }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(38)))

        // Slim navigation rail and scrollable mobile dashboard.
        val shell = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#050709")) }
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(px(3), px(12), px(3), px(8))
            background = GradientDrawable().apply { setColor(Color.parseColor("#090A0B")); setStroke(px(1), Color.parseColor("#382B17")) }
        }
        fun railAction(glyph: String, label: String, action: () -> Unit) {
            rail.addView(Button(this).apply {
                text = "$glyph\n$label"; textSize = 8f; isAllCaps = false
                setTextColor(Color.parseColor("#E5BD70")); setBackgroundColor(Color.TRANSPARENT)
                contentDescription = label; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(px(48), px(58)))
        }
        railAction("⌂", "HUB") { show("Quantum Core is online, sir.") }
        railAction("◈", "MEM") { openMemoryManager() }
        railAction("◉", "AI") { openModelManager() }
        railAction("⚙", "SET") { openSettings() }
        railAction("⋯", "MORE") { showControlMenu() }
        shell.addView(rail, LinearLayout.LayoutParams(px(54), ViewGroup.LayoutParams.MATCH_PARENT))
        val dashboardScroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.parseColor("#050709")); addView(content) }
        shell.addView(dashboardScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        setContentView(shell)

        Speaker.onState = { on ->
            runOnUiThread { hud.mode = if (on) 2 else 0; status.text = if (on) "● SPEAKING" else "● ONLINE" }
        }
        voice = VoiceInput(
            this,
            { t -> runOnUiThread { ask(t) } },
            { on -> runOnUiThread { hud.mode = if (on) 1 else 0; status.text = if (on) "● LISTENING" else "● ONLINE" } },
            { msg -> runOnUiThread { show(msg) } }
        )

        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
        show("$hello, ${store.name.ifBlank { "sir" }}. All systems are online. How may I help?")
    }


    private fun showControlMenu() {
        val items = arrayOf("AI Models", "Memory Centre", "CastPlay  •  Cast to TV", "Music & Spotify", "Voice settings",
            "General settings", "Diagnostics", "Connections", "System tools", "Check for updates",
            "Clear conversation context")
        AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S.  /  CONTROL MENU")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openModelManager()
                    1 -> openMemoryManager()
                    2 -> startActivity(Intent(this, CastPlayActivity::class.java))
                    3 -> ask("play music")
                    4, 5, 7 -> openSettings()
                    6 -> showDiagnostics()
                    8 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    9 -> checkForUpdates()
                    10 -> clearConversationContext()
                }
            }
            .setNegativeButton("CLOSE", null).show()
    }

    private fun clearConversationContext() {
        AlertDialog.Builder(this)
            .setTitle("CLEAR CONVERSATION CONTEXT?")
            .setMessage("This clears JARVIS's recent chat history and response cache. Saved Memory Centre entries, API keys, voice settings and other app data will remain.")
            .setPositiveButton("CLEAR") { _, _ ->
                store.clearConversation()
                show("Recent conversation context cleared. Your saved memories remain intact.")
            }
            .setNegativeButton("KEEP", null)
            .show()
    }

    private fun openMemoryManager() {
        val entries = store.memories()
        val labels = entries.map { "[${it.category}]  ${it.text}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S.  /  MEMORY MANAGER")
            .setItems(labels) { _, which -> editMemory(entries[which]) }
            .setPositiveButton("＋ ADD MEMORY") { _, _ -> editMemory(null) }
            .setNeutralButton("ABOUT MEMORY") { _, _ ->
                AlertDialog.Builder(this).setTitle("YOUR PRIVATE MEMORY")
                    .setMessage("Memories are stored locally in JARVIS app storage. Add facts, preferences, people, work details or instructions. Select an entry to edit it. Memories from older versions are also displayed.")
                    .setPositiveButton("GOT IT", null).show()
            }
            .setNegativeButton("CLOSE", null).show()
    }

    private fun editMemory(existing: Store.MemoryEntry?) {
        val categories = arrayOf("Personal", "Family", "Work", "Preferences", "Projects", "Instructions", "Notes")
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), px(4)) }
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, categories)
            val selected = categories.indexOf(existing?.category ?: "Personal")
            setSelection(if (selected >= 0) selected else 0)
        }
        val value = EditText(this).apply {
            hint = "What should JARVIS remember?"; setText(existing?.text.orEmpty())
            minLines = 3; maxLines = 6; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        box.addView(TextView(this).apply { text = "CATEGORY"; textSize = 10f; setTextColor(Color.parseColor("#D9A441")) })
        box.addView(category)
        box.addView(TextView(this).apply { text = "MEMORY"; textSize = 10f; setTextColor(Color.parseColor("#D9A441")); setPadding(0, px(10), 0, 0) })
        box.addView(value)
        val builder = AlertDialog.Builder(this).setTitle(if (existing == null) "ADD MEMORY" else "EDIT MEMORY").setView(box).setPositiveButton("SAVE", null).setNegativeButton("CANCEL", null)
        if (existing != null && !existing.id.startsWith("legacy-")) builder.setNeutralButton("DELETE", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = value.text.toString().trim()
                if (text.isBlank()) { value.error = "Enter something for JARVIS to remember"; return@setOnClickListener }
                store.saveMemory(existing?.id, category.selectedItem.toString(), text)
                dialog.dismiss(); openMemoryManager()
            }
            if (existing != null && !existing.id.startsWith("legacy-")) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(this).setTitle("Delete this memory?").setMessage("This removes the selected entry from JARVIS memory.")
                        .setNegativeButton("KEEP", null).setPositiveButton("DELETE") { _, _ ->
                            store.deleteMemory(existing.id); dialog.dismiss(); openMemoryManager()
                        }.show()
                }
            }
        }
        dialog.show()
    }
    private fun showDiagnostics() {
        val network = (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork != null
        val model = if (LocalAi.hasModel(this)) "Installed" else "Not installed"
        val provider = store.aiProvider.uppercase(Locale.ROOT)
        val voiceEngine = if (store.elevenKey.isBlank()) "Android fallback" else "ElevenLabs configured"
        val details = "AI mode: $provider\nOffline GGUF: $model\nNetwork: ${if (network) "Connected" else "Offline"}\nVoice: $voiceEngine\nMemory entries: ${store.memories().size}\nRecent chat turns: ${store.conversation().size}"
        AlertDialog.Builder(this).setTitle("SYSTEM DIAGNOSTICS").setMessage(details)
            .setPositiveButton("CLOSE", null).setNeutralButton("SETTINGS") { _, _ -> openSettings() }.show()
    }

    private fun checkForUpdates() {
        status.text = "● CHECKING"
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
                    throw IllegalStateException("No published update is available yet.")
                }
                val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val tag = release.optString("tag_name")
                val releaseUrl = release.optString("html_url")
                val latestCode = tag.substringAfterLast('-').toLongOrNull() ?: 0L
                val installedCode = if (Build.VERSION.SDK_INT >= 28) {
                    packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode.toLong()
                }
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
                    status.text = "● ONLINE"
                    if (latestCode > installedCode && !downloadUrl.isNullOrBlank()) {
                        AlertDialog.Builder(this)
                            .setTitle("J.A.R.V.I.S. update available")
                            .setMessage("Build $latestCode is available. JARVIS will download it and verify the package identity and signing certificate before opening Android's installer. Your current app will not be changed if verification fails.")
                            .setPositiveButton("VERIFY UPDATE") { _, _ -> downloadAndVerifyUpdate(downloadUrl, releaseUrl) }
                            .setNegativeButton("LATER", null)
                            .show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("J.A.R.V.I.S. updates")
                            .setMessage(if (latestCode > 0L) "You are running the latest published build." else "No published build number could be read.")
                            .setPositiveButton("OK", null)
                            .setNeutralButton("RELEASE PAGE") { _, _ ->
                                if (releaseUrl.isNotBlank()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "● ONLINE"
                    AlertDialog.Builder(this).setTitle("Update check unavailable")
                        .setMessage(e.message ?: "Please try again later.")
                        .setPositiveButton("OK", null).show()
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun signingDigests(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()
    }

    private fun downloadAndVerifyUpdate(downloadUrl: String, releaseUrl: String) {
        status.text = "● DOWNLOADING"
        eventFeed?.text = "SECURE UPDATE\nDOWNLOADING RELEASE APK"
        Thread {
            var connection: HttpURLConnection? = null
            var updateFile: File? = null
            try {
                val folder = File(cacheDir, "updates").apply { if (!exists() && !mkdirs()) throw IllegalStateException("Could not create update cache.") }
                updateFile = File(folder, "jarvis-update.apk")
                connection = URL(downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 45000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                if (connection.responseCode !in 200..299) throw IllegalStateException("APK download failed (HTTP ${connection.responseCode}).")
                val length = connection.contentLengthLong
                if (length > 250L * 1024L * 1024L) throw IllegalStateException("The update is larger than the allowed 250 MB limit.")
                connection.inputStream.use { input ->
                    updateFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 250L * 1024L * 1024L) throw IllegalStateException("The update exceeded the 250 MB safety limit.")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (!updateFile.isFile || updateFile.length() < 1_000_000L) throw IllegalStateException("The downloaded APK is incomplete.")
                val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
                val archive = packageManager.getPackageArchiveInfo(updateFile.absolutePath, flags)
                    ?: throw IllegalStateException("Android could not read the downloaded APK.")
                if (archive.packageName != packageName) throw IllegalStateException("Update blocked: the APK belongs to a different application.")
                val currentInfo = packageManager.getPackageInfo(packageName, flags)
                val currentSignatures = signingDigests(currentInfo)
                val updateSignatures = signingDigests(archive)
                if (currentSignatures.isEmpty() || updateSignatures.isEmpty() || currentSignatures != updateSignatures) {
                    updateFile.delete()
                    throw IllegalStateException("UPDATE BLOCKED FOR YOUR PROTECTION. The APK signing certificate does not match the app installed on this phone. Your existing JARVIS app and its data have not been touched. A permanent signing key must be configured before in-place updates can work.")
                }
                val currentVersion = if (Build.VERSION.SDK_INT >= 28) currentInfo.longVersionCode else currentInfo.versionCode.toLong()
                val updateVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
                if (updateVersion <= currentVersion) throw IllegalStateException("The downloaded APK is not newer than the installed version.")
                runOnUiThread {
                    status.text = "● ONLINE"
                    eventFeed?.text = "UPDATE VERIFIED\nSIGNATURE MATCHED • BUILD $updateVersion"
                    AlertDialog.Builder(this)
                        .setTitle("Update verified")
                        .setMessage("The APK belongs to J.A.R.V.I.S. and its signing certificate matches this installation. Android will now ask you to confirm the update.")
                        .setPositiveButton("INSTALL") { _, _ -> installVerifiedUpdate(updateFile) }
                        .setNegativeButton("NOT NOW", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "● ONLINE"
                    eventFeed?.text = "UPDATE CHECK\nINSTALLATION SAFETY CHECK"
                    AlertDialog.Builder(this).setTitle("Update not installed")
                        .setMessage(e.message ?: "The update could not be verified. Your current app remains unchanged.")
                        .setPositiveButton("OK", null)
                        .setNeutralButton("RELEASE PAGE") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))) }
                        .show()
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun installVerifiedUpdate(file: File?) {
        if (file == null || !file.isFile) {
            show("The verified update file is no longer available. Please check for updates again.")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this).setTitle("Allow JARVIS updates")
                .setMessage("Android needs your permission to install updates downloaded from the JARVIS release page. Enable 'Allow from this source', return to JARVIS, and check for updates again.")
                .setPositiveButton("OPEN ANDROID SETTING") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("CANCEL", null).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(install)
        } catch (e: Exception) {
            show("Android could not open the verified installer: " + (e.message ?: "unknown error"))
        }
    }

    private fun refreshWeather(target: TextView) {
        Thread {
            var result = "Weather feed unavailable\nCheck your internet connection"
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
                result = String.format(Locale.UK, "%.1f°C • %s\nFeels %.1f°C • Humidity %d%%\nWind %.0f km/h", temperature, condition, feels, humidity, wind)
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
            runOnUiThread { target.text = result }
        }.start()
    }

    private fun show(text: String) {
        caption.text = text
        eventFeed?.text = "JARVIS RESPONSE\n" + text.take(115)
        Speaker.say(text)
    }

    private fun ask(t: String) {
        if (t.isBlank()) return
        you.text = "You: $t"
        eventFeed?.text = "PROCESSING REQUEST\n" + t.take(100)
        hud.mode = 3
        status.text = "● THINKING"
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
            setPadding(px(12), px(12), px(10), px(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#100E0A"))
                setStroke(px(1), Color.parseColor("#6B5126"))
                cornerRadius = px(10).toFloat()
            }
        }
        card.addView(TextView(this).apply {
            text = title; textSize = 9f; letterSpacing = 0.16f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#E2B65F"))
        })
        card.addView(TextView(this).apply {
            text = body; textSize = 11f; setTextColor(Color.parseColor("#E0D4BA")); setLineSpacing(px(3).toFloat(), 1f)
            setPadding(0, px(6), 0, 0)
        })
        return card
    }

    private fun openModelManager() {
        val ready = LocalAi.hasModel(this)
        val message = if (ready) {
            "OFFLINE BRAIN READY\nQwen 0.5B GGUF is installed on this phone. JARVIS can answer without internet or API keys.\n\nYou can also import another compatible .gguf model."
        } else {
            "OFFLINE BRAIN NOT INSTALLED\nDownload the recommended Qwen 0.5B GGUF model (${LocalAi.MODEL_SIZE_LABEL}) over Wi-Fi, or import a GGUF file you already have.\n\nThe model is stored privately on this phone and is not included in app updates."
        }
        AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S. • LOCAL AI MODELS")
            .setMessage(message)
            .setPositiveButton(if (ready) "MODEL INSTALLED" else "DOWNLOAD MODEL") { _, _ ->
                if (!ready) {
                    try {
                        LocalAi.startDownload(this)
                        show("Offline brain download started. Android will show a notification when it is complete.")
                    } catch (e: Exception) {
                        show("I could not start the model download: " + (e.message ?: "unknown error"))
                    }
                }
            }
            .setNeutralButton("IMPORT GGUF") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, MODEL_PICK_REQUEST)
            }
            .setNegativeButton(if (ready) "REMOVE MODEL" else "CLOSE") { _, _ ->
                if (ready) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        LocalAi.removeModel(this@MainActivity)
                        runOnUiThread { show("Offline model removed from this phone.") }
                    }
                }
            }
            .show()
    }

    @Deprecated("Deprecated in Android API; retained for this Activity-based app.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MODEL_PICK_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            status.text = "● IMPORTING MODEL"
            Thread {
                try {
                    LocalAi.importModel(this, uri)
                    runOnUiThread { status.text = "ONLINE"; show("GGUF model imported successfully. The offline brain is ready.") }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "ONLINE"; show("Model import failed: " + (e.message ?: "invalid file")) }
                }
            }.start()
        }
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
        val provider = field("AI mode: local (default), nvidia or anthropic", store.aiProvider)
        val key = field("Anthropic API key (only needed if using Anthropic)", store.apiKey, true)
        val nvidiaKey = field("NVIDIA NIM API key", store.nvidiaApiKey, true)
        val nvidiaModel = field("NVIDIA model ID", store.nvidiaModel)
        val anthropicModel = field("Anthropic model ID", store.anthropicModel)
        val elKey = field("ElevenLabs API key (Jarvis voice)", store.elevenKey, true)
        val vid = field("ElevenLabs voice ID", store.voiceId)
        val browseVoices = Button(this).apply {
            text = "FIND BRITISH MALE VOICES"
            setOnClickListener {
                val apiKey = elKey.text.toString().trim()
                if (apiKey.isBlank()) {
                    show("Enter your ElevenLabs API key first, then tap Find British Male Voices.")
                    return@setOnClickListener
                }
                store.elevenKey = apiKey
                text = "SEARCHING ELEVENLABS VOICES…"
                isEnabled = false
                Thread {
                    var failure: String? = null
                    val choices = try {
                        val connection = (URL("https://api.elevenlabs.io/v2/voices?accent=british&gender=male&language=en&page_size=100&sort=name&sort_direction=asc")
                            .openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 12000
                            readTimeout = 20000
                            setRequestProperty("xi-api-key", apiKey)
                            setRequestProperty("Accept", "application/json")
                        }
                        try {
                            val code = connection.responseCode
                            if (code !in 200..299) {
                                val detail = try { connection.errorStream?.bufferedReader()?.readText()?.take(180) } catch (_: Exception) { null }
                                throw java.io.IOException("ElevenLabs HTTP $code. ${detail ?: "Check API key and account access."}")
                            }
                            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                            val voices = json.optJSONArray("voices") ?: org.json.JSONArray()
                            (0 until voices.length()).mapNotNull { index ->
                                val voice = voices.optJSONObject(index) ?: return@mapNotNull null
                                val id = voice.optString("voice_id")
                                val name = voice.optString("name")
                                if (id.isBlank() || name.isBlank()) null else id to name
                            }.distinctBy { it.first }
                        } finally { connection.disconnect() }
                    } catch (e: Exception) {
                        failure = e.message ?: "Could not load ElevenLabs voices."
                        emptyList()
                    }
                    runOnUiThread {
                        text = "FIND BRITISH MALE VOICES"
                        isEnabled = true
                        if (failure != null) {
                            show("Voice search failed: $failure")
                        } else if (choices.isEmpty()) {
                            show("No British male voices are currently available to this ElevenLabs account. Add a suitable voice to My Voices in ElevenLabs, then search again.")
                        } else {
                            val names = choices.map { it.second }.toTypedArray()
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Choose your J.A.R.V.I.S. voice")
                                .setItems(names) { _, selected ->
                                    vid.setText(choices[selected].first)
                                    voiceStatus.text = "VOICE SYSTEM • ${choices[selected].second} SELECTED — SAVE SETTINGS, THEN TEST ONLINE"
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                }.start()
            }
        }
        box.addView(browseVoices)
        val spotify = CheckBox(this).apply { text = "Use Spotify for music"; isChecked = store.useSpotify }
        val read = CheckBox(this).apply { text = "Read incoming messages aloud"; isChecked = store.readAloud }
        val access = Button(this).apply {
            text = "Grant notification access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        fun saveVoiceCredentialsForTest() {
            store.elevenKey = elKey.text.toString().trim()
            store.voiceId = vid.text.toString().trim()
        }
        val testOnlineVoice = Button(this).apply {
            text = "TEST ONLINE • ELEVENLABS"
            setOnClickListener {
                saveVoiceCredentialsForTest()
                voiceStatus.text = "VOICE SYSTEM • TESTING ONLINE ELEVENLABS…"
                Speaker.testOnlineVoice()
            }
        }
        val testOfflineVoice = Button(this).apply {
            text = "TEST OFFLINE • LOCAL J.A.R.V.I.S."
            setOnClickListener {
                voiceStatus.text = "VOICE SYSTEM • TESTING ON-DEVICE VOICE…"
                Speaker.testOfflineVoice()
            }
        }
        val installOfflineVoice = Button(this).apply {
            text = "INSTALL / MANAGE OFFLINE VOICE"
            setOnClickListener {
                try {
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        box.addView(spotify)
        box.addView(read)
        box.addView(access)
        box.addView(testOnlineVoice)
        box.addView(testOfflineVoice)
        box.addView(installOfflineVoice)
        AlertDialog.Builder(this)
            .setTitle("J.A.R.V.I.S. SETTINGS")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Save") { _, _ ->
                store.name = name.text.toString().trim()
                store.area = area.text.toString().trim()
                store.apiKey = key.text.toString().trim()
                store.aiProvider = provider.text.toString().trim().lowercase(Locale.ROOT)
                store.nvidiaApiKey = nvidiaKey.text.toString().trim()
                store.nvidiaModel = nvidiaModel.text.toString().trim()
                store.anthropicModel = anthropicModel.text.toString().trim()
                store.elevenKey = elKey.text.toString().trim()
                store.voiceId = vid.text.toString().trim()
                store.useSpotify = spotify.isChecked
                store.readAloud = read.isChecked
                voiceStatus.text = if (store.elevenKey.isBlank()) "VOICE SYSTEM • AUTO / LOCAL VOICE" else "VOICE SYSTEM • AUTO / ONLINE CONFIGURED"
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
