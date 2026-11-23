package com.jarvis.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Private on-device GGUF model storage and llama.cpp inference. */
object LocalAi {
    const val MODEL_FILE_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    const val MODEL_SIZE_LABEL = "approximately 491 MB"
    private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"
    private val mutex = Mutex()
    private var loadedModel: LlamaModel? = null

    fun modelFile(context: Context): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILE_NAME)
    }

    fun hasModel(context: Context): Boolean {
        val file = modelFile(context)
        if (!file.isFile || file.length() < 100_000_000L) return false
        return try {
            val magic = ByteArray(4)
            file.inputStream().use { it.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF" }
        } catch (_: Exception) { false }
    }

    fun startDownload(context: Context): Long {
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("J.A.R.V.I.S. offline brain")
            .setDescription("Downloading Qwen 0.5B GGUF model ($MODEL_SIZE_LABEL)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, "models", MODEL_FILE_NAME)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }

    fun importModel(context: Context, source: Uri) {
        val target = modelFile(context)
        val partial = File(target.parentFile, MODEL_FILE_NAME + ".part")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                partial.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
            } ?: throw IllegalStateException("Could not open the selected model file.")
            val valid = partial.inputStream().use {
                val magic = ByteArray(4)
                it.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF"
            }
            if (!valid || partial.length() < 100_000_000L) {
                throw IllegalArgumentException("This is not a complete GGUF language model.")
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) throw IllegalStateException("Could not save the model.")
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    suspend fun answer(context: Context, prompt: String, systemPrompt: String): String = mutex.withLock {
        val file = modelFile(context)
        if (!hasModel(context)) throw IllegalStateException("No offline model is installed. Open MODELS to download or import one.")
        val model = loadedModel ?: Llama.loadModel(
            modelPath = file.absolutePath,
            config = LlamaConfig(
                contextSize = 2048,
                threads = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(2)),
                gpuLayers = 0
            )
        ).also { loadedModel = it }
        Llama.complete(model, prompt, systemPrompt = systemPrompt, maxTokens = 220)
            .text.trim().ifBlank { "The offline model returned an empty answer." }
    }

    suspend fun removeModel(context: Context) = mutex.withLock {
        loadedModel?.let { Llama.releaseModel(it) }
        loadedModel = null
        modelFile(context).delete()
    }
}
