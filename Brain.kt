package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Brain(context: Context, private val store: Store) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val noSession =
        "I cannot see a music player, sir. Grant notification access in settings and start some music first."

    fun handle(raw: String, out: (String) -> Unit) = route(raw) { out(polish(it)) }

    private fun polish(t: String): String = t.replace(Regex("\\bsir\\b"), store.name.ifBlank { "sir" })
    private fun title(s: String): String = s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    private fun launch(i: Intent): Boolean = try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true } catch (e: Exception) { false }

    private fun online(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val c = cm.getNetworkCapabilities(n) ?: return false
        return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun route(raw: String, reply: (String) -> Unit) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val l = q.lowercase(Locale.ROOT).replace(Regex("^(hey |ok |okay )?jarvis[,.]?\\s*"), "")

        store.skill(l)?.let { return reply(it) }

        Regex("^(?:my name is|call me)\\s+(.+)").find(l)?.let {
            store.name = title(it.groupValues[1])
            return reply("A pleasure, ${store.name}. I shall remember that.")
        }
        Regex("^(?:i live in|i am in|i'm in|my location is)\\s+(.+)").find(l)?.let {
            store.area = title(it.groupValues[1])
            return reply("Understood, sir. Your area is now ${store.area}.")
        }
        Regex("^(?:learn|remember) that\\s+(.+)").find(l)?.let {
            store.addFact(it.groupValues[1])
            return reply("Understood, sir. I have learned that ${it.groupValues[1]}.")
        }
        Regex("^(?:when i say|teach you)\\s*[\"']?(.+?)[\"']?,?\\s+(?:you |then )?(?:say|reply|respond)\\s+[\"']?(.+?)[\"']?$").find(l)?.let {
            store.setSkill(it.groupValues[1], it.groupValues[2])
            return reply("Learned, sir. When you say ${it.groupValues[1]}, I will say ${it.groupValues[2]}.")
        }
        if (Regex("what (?:have you|did you) learn|what do you know about me").containsMatchIn(l)) {
            val top = store.topPlay()
            return reply(
                "I know your name as ${store.name.ifBlank { "not yet set" }} and your area as ${store.area.ifBlank { "not yet set" }}. " +
                    "I hold ${store.facts().size} facts, ${store.skillCount()} taught phrases, and ${store.notes().size} notes" +
                    (if (top != null) ". Your most played request is $top." else ".")
            )
        }
        if (Regex("^forget (everything|what you (?:have )?learned)").containsMatchIn(l)) {
            store.wipeLearned()
            return reply("Learned data erased, sir.")
        }
        if (Regex("^read(?: aloud)?:\\s*\\S").containsMatchIn(l)) {
            return reply(q.replace(Regex("^.*?read(?: aloud)?:\\s*", RegexOption.IGNORE_CASE), ""))
        }
        if (Regex("read (my )?(messages|texts|sms)").containsMatchIn(l)) {
            val msgs = Inbox.drain()
            return reply(
                if (msgs.isEmpty()) "You have no new messages, sir."
                else msgs.joinToString(" ") { "Message from ${it.first}. ${it.second}." }
            )
        }

        Regex("^(?:remember|note|remind me(?: to)?|save)\\s+(.+)").find(l)?.let {
            store.addNote(it.groupValues[1])
            return reply("Noted, sir. Saved on this device: ${it.groupValues[1]}.")
        }
        if (Regex("^(show|read|list)?\\s*(my )?(notes|reminders)").containsMatchIn(l)) {
            val n = store.notes()
            return reply(
                if (n.isEmpty()) "You have no saved notes, sir."
                else "You have ${n.size} saved. Most recent: " + n.takeLast(3).reversed().joinToString(". Then, ") + "."
            )
        }
        if (Regex("^clear (my )?(notes|reminders)").containsMatchIn(l)) {
            store.clearNotes()
            return reply("All notes cleared, sir.")
        }

        if (Regex("^(pause|stop)( the)?( music| song)?$").matches(l)) return reply(if (MediaControl.pause(ctx)) "Paused, sir." else noSession)
        if (Regex("^(resume|continue)( the)?( music| song)?$").matches(l)) return reply(if (MediaControl.play(ctx)) "Resuming, sir." else noSession)
        if (Regex("^(next|skip)( song| track)?$").matches(l)) return reply(if (MediaControl.next(ctx)) "Skipping, sir." else noSession)
        if (Regex("^(previous|back)( song| track)?$").matches(l)) return reply(if (MediaControl.previous(ctx)) "Going back, sir." else noSession)
        if (Regex("what('?s| is) (playing|this song)").containsMatchIn(l)) return reply(MediaControl.nowPlaying(ctx) ?: "Nothing appears to be playing, sir.")

        Regex("^(?:play|music)\\b\\s*(.*)").find(l)?.let { m ->
            var s = m.groupValues[1].trim().removeSuffix(" on spotify").trim()
            if (s.isEmpty() || s == "music") return reply(if (MediaControl.play(ctx)) "Resuming, sir." else noSession)
            if (s == "my usual") s = store.topPlay() ?: return reply("I do not yet know your usual, sir. Ask me to play something a few times first.")
            val spotify = store.useSpotify || l.endsWith("on spotify")
            store.bump("play:$s")
            return reply(if (MediaControl.playFromSearch(ctx, s, spotify)) "Playing $s, sir." else "I could not start playback, sir.")
        }

        Regex("^(?:navigate|directions|take me|drive)(?: to)?\\s*(.*)").find(l)?.let { m ->
            var d = m.groupValues[1].trim()
            if (d.isEmpty() || d == "home") {
                d = store.facts().firstOrNull { it.startsWith("my home is ") }?.removePrefix("my home is ")
                    ?: return reply("Tell me your home address first, sir. Say: learn that my home is, then the address.")
            } else if (store.area.isNotBlank()) {
                d = d + " near " + store.area
            }
            val maps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d))).setPackage("com.google.android.apps.maps")
            val ok = launch(maps) || launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(d))))
            return reply(if (ok) "Navigation started to $d, sir." else "I could not open a maps app, sir.")
        }

        Regex("^(?:call|phone|dial)\\s+([\\d +()-]{5,})").find(l)?.let {
            val num = it.groupValues[1].replace(Regex("[^\\d+]"), "")
            return reply(if (launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))) "Dialling $num, sir." else "I could not open the dialler, sir.")
        }
        if (Regex("^(call|phone)\\b").containsMatchIn(l)) return reply("I need a phone number, sir. I do not read your contacts.")

        Regex("^(?:search(?: google| the web)? for|google|look up|find online)\\s+(.+)$").find(l)?.let { match ->
            val searchTerm = match.groupValues[1].trim()
            val searchUrl = "https://www.google.com/search?q=" + Uri.encode(searchTerm)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            if (store.useChromeSearch) intent.setPackage("com.android.chrome")
            val opened = launch(intent) || launch(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
            return reply(
                if (opened) "Opening Google search for $searchTerm, sir."
                else "I could not open a browser. Please install or enable Google Chrome, sir."
            )
        }

        if (Regex("\\btime\\b").containsMatchIn(l) && l.length < 30) {
            return reply("It is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + ", sir.")
        }

        ask(q, reply)
    }

    private fun ask(q: String, reply: (String) -> Unit) {
        val key = q.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), "").trim()
        store.cached(key)?.let { return reply(it) }
        if (!online()) return reply("I am offline, sir. I can still use notes, what you have taught me, and my saved answers.")
        if (store.apiKey.isBlank()) return reply("Add your Anthropic API key in settings so I can answer open questions, sir.")
        Thread {
            val out = try { callClaude(q) } catch (e: Exception) { null }
            main.post {
                if (out == null) {
                    reply("My reasoning core is unavailable at the moment, sir.")
                } else {
                    store.cachePut(key, out)
                    reply(out)
                }
            }
        }.start()
    }

    private fun callClaude(q: String): String? {
        val who = store.name.ifBlank { "sir" }
        val facts = store.facts().takeLast(15).joinToString("; ").ifBlank { "none" }
        val system = "You are JARVIS, a calm, dry, refined British AI butler. Address the user as $who now and then. " +
            "Reply in at most two short spoken sentences, plain text, no markdown. " +
            "User area: ${store.area.ifBlank { "unknown" }}. Known facts: $facts."
        val body = JSONObject()
            .put("model", "claude-sonnet-5-5")
            .put("max_tokens", 300)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", q)))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 10000
            c.readTimeout = 30000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.setRequestProperty("x-api-key", store.apiKey)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) return null
            val text = c.inputStream.bufferedReader().readText()
            val content = JSONObject(text).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                if (b.optString("type") == "text") sb.append(b.getString("text"))
            }
            return sb.toString().trim().ifEmpty { null }
        } finally {
            c.disconnect()
        }
    }
}
