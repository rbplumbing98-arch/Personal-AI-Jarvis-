package com.jarvis.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small on-device store. The API key is encrypted with an Android Keystore AES key.
 * Other non-secret preferences remain in private app SharedPreferences.
 */
class Store(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private val secretPrefs = app.getSharedPreferences("jarvis_secure", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) { sp.edit().putString("name", v).apply() }
    var area: String
        get() = sp.getString("area", "") ?: ""
        set(v) { sp.edit().putString("area", v).apply() }

    var apiKey: String
        get() {
            val encrypted = secretPrefs.getString("api_key", null)
            if (!encrypted.isNullOrBlank()) return decrypt(encrypted).orEmpty()
            // One-time migration from the previous plaintext preference.
            val legacy = sp.getString("apikey", null)
            if (!legacy.isNullOrBlank()) {
                setEncryptedApiKey(legacy)
                sp.edit().remove("apikey").apply()
                return legacy
            }
            return ""
        }
        set(v) {
            if (v.isBlank()) secretPrefs.edit().remove("api_key").apply()
            else setEncryptedApiKey(v)
            sp.edit().remove("apikey").apply()
        }

    private fun setEncryptedApiKey(value: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            secretPrefs.edit().putString("api_key", packed).apply()
        } catch (e: Exception) {
            // Do not fall back to storing a secret in plaintext.
            secretPrefs.edit().remove("api_key").apply()
        }
    }

    private fun decrypt(packed: String): String? {
        return try {
            val parts = packed.split(":", limit = 2)
            if (parts.size != 2) null else {
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            }
        } catch (e: Exception) { null }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    var elevenKey: String
        get() = secretPrefs.getString("el_key", null)?.let { decrypt(it) }.orEmpty()
        set(v) {
            if (v.isBlank()) secretPrefs.edit().remove("el_key").apply() else putEncrypted("el_key", v)
        }
    var voiceId: String
        get() = sp.getString("voice_id", DEFAULT_VOICE)?.ifBlank { DEFAULT_VOICE } ?: DEFAULT_VOICE
        set(v) { sp.edit().putString("voice_id", v.ifBlank { DEFAULT_VOICE }).apply() }

    private fun putEncrypted(prefKey: String, value: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            secretPrefs.edit().putString(prefKey, packed).apply()
        } catch (e: Exception) {
            secretPrefs.edit().remove(prefKey).apply()
        }
    }

    var hubStyle: Int
        get() = if (sp.getInt("hub_style", 3) == 4) 4 else 3
        set(v) { sp.edit().putInt("hub_style", if (v == 4) 4 else 3).apply() }

    var useSpotify: Boolean
        get() = sp.getBoolean("spotify", true)
        set(v) { sp.edit().putBoolean("spotify", v).apply() }
    var readAloud: Boolean
        get() = sp.getBoolean("read", false)
        set(v) { sp.edit().putBoolean("read", v).apply() }

    private fun arr(k: String): JSONArray = try { JSONArray(sp.getString(k, "[]")) } catch (e: Exception) { JSONArray() }
    private fun obj(k: String): JSONObject = try { JSONObject(sp.getString(k, "{}")) } catch (e: Exception) { JSONObject() }
    private fun list(k: String): List<String> { val a = arr(k); return (0 until a.length()).map { a.getString(it) } }
    private fun add(k: String, v: String) {
        val a = arr(k)
        a.put(v)
        // Bound local storage so repeated voice notes cannot grow forever.
        while (a.length() > 200) a.remove(0)
        sp.edit().putString(k, a.toString()).apply()
    }

    fun notes(): List<String> = list("notes")
    fun addNote(t: String) = add("notes", t)
    fun clearNotes() { sp.edit().remove("notes").apply() }
    fun facts(): List<String> = list("facts")
    fun addFact(t: String) = add("facts", t)
    fun skill(k: String): String? = obj("skills").optString(k, "").ifEmpty { null }
    fun setSkill(k: String, v: String) {
        val o = obj("skills")
        o.put(k, v)
        sp.edit().putString("skills", o.toString()).apply()
    }
    fun skillCount(): Int = obj("skills").length()
    fun bump(k: String) {
        val o = obj("stats")
        o.put(k, o.optInt(k, 0) + 1)
        sp.edit().putString("stats", o.toString()).apply()
    }
    fun topPlay(): String? {
        val o = obj("stats")
        var best: String? = null
        var n = 0
        for (k in o.keys()) {
            if (k.startsWith("play:") && o.getInt(k) > n) { best = k.substring(5); n = o.getInt(k) }
        }
        return best
    }
    fun cached(k: String): String? = obj("cache").optString(k, "").ifEmpty { null }
    fun cachePut(k: String, v: String) {
        val o = obj("cache")
        o.put(k, v)
        while (o.length() > 60) o.remove(o.keys().next())
        sp.edit().putString("cache", o.toString()).apply()
    }
    fun wipeLearned() {
        sp.edit().remove("facts").remove("skills").remove("stats").remove("cache").apply()
    }

    companion object {
        private const val KEY_ALIAS = "jarvis_api_key_aes_gcm"
        const val DEFAULT_VOICE = "gHuiaE9IqRmy6mCWKwS0"
    }
}
