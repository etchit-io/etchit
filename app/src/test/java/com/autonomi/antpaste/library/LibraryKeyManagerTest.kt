package com.autonomi.antpaste.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class LibraryKeyManagerTest {

    // The SIGN_MESSAGE bytes are normative (spec §4.1). Changing them silently
    // invalidates every existing on-chain library — this test guards against drift.
    @Test
    fun signMessage_matchesSpecBytes() {
        val bytes = LibraryKeyManager.SIGN_MESSAGE.toByteArray(Charsets.UTF_8)
        assertEquals(137, bytes.size)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = sha.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("5163bfeff8f6fa44563730938abbe6a23b35aa890868754e22ff15af7666c0d5", hex)
    }

    @Test
    fun signMessage_endsWithoutNewline() {
        val bytes = LibraryKeyManager.SIGN_MESSAGE.toByteArray(Charsets.UTF_8)
        assertEquals('.'.code.toByte(), bytes[bytes.size - 1])
    }
}
