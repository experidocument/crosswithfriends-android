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
        val rawTrimmed = query.trim()
        if (rawTrimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Query cannot be empty"))
        }

        // Clean query: remove leading/trailing clue numbers like "30A", "5 Across", "(9)", "(39 Across)"
        val cleanedQuery = cleanClueQuery(rawTrimmed)

        try {
            // Tier 1: Try direct title lookup with underscores (e.g., "Mint julep" -> "Mint_julep")
            val directTitle = cleanedQuery.replace(" ", "_")
            var wikiJson = fetchWikiSummary(directTitle)

            // Tier 2: If direct lookup failed (common for clues or sentences), use Wikipedia Search API
            if (wikiJson == null) {
                val searchTitle = searchWikiTitle(cleanedQuery)
                if (searchTitle != null) {
                    wikiJson = fetchWikiSummary(searchTitle.replace(" ", "_"))
                }
            }

            var wordTitle = cleanedQuery
            var triviaText = ""
            var sourceUrl = "https://www.google.com/search?q=" + URLEncoder.encode(rawTrimmed, "UTF-8")
            var description = ""

            if (wikiJson != null) {
                wordTitle = wikiJson.optString("title", cleanedQuery)
                triviaText = wikiJson.optString("extract", "")
                description = wikiJson.optString("description", "")
                sourceUrl = wikiJson.optJSONObject("content_urls")
                    ?.optJSONObject("desktop")
                    ?.optString("page", sourceUrl) ?: sourceUrl
            }

            // Tier 3: Also query Dictionary API for definition, etymology, and example sentences
            val dictQuery = if (cleanedQuery.contains(" ")) cleanedQuery.split(" ").first() else cleanedQuery
            val dictJson = fetchDictionary(dictQuery)

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

            // Final fallback synthesis
            val finalTrivia = when {
                triviaText.isNotBlank() -> triviaText
                definitionText.isNotBlank() -> definitionText
                else -> "Clue / Search: \"$rawTrimmed\"\n\nNo exact single-page article found. Tap 'Search on Google' below to view full clue solver and web results."
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

    /**
     * Cleans up typical crossword prefixes and suffixes (e.g., "5A Rodent...", "(9)")
     */
    private fun cleanClueQuery(query: String): String {
        var q = query
        // Remove leading clue number like "5A ", "30 Across ", "12D "
        q = q.replace(Regex("^[0-9]+[A-Za-z]?\\s+"), "")
        q = q.replace(Regex("^[0-9]+\\s*(Across|Down)\\s+", RegexOption.IGNORE_CASE), "")
        // Remove trailing letter counts like "(9)", "(4, 5)", "(39 Across)"
        q = q.replace(Regex("\\([0-9,\\s]+(letters)?\\)$"), "")
        q = q.replace(Regex("\\([0-9]+\\s*(Across|Down)\\)$", RegexOption.IGNORE_CASE), "")
        return q.trim()
    }

    private fun fetchWikiSummary(title: String): JSONObject? {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
            val urlString = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
            fetchJson(urlString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Searches Wikipedia for matching articles when a phrase or clue is searched.
     */
    private fun searchWikiTitle(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json&srlimit=1"
            val json = fetchJson(urlString)
            val searchResults = json?.optJSONObject("query")?.optJSONArray("search")
            if (searchResults != null && searchResults.length() > 0) {
                searchResults.getJSONObject(0).optString("title", null)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDictionary(word: String): JSONArray? {
        return try {
            val encoded = URLEncoder.encode(word.trim(), "UTF-8")
            val urlString = "https://api.dictionaryapi.dev/api/v2/entries/en/$encoded"
            fetchJsonArray(urlString)
        } catch (e: Exception) {
            null
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
            combined.contains("recipe") || combined.contains("wine") || combined.contains("soup") ||
            combined.contains("julep") || combined.contains("bourbon") -> "🍜 Food & Drink"

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
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.2 (Android)")
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
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.2 (Android)")
            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONArray(response)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
