package com.autonomi.antpaste

/**
 * Detects content type from raw bytes using magic byte signatures.
 * Used by the fetch flow to decide how to display network data.
 */
object ContentDetector {

    enum class ContentType {
        ETCH_ENVELOPE,  // Our JSON envelope format
        IMAGE,          // PNG, JPEG, GIF, WEBP, BMP
        TEXT,           // Valid UTF-8 text (not our envelope)
        BACKUP,         // Encrypted etchit backup
        BINARY,         // Everything else — save to file
    }

    data class Result(
        val type: ContentType,
        val mimeType: String,
        val extension: String,
    )

    fun detect(data: ByteArray): Result {
        // Check for encrypted backup first
        if (BackupCrypto.isBackup(data)) {
            return Result(ContentType.BACKUP, "application/octet-stream", "bin")
        }

        // Try etch envelope first
        if (looksLikeEnvelope(data)) {
            return Result(ContentType.ETCH_ENVELOPE, "application/json", "json")
        }

        // Check image magic bytes
        imageType(data)?.let { return it }

        // Check video/audio
        videoType(data)?.let { return it }

        // Check PDF
        if (data.size >= 4 && data.startsWith("%PDF")) {
            return Result(ContentType.BINARY, "application/pdf", "pdf")
        }

        // Try as UTF-8 text
        if (isValidUtf8(data)) {
            return Result(ContentType.TEXT, "text/plain", "txt")
        }

        return Result(ContentType.BINARY, "application/octet-stream", "bin")
    }

    private fun looksLikeEnvelope(data: ByteArray): Boolean {
        if (data.size > 10_000_000) return false // Don't try to parse huge data as text
        val str = try { data.toString(Charsets.UTF_8) } catch (_: Exception) { return false }
        return str.contains("\"content\"") && str.contains("\"meta\"")
    }

    private fun imageType(data: ByteArray): Result? {
        if (data.size < 4) return null
        return when {
            // PNG: 89 50 4E 47
            data[0] == 0x89.toByte() && data.startsWith("PNG", 1) ->
                Result(ContentType.IMAGE, "image/png", "png")
            // JPEG: FF D8 FF
            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() ->
                Result(ContentType.IMAGE, "image/jpeg", "jpg")
            // GIF: GIF8
            data.startsWith("GIF8") ->
                Result(ContentType.IMAGE, "image/gif", "gif")
            // WEBP: RIFF....WEBP
            data.size >= 12 && data.startsWith("RIFF") && data.startsWith("WEBP", 8) ->
                Result(ContentType.IMAGE, "image/webp", "webp")
            // BMP: BM
            data[0] == 0x42.toByte() && data[1] == 0x4D.toByte() ->
                Result(ContentType.IMAGE, "image/bmp", "bmp")
            else -> null
        }
    }

    private fun videoType(data: ByteArray): Result? {
        if (data.size < 12) return null
        return when {
            // MP4/M4V: ....ftyp
            data.startsWith("ftyp", 4) ->
                Result(ContentType.BINARY, "video/mp4", "mp4")
            // WEBM/MKV: 1A 45 DF A3
            data[0] == 0x1A.toByte() && data[1] == 0x45.toByte() &&
                data[2] == 0xDF.toByte() && data[3] == 0xA3.toByte() ->
                Result(ContentType.BINARY, "video/webm", "webm")
            // MP3: ID3 or FF FB
            data.startsWith("ID3") ||
                (data[0] == 0xFF.toByte() && data[1] == 0xFB.toByte()) ->
                Result(ContentType.BINARY, "audio/mpeg", "mp3")
            else -> null
        }
    }

    private fun isValidUtf8(data: ByteArray): Boolean {
        if (data.size > 10_000_000) return false // Don't scan huge files
        var i = 0
        var controlCount = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            when {
                b <= 0x7F -> {
                    // ASCII — count control chars (except common ones)
                    if (b < 0x20 && b != 0x0A && b != 0x0D && b != 0x09) controlCount++
                    i++
                }
                b in 0xC0..0xDF -> { if (i + 1 >= data.size) return false; i += 2 }
                b in 0xE0..0xEF -> { if (i + 2 >= data.size) return false; i += 3 }
                b in 0xF0..0xF7 -> { if (i + 3 >= data.size) return false; i += 4 }
                else -> return false
            }
        }
        // If more than 10% control chars, probably binary
        return data.isNotEmpty() && controlCount.toFloat() / data.size < 0.1f
    }

    private fun ByteArray.startsWith(str: String, offset: Int = 0): Boolean {
        if (offset + str.length > size) return false
        return str.indices.all { this[offset + it] == str[it].code.toByte() }
    }
}
