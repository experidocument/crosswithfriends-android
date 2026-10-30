package com.crosswithfriends.app

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.io.File

/**
 * Manages local persistence and Google Drive/Doc export of the Crossword Learning Database.
 */
class JournalManager(private val context: Context) {

    private val storageFile = File(context.filesDir, "crossword_learning_database.json")

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
        // If entry with same word already exists, replace or update it
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

    /**
     * Exports all saved entries categorized according to the Google Doc template structure.
     */
    fun exportFullJournalForGoogleDocs(): String {
        val entries = getAllEntries()
        if (entries.isEmpty()) return "Crossword Learning Database\n(No entries saved yet)"

        val sb = StringBuilder()
        sb.append("Crossword Learning Database\n\n")

        val grouped = entries.groupBy { it.category }
        grouped.forEach { (category, categoryEntries) ->
            sb.append(category).append("\n")
            categoryEntries.forEach { entry ->
                sb.append(entry.toGoogleDocSnippet())
            }
            sb.append("\n")
        }
        return sb.toString()
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
