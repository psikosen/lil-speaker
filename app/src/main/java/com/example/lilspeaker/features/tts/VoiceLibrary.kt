package com.example.lilspeaker.features.tts

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

class VoiceLibrary {
    fun load(file: File): List<VoiceProfile> {
        if (!file.exists()) return emptyList()
        val voices = mutableListOf<VoiceProfile>()
        file.inputStream().buffered().use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".npy")) {
                        val id = entry.name.removeSuffix(".npy")
                        val npy = NpyReader(zip)
                        val data = npy.readFloatArray()
                        val profile = VoiceProfile(
                            id = id,
                            displayName = friendlyName(id),
                            embedding = data
                        )
                        voices += profile
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return voices.sortedBy { it.displayName }
    }

    private fun friendlyName(id: String): String {
        return id.replace("_", " ")
            .replace('-', ' ')
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    private class NpyReader(stream: InputStream) {
        private val header: String
        private val dtype: String
        private val shape: List<Int>
        private val source: InputStream

        init {
            val magic = ByteArray(6)
            stream.readFully(magic)
            require(String(magic) == "\u0093NUMPY") { "Invalid npy header" }
            val major = stream.read()
            val minor = stream.read()
            require(major == 1 || major == 2) { "Unsupported npy version" }
            val headerLenBytes = ByteArray(2)
            stream.readFully(headerLenBytes)
            val headerLen = ByteBuffer.wrap(headerLenBytes).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val headerBytes = ByteArray(headerLen)
            stream.readFully(headerBytes)
            header = String(headerBytes)
            dtype = Regex("'descr': '([^']+)'")
                .find(header)
                ?.groupValues
                ?.get(1)
                ?: throw IllegalArgumentException("dtype missing")
            shape = Regex("'shape': \(([^\)]*)\)")
                .find(header)
                ?.groupValues
                ?.get(1)
                ?.split(",")
                ?.mapNotNull { token ->
                    val trimmed = token.trim()
                    if (trimmed.isEmpty()) null else trimmed.removeSuffix(",").trim().toIntOrNull()
                }
                ?: emptyList()
            source = stream
        }

        fun readFloatArray(): FloatArray {
            require(dtype.contains("<f4")) { "Only float32 voices supported" }
            val size = shape.fold(1) { acc, value -> acc * value }
            val bytes = ByteArray(size * 4)
            source.readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val floats = FloatArray(size)
            buffer.asFloatBuffer().get(floats)
            return floats
        }
    }
}

private fun InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val read = this.read(buffer, offset, buffer.size - offset)
        if (read == -1) throw IllegalStateException("Unexpected EOF")
        offset += read
    }
}
