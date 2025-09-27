package com.example.lilspeaker.features.download

import android.content.Context
import com.example.lilspeaker.core.logging.AppLogger
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink

class ModelDownloadManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun ensureModel(
        fileName: String,
        url: String,
        checksum: String
    ): File = withContext(dispatcher) {
        val destination = File(modelsDir, fileName)
        if (destination.exists() && verifyChecksum(destination, checksum)) {
            return@withContext destination
        }
        download(url, destination)
        if (!verifyChecksum(destination, checksum)) {
            destination.delete()
            throw IOException("Checksum mismatch for $fileName")
        }
        AppLogger.i(
            sourceClass = "ModelDownloadManager",
            function = "ensureModel",
            systemSection = "model_download",
            message = "Fetched $fileName"
        )
        destination
    }

    private fun verifyChecksum(file: File, expected: String): Boolean {
        if (expected.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun download(url: String, destination: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            destination.sink().buffer().use { sink ->
                sink.writeAll(body.source())
            }
        }
    }
}
