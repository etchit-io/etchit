package com.autonomi.antpaste.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Streams progress events parsed from this app's own logcat.
 *
 * Android sandboxes logcat per-UID since Jelly Bean, so an app can read
 * its own `ant_ffi:I` tagged lines without any permission. The FFI's
 * tracing subscriber emits the interesting milestones (quotes collected,
 * payment sent, chunks stored, etc.); [eventFromLine] pattern-matches
 * those into short user-facing status strings.
 *
 * Collect [events] on any coroutine scope — cancelling the collecting
 * coroutine terminates the `logcat` subprocess through the `finally`
 * block.
 */
object ProgressTail {

    /**
     * Emit progress strings as they arrive. The upstream runs on
     * [Dispatchers.IO]; the collector runs wherever the caller's scope
     * is dispatching. Silent if `logcat` cannot be spawned (old Android,
     * sandbox changes, etc.) — emits nothing rather than throwing.
     */
    fun events(): Flow<String> = flow {
        val proc = try {
            Runtime.getRuntime().exec(
                arrayOf("logcat", "-v", "raw", "-T", "1", "ant_ffi:I", "*:S")
            )
        } catch (e: Exception) {
            Log.w("ant-paste", "logcat stream failed to start", e)
            return@flow
        }
        try {
            proc.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val msg = eventFromLine(line) ?: continue
                    emit(msg)
                }
            }
        } catch (_: Exception) {
            // Stream closed — normal on job cancel
        } finally {
            runCatching { proc.destroy() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Pattern-match a single `ant_ffi` log line into a short status
     * string. Returns `null` when the line isn't one of the milestones
     * we care about. Pure function — unit testable.
     */
    fun eventFromLine(line: String): String? {
        return when {
            // "Data encrypted into N chunks" fires the instant self-encryption
            // finishes (microseconds). Immediately after, quote collection starts
            // and that's what the user actually waits on — so label both phases
            // as "Collecting quotes" instead of misleading "Encrypting content".
            "Data encrypted into" in line -> "Collecting quotes"
            "Collected" in line && "quotes for address" in line -> "Collecting quotes"
            "Batch payment for" in line -> "Paying for storage"
            "Batch payment succeeded" in line -> "Payment confirmed"
            "stored on" in line && "majority reached" in line -> "Storing chunks"
            "Data uploaded" in line -> "Storing data map"
            "Storing DataMap" in line -> "Storing data map"
            "Token spend approved" in line -> "Wallet approved"
            "Data downloaded and decrypted" in line -> "Fetched from network"
            else -> null
        }
    }
}
