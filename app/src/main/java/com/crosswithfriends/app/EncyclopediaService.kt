package com.crosswithfriends.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class EncyclopediaResult(
    val word: String,
    val category: String,
    val summary: String,
    val trivia: String,
    val etymology: String,
    val example: String,
    val sourceUrl: String
)

object EncyclopediaService {

    suspend fun lookup(query: String): Result<EncyclopediaResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Query cannot be empty"))
        }

        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            
            // 1. Fetch from Wikipedia REST Summary API
            val wikiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val wikiJson = fetchJson(wikiUrl)

            var wordTitle = trimmed
            var triviaText = ""
            var sourceUrl = "https://en.wikipedia.org/wiki/$encoded"
            var description = ""

            if (wikiJson != null) {
                wordTitle = wikiJson.optString("title", trimmed)
                triviaText = wikiJson.optString("extract", "")
                description = wikiJson.optString("description", "")
                sourceUrl = wikiJson.optJSONObject("content_urls")
                    ?.optJSONObject("desktop")
                    ?.optString("page", sourceUrl) ?: sourceUrl
            }

            // 2. Fetch from Dictionary API for definitions, etymology, and example sentences
            val dictUrl = "https://api.dictionaryapi.dev/api/v2/entries/en/$encoded"
            val dictJson = fetchJsonArray(dictUrl)

            var etymologyText = ""
            var exampleText = ""
            var definitionText = ""

            if (dictJson != null && dictJson.length() > 0) {
                val firstEntry = dictJson.getJSONObject(0)
                etymologyText = firstEntry.optString("origin", "")
                val meanings = firstEntry.optJSONArray("meanings")
                if (meanings != null && meanings.length() > 0) {
                    val firstMeaning = meanings.getJSONObject(0)
                    val definitions = firstMeaning.optJSONArray("definitions")
                    if (definitions != null && definitions.length() > 0) {
                        val firstDef = definitions.getJSONObject(0)
                        definitionText = firstDef.optString("definition", "")
                        exampleText = firstDef.optString("example", "")
                    }
                }
            }

            // Synthesize the final fields
            val finalTrivia = when {
                triviaText.isNotBlank() -> triviaText
                definitionText.isNotBlank() -> definitionText
                else -> "No immediate encyclopedia summary found. Tap 'Search on Google' for full web results."
            }

            val finalCategory = detectCategory(wordTitle, description, finalTrivia)

            val result = EncyclopediaResult(
                word = wordTitle,
                category = finalCategory,
                summary = description,
                trivia = finalTrivia,
                etymology = etymologyText,
                example = exampleText,
                sourceUrl = sourceUrl
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun detectCategory(word: String, description: String, text: String): String {
        val combined = "$word $description $text".lowercase()
        return when {
            combined.contains("island") || combined.contains("city") || combined.contains("country") ||
            combined.contains("river") || combined.contains("mountain") || combined.contains("capital") ||
            combined.contains("lake") || combined.contains("state") || combined.contains("region") -> "🌍 Geography & Places"

            combined.contains("painter") || combined.contains("actor") || combined.contains("actress") ||
            combined.contains("film") || combined.contains("director") || combined.contains("music") ||
            combined.contains("artist") || combined.contains("novel") || combined.contains("author") ||
            combined.contains("sculptor") || combined.contains("theatre") -> "🎨 Arts & Entertainment"

            combined.contains("noodle") || combined.contains("dish") || combined.contains("food") ||
            combined.contains("cuisine") || combined.contains("cocktail") || combined.contains("drink") ||
            combined.contains("recipe") || combined.contains("wine") || combined.contains("soup") -> "🍜 Food & Drink"

            combined.contains("chemistry") || combined.contains("compound") || combined.contains("physics") ||
            combined.contains("biology") || combined.contains("species") || combined.contains("organic") ||
            combined.contains("anatomy") || combined.contains("element") || combined.contains("formula") -> "🧪 Science & Miscellaneous"

            combined.contains("slang") || combined.contains("prefix") || combined.contains("suffix") ||
            combined.contains("idiom") || combined.contains("verb") || combined.contains("noun") ||
            combined.contains("adjective") || combined.contains("phrase") -> "📖 Vocabulary & Wordplay"

            else -> "💡 General Trivia"
        }
    }

    private fun fetchJson(urlString: String): JSONObject? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.1 (Android)")
            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchJsonArray(urlString: String): JSONArray? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.1 (Android)")
            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONArray(response)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
