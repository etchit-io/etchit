package com.autonomi.antpaste

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stores private data maps in EncryptedSharedPreferences. Maintains its
 * own index so entries survive etch history clears. The data maps are
 * the only way to retrieve private etches — losing them means the data
 * is gone forever.
 */
class PrivateDataStore(private val encryptedPrefs: SharedPreferences) {

    data class Entry(
        val id: String,
        val dataMapHex: String,
        val title: String,
        val timestampMs: Long,
    )

    fun save(title: String, dataMapHex: String): String {
        val id = UUID.randomUUID().toString()
        // Store the data map itself
        encryptedPrefs.edit().putString(dataKey(id), dataMapHex).apply()
        // Add to the index
        val index = loadIndex().toMutableList()
        index.add(0, IndexEntry(id, title, System.currentTimeMillis()))
        saveIndex(index)
        return id
    }

    fun get(id: String): Entry? {
        val dataMap = encryptedPrefs.getString(dataKey(id), null) ?: return null
        val idx = loadIndex().find { it.id == id } ?: return null
        return Entry(id, dataMap, idx.title, idx.timestampMs)
    }

    fun listAll(): List<Entry> {
        return loadIndex().mapNotNull { idx ->
            val dm = encryptedPrefs.getString(dataKey(idx.id), null) ?: return@mapNotNull null
            Entry(idx.id, dm, idx.title, idx.timestampMs)
        }
    }

    fun remove(id: String) {
        encryptedPrefs.edit().remove(dataKey(id)).apply()
        saveIndex(loadIndex().filter { it.id != id })
    }

    private fun dataKey(id: String) = "pdm_$id"

    private data class IndexEntry(val id: String, val title: String, val timestampMs: Long)

    private fun loadIndex(): List<IndexEntry> {
        val raw = encryptedPrefs.getString(INDEX_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                IndexEntry(obj.getString("id"), obj.optString("t", ""), obj.getLong("ts"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveIndex(entries: List<IndexEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("t", e.title)
                put("ts", e.timestampMs)
            })
        }
        encryptedPrefs.edit().putString(INDEX_KEY, arr.toString()).apply()
    }

    /** Serialize all entries to JSON for backup. */
    fun exportAll(): ByteArray {
        val arr = JSONArray()
        for (entry in listAll()) {
            arr.put(JSONObject().apply {
                put("dm", entry.dataMapHex)
                put("t", entry.title)
                put("ts", entry.timestampMs)
            })
        }
        return arr.toString().toByteArray(Charsets.UTF_8)
    }

    /** Import entries from a backup JSON, skipping duplicates. */
    fun importAll(json: ByteArray): Int {
        val arr = try { JSONArray(json.toString(Charsets.UTF_8)) } catch (_: Exception) { return 0 }
        val existing = listAll().map { it.dataMapHex }.toSet()
        var imported = 0
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val dm = obj.getString("dm")
            if (dm in existing) continue
            save(obj.optString("t", ""), dm)
            imported++
        }
        return imported
    }

    companion object {
        private const val INDEX_KEY = "pdm_index"
    }
}
