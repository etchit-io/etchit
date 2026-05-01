package com.autonomi.antpaste.library

// Replays a sequence of decrypted on-chain library batches into the canonical
// per-addr state per §11 of docs/library-format-v1.md. Pure: no I/O, no
// time, no allocation beyond the result map.
object LibraryReplay {

    fun apply(events: List<TxEvent>): Map<String, LibraryEntry> {
        val sorted = events.sortedWith(compareBy({ it.blockNum }, { it.txIndex }))
        val state = LinkedHashMap<String, LibraryEntry>()
        for (event in sorted) {
            val entries = LibraryPayload.decode(event.payloadJson) ?: continue
            for (e in entries) applyEntry(state, e)
        }
        return state
    }

    private fun applyEntry(state: MutableMap<String, LibraryEntry>, e: WireEntry) {
        when (e.action) {
            WireEntry.ACTION_ADD -> {
                state[e.addr] = LibraryEntry(addr = e.addr, title = e.title, ts = e.ts, isBookmark = false, isHidden = false)
            }
            WireEntry.ACTION_BOOKMARK -> {
                state[e.addr] = LibraryEntry(addr = e.addr, title = e.title, ts = e.ts, isBookmark = true, isHidden = false)
            }
            WireEntry.ACTION_HIDE -> {
                val existing = state[e.addr]
                state[e.addr] = if (existing != null) existing.copy(isHidden = true)
                else LibraryEntry(addr = e.addr, title = "", ts = 0L, isBookmark = false, isHidden = true)
            }
        }
    }
}
