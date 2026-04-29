package com.autonomi.antpaste

import org.junit.Assert.*
import org.junit.Test

class PasteUtilsTest {

    // ── escapeJson ────────────────────────────────────────────────

    @Test
    fun escapeJson_plain_text_unchanged() {
        assertEquals("hello world", PasteUtils.escapeJson("hello world"))
    }

    @Test
    fun escapeJson_escapes_quotes() {
        assertEquals("""say \"hello\"""", PasteUtils.escapeJson("""say "hello""""))
    }

    @Test
    fun escapeJson_escapes_newlines_and_tabs() {
        assertEquals("line1\\nline2\\ttab", PasteUtils.escapeJson("line1\nline2\ttab"))
    }

    @Test
    fun escapeJson_escapes_backslashes_first() {
        assertEquals("a\\\\b", PasteUtils.escapeJson("a\\b"))
    }

    @Test
    fun escapeJson_escapes_carriage_return() {
        assertEquals("a\\r\\nb", PasteUtils.escapeJson("a\r\nb"))
    }

    @Test
    fun escapeJson_empty_string() {
        assertEquals("", PasteUtils.escapeJson(""))
    }

    // ── unescapeJson ──────────────────────────────────────────────

    @Test
    fun unescapeJson_restores_newlines() {
        assertEquals("a\nb", PasteUtils.unescapeJson("a\\nb"))
    }

    @Test
    fun unescapeJson_restores_tabs() {
        assertEquals("a\tb", PasteUtils.unescapeJson("a\\tb"))
    }

    @Test
    fun unescapeJson_restores_quotes() {
        assertEquals("say \"hi\"", PasteUtils.unescapeJson("say \\\"hi\\\""))
    }

    @Test
    fun unescapeJson_restores_backslashes() {
        assertEquals("a\\b", PasteUtils.unescapeJson("a\\\\b"))
    }

    @Test
    fun escapeJson_and_unescapeJson_roundtrip() {
        val originals = listOf(
            "simple text",
            "line1\nline2\nline3",
            "path\\to\\file",
            "She said \"hello\"",
            "tabs\there\tand\there",
            "mixed\n\t\"escape\\chars\"",
            "",
        )
        for (original in originals) {
            val escaped = PasteUtils.escapeJson(original)
            val restored = PasteUtils.unescapeJson(escaped)
            assertEquals("Roundtrip failed for: $original", original, restored)
        }
    }

    // ── buildEnvelope ─────────────────────────────────────────────

    @Test
    fun buildEnvelope_basic() {
        val envelope = PasteUtils.buildEnvelope("hello", "My Title")
        assertTrue(envelope.contains("\"v\":1"))
        assertTrue(envelope.contains("\"content\":\"hello\""))
        assertTrue(envelope.contains("\"title\":\"My Title\""))
        assertFalse(envelope.contains("created_at"))  // deterministic — no timestamp
    }

    @Test
    fun buildEnvelope_is_deterministic() {
        // Critical for network-side dedup: identical (content, title)
        // must produce byte-identical envelopes so the chunk addresses
        // match across attempts.
        val a = PasteUtils.buildEnvelope("hello", "My Title")
        val b = PasteUtils.buildEnvelope("hello", "My Title")
        assertEquals(a, b)
    }

    @Test
    fun buildEnvelope_escapes_content() {
        val envelope = PasteUtils.buildEnvelope("line1\nline2", "test")
        assertTrue(envelope.contains("\"content\":\"line1\\nline2\""))
        assertFalse(envelope.contains("\n"))  // actual newline should not be in output
    }

    @Test
    fun buildEnvelope_escapes_title_with_quotes() {
        val envelope = PasteUtils.buildEnvelope("content", "say \"hi\"")
        assertTrue(envelope.contains("\"title\":\"say \\\"hi\\\"\""))
    }

    @Test
    fun buildEnvelope_empty_content_and_title() {
        val envelope = PasteUtils.buildEnvelope("", "")
        assertTrue(envelope.contains("\"content\":\"\""))
        assertTrue(envelope.contains("\"title\":\"\""))
    }

    // ── parseEnvelope ─────────────────────────────────────────────

    @Test
    fun parseEnvelope_valid_envelope() {
        val envelope = """{"v":1,"meta":{"title":"Test","lang":"","created_at":0},"content":"hello world"}"""
        val (title, content) = PasteUtils.parseEnvelope(envelope)
        assertEquals("Test", title)
        assertEquals("hello world", content)
    }

    @Test
    fun parseEnvelope_with_escaped_content() {
        val envelope = """{"v":1,"meta":{"title":"","lang":"","created_at":0},"content":"line1\nline2"}"""
        val (_, content) = PasteUtils.parseEnvelope(envelope)
        assertEquals("line1\nline2", content)
    }

    @Test
    fun parseEnvelope_raw_text_passthrough() {
        val raw = "just plain text, no JSON"
        val (title, content) = PasteUtils.parseEnvelope(raw)
        assertEquals("", title)
        assertEquals(raw, content)
    }

    @Test
    fun parseEnvelope_malformed_json_returns_raw() {
        val raw = "{broken json"
        val (title, content) = PasteUtils.parseEnvelope(raw)
        assertEquals("", title)
        assertEquals(raw, content)
    }

    @Test
    fun buildEnvelope_then_parseEnvelope_roundtrip() {
        val originalContent = "Hello\nWorld\twith \"quotes\" and \\backslashes"
        val originalTitle = "My \"Special\" Paste"
        val envelope = PasteUtils.buildEnvelope(originalContent, originalTitle)
        val (parsedTitle, parsedContent) = PasteUtils.parseEnvelope(envelope)
        assertEquals(originalTitle, parsedTitle)
        assertEquals(originalContent, parsedContent)
    }

    @Test
    fun parseEnvelope_legacy_with_created_at() {
        // Older etches on the network include "created_at" in the meta
        // block. The current parser ignores it; verify back-compat so
        // existing addresses keep decoding cleanly.
        val envelope = """{"v":1,"meta":{"title":"Old","lang":"","created_at":1700000000.0},"content":"hi"}"""
        val (title, content) = PasteUtils.parseEnvelope(envelope)
        assertEquals("Old", title)
        assertEquals("hi", content)
    }

    // ── formatSize ────────────────────────────────────────────────

    @Test
    fun formatSize_bytes() {
        assertEquals("0 B", PasteUtils.formatSize(0))
        assertEquals("512 B", PasteUtils.formatSize(512))
        assertEquals("1023 B", PasteUtils.formatSize(1023))
    }

    @Test
    fun formatSize_kilobytes() {
        assertEquals("1.0 KB", PasteUtils.formatSize(1024))
        assertEquals("1.5 KB", PasteUtils.formatSize(1536))
        assertEquals("10.0 KB", PasteUtils.formatSize(10240))
    }

    @Test
    fun formatSize_megabytes() {
        assertEquals("1.0 MB", PasteUtils.formatSize(1024 * 1024))
        assertEquals("2.5 MB", PasteUtils.formatSize((2.5 * 1024 * 1024).toInt()))
    }

    // ── isValidAddress ────────────────────────────────────────────

    @Test
    fun isValidAddress_valid_64_hex() {
        val addr = "a".repeat(64)
        assertTrue(PasteUtils.isValidAddress(addr))
    }

    @Test
    fun isValidAddress_mixed_hex_chars() {
        val addr = "0123456789abcdefABCDEF" + "0".repeat(42)
        assertTrue(PasteUtils.isValidAddress(addr))
    }

    @Test
    fun isValidAddress_too_short() {
        assertFalse(PasteUtils.isValidAddress("abc123"))
    }

    @Test
    fun isValidAddress_too_long() {
        assertFalse(PasteUtils.isValidAddress("a".repeat(65)))
    }

    @Test
    fun isValidAddress_non_hex_chars() {
        val addr = "g" + "0".repeat(63)
        assertFalse(PasteUtils.isValidAddress(addr))
    }

    @Test
    fun isValidAddress_empty() {
        assertFalse(PasteUtils.isValidAddress(""))
    }
}
