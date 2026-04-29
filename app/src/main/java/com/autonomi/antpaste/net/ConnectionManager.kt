package com.autonomi.antpaste.net

import android.content.SharedPreferences

/** Helpers for resolving P2P bootstrap peer configuration. */
object ConnectionManager {

    /** Autonomi production network bootstrap peers (ip:port format). */
    val DEFAULT_PEERS = listOf(
        "207.148.94.42:10000",
        "45.77.50.10:10000",
        "66.135.23.83:10000",
        "149.248.9.2:10000",
        "49.12.119.240:10000",
        "5.161.25.133:10000",
        "18.228.202.183:10000",
    )

    /** Default peers as a newline-separated string for the Settings field. */
    val DEFAULT_PEERS_TEXT: String = DEFAULT_PEERS.joinToString("\n")

    /**
     * Read bootstrap peer multiaddrs from Settings. Falls back to
     * [DEFAULT_PEERS] when the user hasn't configured any.
     */
    fun readBootstrapPeers(prefs: SharedPreferences): List<String> {
        val saved = prefs.getString("bootstrap_peers", null)
        val lines = if (saved.isNullOrBlank()) DEFAULT_PEERS else
            saved.lines().filter { it.isNotBlank() }.map { it.trim() }
        return lines.map { normalizeMultiaddr(it) }
    }

    /**
     * Upgrade a plain `ip:port` string to a QUIC multiaddr.
     *
     * Inputs already starting with `/` are returned unchanged. Inputs
     * that don't split cleanly into `host:port` are also passed through
     * untouched — the FFI's `MultiAddr::parse` will reject them at
     * connect time with a clear `InitializationFailed` error, so we
     * don't duplicate validation here.
     */
    fun normalizeMultiaddr(addr: String): String {
        if (addr.startsWith("/")) return addr
        val parts = addr.split(":")
        if (parts.size == 2) {
            return "/ip4/${parts[0]}/udp/${parts[1]}/quic"
        }
        return addr
    }
}
