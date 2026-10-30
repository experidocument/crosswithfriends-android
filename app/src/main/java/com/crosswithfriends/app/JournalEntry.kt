package com.crosswithfriends.app

import org.json.JSONObject
import java.util.UUID

/**
 * Represents an entry in the Crossword Learning Database, matching the format
 * of the user's study journal.
 */
data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val word: String,
    val category: String,
    val clue: String = "",
    val trivia: String = "",
    val etymology: String = "",
    val example: String = "",
    val sourceUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("word", word)
            put("category", category)
            put("clue", clue)
            put("trivia", trivia)
            put("etymology", etymology)
            put("example", example)
            put("sourceUrl", sourceUrl)
            put("timestamp", timestamp)
        }
    }

    /**
     * Formats this entry according to the exact Google Doc template.
     */
    fun toGoogleDocSnippet(): String {
        val sb = StringBuilder()
        sb.append("* ").append(word.uppercase()).append("\n")
        if (clue.isNotBlank()) {
            sb.append("* Clue: ").append(clue).append("\n")
        }
        if (trivia.isNotBlank()) {
            sb.append("* Detailed Trivia: ").append(trivia).append("\n")
        }
        if (etymology.isNotBlank()) {
            sb.append("* Etymology: ").append(etymology).append("\n")
        }
        if (example.isNotBlank()) {
            sb.append("* Example: \"").append(example).append("\"\n")
        }
        if (sourceUrl.isNotBlank()) {
            sb.append("* Visual/Link: ").append(sourceUrl).append("\n")
        }
        sb.append("\n")
        return sb.toString()
    }

    companion object {
        fun fromJsonObject(json: JSONObject): JournalEntry {
            return JournalEntry(
                id = json.optString("id", UUID.randomUUID().toString()),
                word = json.optString("word", ""),
                category = json.optString("category", "💡 General Trivia"),
                clue = json.optString("clue", ""),
                trivia = json.optString("trivia", ""),
                etymology = json.optString("etymology", ""),
                example = json.optString("example", ""),
                sourceUrl = json.optString("sourceUrl", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
