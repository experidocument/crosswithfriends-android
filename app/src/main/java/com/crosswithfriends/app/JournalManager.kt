package com.crosswithfriends.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages local persistence and direct Google Doc sync of the Crossword Learning Database.
 */
class JournalManager(private val context: Context) {

    private val storageFile = File(context.filesDir, "crossword_learning_database.json")
    private val prefs = context.getSharedPreferences("CrosswordJournalPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WEBHOOK_URL = "google_doc_webhook_url"
    }

    fun getGoogleDocWebhookUrl(): String {
        return prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
    }

    fun setGoogleDocWebhookUrl(url: String) {
        prefs.edit().putString(KEY_WEBHOOK_URL, url.trim()).apply()
    }

    fun getAllEntries(): List<JournalEntry> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val content = storageFile.readText()
            val jsonArray = JSONArray(content)
            val list = mutableListOf<JournalEntry>()
            for (i in 0 until jsonArray.length()) {
                list.add(JournalEntry.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEntry(entry: JournalEntry) {
        val currentEntries = getAllEntries().toMutableList()
        val existingIndex = currentEntries.indexOfFirst { it.word.equals(entry.word, ignoreCase = true) }
        if (existingIndex >= 0) {
            currentEntries[existingIndex] = entry
        } else {
            currentEntries.add(0, entry)
        }
        persistEntries(currentEntries)
    }

    fun deleteEntry(id: String) {
        val currentEntries = getAllEntries().toMutableList()
        currentEntries.removeAll { it.id == id }
        persistEntries(currentEntries)
    }

    private fun persistEntries(entries: List<JournalEntry>) {
        try {
            val jsonArray = JSONArray()
            entries.forEach { jsonArray.put(it.toJsonObject()) }
            storageFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getUnsyncedEntries(): List<JournalEntry> {
        return getAllEntries().filter { !it.isSynced }
    }

    fun markEntriesAsSynced(entryIds: List<String>) {
        val currentEntries = getAllEntries().toMutableList()
        var modified = false
        currentEntries.forEachIndexed { index, entry ->
            if (entryIds.contains(entry.id) && !entry.isSynced) {
                currentEntries[index] = entry.copy(isSynced = true)
                modified = true
            }
        }
        if (modified) {
            persistEntries(currentEntries)
        }
    }

    /**
     * Formats entries categorized according to the Google Doc template structure.
     * Does NOT prepend 'Crossword Learning Database' title, syncing only the journal items.
     */
    fun formatEntriesForSync(entries: List<JournalEntry>): String {
        if (entries.isEmpty()) return ""

        val sb = StringBuilder()
        val grouped = entries.groupBy { it.category }
        grouped.forEach { (category, categoryEntries) ->
            sb.append(category).append("\n")
            categoryEntries.forEach { entry ->
                sb.append(entry.toGoogleDocSnippet())
            }
            sb.append("\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Exports all saved entries without the title heading.
     */
    fun exportFullJournalForGoogleDocs(): String {
        val entries = getAllEntries()
        if (entries.isEmpty()) return "(No entries saved yet)"
        return formatEntriesForSync(entries)
    }

    /**
     * Custom Categories Management
     */
    fun getCustomCategories(): List<String> {
        val raw = prefs.getString("custom_categories_v2", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;;").filter { it.isNotBlank() }
    }

    fun addCustomCategory(category: String) {
        val current = getCustomCategories().toMutableList()
        val cleaned = category.trim()
        if (cleaned.isNotBlank() && !current.contains(cleaned)) {
            current.add(cleaned)
            prefs.edit().putString("custom_categories_v2", current.joinToString(";;;")).apply()
        }
    }

    /**
     * Sends formatted text directly to the user's Google Doc via Google Apps Script Webhook.
     */
    suspend fun syncDirectToGoogleDoc(text: String, webhookUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = URL(webhookUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")

            val payload = JSONObject().apply {
                put("text", text)
                put("timestamp", System.currentTimeMillis())
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..399) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Launches the native Android Share Sheet targeting Google Drive, Google Docs, Gmail, etc.
     */
    fun shareToGoogleDriveOrDocs(content: String, title: String = "Crossword Learning Database") {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Sync to Google Drive / Docs")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
