package com.autonomi.antpaste

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a list of etched pastes with title and timestamp in
 * SharedPreferences as a JSON array. Supports both public (address)
 * and private (dataMapId referencing PrivateDataStore) entries.
 * Entries are stored newest-first.
 */
class EtchHistory(private val prefs: SharedPreferences) {

    data class Entry(
        val address: String,
        val title: String,
        val timestampMs: Long,
        val isPrivate: Boolean = false,
        /** For private entries, the UUID key into [PrivateDataStore]. */
        val dataMapId: String? = null,
    )

    fun load(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Entry(
                    address = obj.getString("a"),
                    title = obj.optString("t", ""),
                    timestampMs = obj.getLong("ts"),
                    isPrivate = obj.optBoolean("p", false),
                    dataMapId = if (obj.has("dmid")) obj.getString("dmid") else null,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(address: String, title: String) {
        val entries = load().toMutableList()
        entries.add(0, Entry(address, title, System.currentTimeMillis()))
        save(entries)
    }

    fun addPrivate(title: String, dataMapId: String) {
        val entries = load().toMutableList()
        entries.add(0, Entry(
            address = "",
            title = title,
            timestampMs = System.currentTimeMillis(),
            isPrivate = true,
            dataMapId = dataMapId,
        ))
        save(entries)
    }

    fun remove(entry: Entry) {
        save(load().filter {
            if (it.isPrivate) it.dataMapId != entry.dataMapId
            else it.address != entry.address
        })
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().apply {
                put("a", e.address)
                put("t", e.title)
                put("ts", e.timestampMs)
                if (e.isPrivate) {
                    put("p", true)
                    put("dmid", e.dataMapId)
                }
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "etch_history"
    }
}
