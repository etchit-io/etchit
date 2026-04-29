package com.autonomi.antpaste

/**
 * Pure utility functions for envelope encoding, JSON escaping, and formatting.
 * Extracted from MainActivity for testability.
 */
object PasteUtils {

    /**
     * Build the JSON envelope that wraps user content + metadata before
     * self-encryption. Deliberately deterministic for a given (content,
     * title) pair: the on-network address is content-addressed, so a
     * variable timestamp here would produce a fresh chunk tree on every
     * etch and break the network's already-stored deduplication. Etch
     * creation time is tracked locally via [EtchHistory], not embedded
     * in the on-network bytes.
     */
    fun buildEnvelope(content: String, title: String): String {
        return """{"v":1,"meta":{"title":"${escapeJson(title)}","lang":""},"content":"${escapeJson(content)}"}"""
    }

    fun parseEnvelope(raw: String): Pair<String, String> {
        return try {
            if (raw.contains("\"content\"") && raw.contains("\"meta\"")) {
                val contentStart = raw.indexOf("\"content\":\"") + 11
                val contentEnd = findClosingQuote(raw, contentStart)
                val content = unescapeJson(raw.substring(contentStart, contentEnd))
                val titleMatch = Regex("""\"title\":\"((?:[^"\\]|\\.)*)\"""").find(raw)
                val title = titleMatch?.groupValues?.get(1)?.let { unescapeJson(it) } ?: ""
                Pair(title, content)
            } else {
                Pair("", raw)
            }
        } catch (e: Exception) {
            Pair("", raw)
        }
    }

    /** Find the closing quote of a JSON string value, skipping escaped quotes. */
    private fun findClosingQuote(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            if (s[i] == '\\') {
                i += 2 // skip escaped character
            } else if (s[i] == '"') {
                return i
            } else {
                i++
            }
        }
        // Fallback: use the old lastIndexOf approach
        return s.lastIndexOf("\"}")
    }

    fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    fun unescapeJson(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (i + 1 < s.length && s[i] == '\\') {
                when (s[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n'  -> { sb.append('\n'); i += 2 }
                    'r'  -> { sb.append('\r'); i += 2 }
                    't'  -> { sb.append('\t'); i += 2 }
                    '"'  -> { sb.append('"');  i += 2 }
                    else -> { sb.append(s[i]); i++ }
                }
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    fun formatSize(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }

    fun isValidAddress(address: String): Boolean =
        address.length == 64 && address.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
