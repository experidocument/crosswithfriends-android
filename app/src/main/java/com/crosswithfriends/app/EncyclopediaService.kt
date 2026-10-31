package com.crosswithfriends.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class EncyclopediaResult(
    val word: String,
    val category: String,
    val summary: String,
    val trivia: String,
    val etymology: String,
    val example: String,
    val sourceUrl: String
)

data class AiOverviewResult(
    val answer: String,
    val lettersCount: String,
    val explanation: String,
    val sourceUrl: String
)

object EncyclopediaService {

    // ==========================================
    // OPTION 1: WORD OR TERM (ENCYCLOPEDIA STYLE)
    // ==========================================
    suspend fun lookup(query: String): Result<EncyclopediaResult> = withContext(Dispatchers.IO) {
        val rawTrimmed = query.trim()
        if (rawTrimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Query cannot be empty"))
        }

        val cleanedQuery = cleanClueQuery(rawTrimmed)

        try {
            val directTitle = cleanedQuery.replace(" ", "_")
            var wikiJson = fetchWikiSummary(directTitle)

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

            val finalTrivia = when {
                triviaText.isNotBlank() -> triviaText
                definitionText.isNotBlank() -> definitionText
                else -> "No immediate encyclopedia summary found for '$rawTrimmed'. Tap 'Search on Google' for full web results."
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

    // ==========================================
    // OPTION 2: CLUE AI OVERVIEW (SINGLE DIRECT ANSWER)
    // ==========================================
    suspend fun getClueAiOverview(clue: String): Result<AiOverviewResult> = withContext(Dispatchers.IO) {
        val trimmed = clue.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Clue cannot be empty"))
        }

        // Extract expected letter count from clues like "...(9)" or "...(5 letters)"
        val lettersCount = extractLettersCount(trimmed)
        val searchTerm = "$trimmed crossword clue"
        val googleSearchUrl = "https://www.google.com/search?q=" + URLEncoder.encode(searchTerm, "UTF-8")

        try {
            val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
            val urlString = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val html = fetchString(urlString)

            var topSnippet = ""
            var topTitle = ""

            if (!html.isNullOrBlank()) {
                val snippetPattern = Pattern.compile("<a[^>]+class=[\"']result__snippet[\"'][^>]*>(.*?)</a>", Pattern.DOTALL)
                val titlePattern = Pattern.compile("<h2[^>]+class=[\"']result__title[\"'][^>]*>.*?<a[^>]+href=[\"'](.*?)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL)

                val snippetMatcher = snippetPattern.matcher(html)
                val titleMatcher = titlePattern.matcher(html)

                if (titleMatcher.find() && snippetMatcher.find()) {
                    topTitle = cleanHtml(titleMatcher.group(2).orEmpty())
                    topSnippet = cleanHtml(snippetMatcher.group(1).orEmpty())
                }
            }

            // Fallback: search Wikipedia
            if (topSnippet.isBlank()) {
                val cleanQuery = cleanClueQuery(trimmed)
                val wikiTitle = searchWikiTitle(cleanQuery)
                if (wikiTitle != null) {
                    val wikiJson = fetchWikiSummary(wikiTitle.replace(" ", "_"))
                    topTitle = wikiTitle
                    topSnippet = wikiJson?.optString("extract", "").orEmpty()
                }
            }

            // Extract the most plausible uppercase solution word
            val extractedWord = extractPotentialAnswer(topTitle, topSnippet, lettersCount)

            val finalAnswer = if (extractedWord.isNotBlank()) extractedWord else topTitle.ifBlank { "SOLUTION FOUND" }
            val finalExplanation = if (topSnippet.isNotBlank()) {
                topSnippet
            } else {
                "Plausible solutions found. Tap 'View All Results on Google' below to view full clue solver breakdowns and letter grids."
            }

            val overview = AiOverviewResult(
                answer = finalAnswer.uppercase(),
                lettersCount = if (lettersCount.isNotBlank()) "$lettersCount LETTERS" else "",
                explanation = finalExplanation,
                sourceUrl = googleSearchUrl
            )

            Result.success(overview)
        } catch (e: Exception) {
            val fallback = AiOverviewResult(
                answer = "WEB SEARCH RESULT",
                lettersCount = if (lettersCount.isNotBlank()) "$lettersCount LETTERS" else "",
                explanation = "Direct search for: \"$trimmed\". Tap below to view Google's AI Overview and crossword solver answers.",
                sourceUrl = googleSearchUrl
            )
            Result.success(fallback)
        }
    }

    private fun extractLettersCount(clue: String): String {
        val matcher = Pattern.compile("\\(([0-9]+)\\s*(letters)?\\)", Pattern.CASE_INSENSITIVE).matcher(clue)
        return if (matcher.find()) {
            matcher.group(1).orEmpty()
        } else ""
    }

    private fun extractPotentialAnswer(title: String, snippet: String, expectedLength: String): String {
        val targetLen = expectedLength.toIntOrNull()
        val combined = "$title $snippet"

        // Check for words in all caps e.g. "ROTTERDAM"
        val capsMatcher = Pattern.compile("\\b([A-Z]{3,15})\\b").matcher(combined)
        val candidates = mutableListOf<String>()
        while (capsMatcher.find()) {
            val word = capsMatcher.group(1) ?: continue
            if (word != "CROSSWORD" && word != "CLUE" && word != "SOLVER" && word != "ANSWER" && word != "LETTERS") {
                if (targetLen != null && word.length == targetLen) {
                    return word // Exact letter match!
                }
                candidates.add(word)
            }
        }

        return candidates.firstOrNull() ?: ""
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun cleanClueQuery(query: String): String {
        var q = query
        q = q.replace(Regex("^[0-9]+[A-Za-z]?\\s+"), "")
        q = q.replace(Regex("^[0-9]+\\s*(Across|Down)\\s+", RegexOption.IGNORE_CASE), "")
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
            combined.contains("film") || combined.contains("movie") || combined.contains("cinema") ||
            combined.contains("actor") || combined.contains("actress") || combined.contains("director") ||
            combined.contains("television") || combined.contains("tv series") || combined.contains("sitcom") ||
            combined.contains("hollywood") || combined.contains("oscar") || combined.contains("emmy") ||
            combined.contains("screenplay") || combined.contains("episode") -> "🎬 Film & TV"

            combined.contains("music") || combined.contains("song") || combined.contains("album") ||
            combined.contains("band") || combined.contains("singer") || combined.contains("composer") ||
            combined.contains("musician") || combined.contains("symphony") || combined.contains("opera") ||
            combined.contains("grammy") || combined.contains("orchestra") || combined.contains("guitar") -> "🎵 Music"

            combined.contains("sport") || combined.contains("game") || combined.contains("football") ||
            combined.contains("soccer") || combined.contains("baseball") || combined.contains("basketball") ||
            combined.contains("tennis") || combined.contains("olympic") || combined.contains("athlete") ||
            combined.contains("stadium") || combined.contains("tournament") || combined.contains("nba") ||
            combined.contains("nfl") || combined.contains("chess") -> "⚽ Sports"

            combined.contains("president") || combined.contains("prime minister") || combined.contains("king") ||
            combined.contains("queen") || combined.contains("emperor") || combined.contains("dynasty") ||
            combined.contains("empire") || combined.contains("battle") || combined.contains("treaty") ||
            combined.contains("war") || combined.contains("reign") || combined.contains("parliament") -> "🏛️ History"

            combined.contains("novel") || combined.contains("author") || combined.contains("poet") ||
            combined.contains("poetry") || combined.contains("poem") || combined.contains("mythology") ||
            combined.contains("myth") || combined.contains("folklore") || combined.contains("literature") ||
            combined.contains("playwright") || combined.contains("fable") -> "📜 Literature"

            combined.contains("island") || combined.contains("city") || combined.contains("country") ||
            combined.contains("river") || combined.contains("mountain") || combined.contains("capital") ||
            combined.contains("lake") || combined.contains("state in") || combined.contains("region") ||
            combined.contains("ocean") || combined.contains("sea") || combined.contains("province") -> "🌍 Geography"

            combined.contains("painter") || combined.contains("painting") || combined.contains("art") ||
            combined.contains("museum") || combined.contains("sculptor") || combined.contains("theatre") -> "🎨 Arts"

            combined.contains("noodle") || combined.contains("dish") || combined.contains("food") ||
            combined.contains("cuisine") || combined.contains("cocktail") || combined.contains("drink") ||
            combined.contains("recipe") || combined.contains("wine") || combined.contains("soup") ||
            combined.contains("julep") || combined.contains("bourbon") || combined.contains("baking") ||
            combined.contains("dessert") -> "🍜 Food & Drink"

            combined.contains("chemistry") || combined.contains("compound") || combined.contains("physics") ||
            combined.contains("biology") || combined.contains("species") || combined.contains("organic") ||
            combined.contains("anatomy") || combined.contains("element") || combined.contains("formula") ||
            combined.contains("animal") || combined.contains("plant") || combined.contains("planet") -> "🧪 Science"

            combined.contains("slang") || combined.contains("prefix") || combined.contains("suffix") ||
            combined.contains("idiom") || combined.contains("verb") || combined.contains("noun") ||
            combined.contains("adjective") || combined.contains("phrase") || combined.contains("synonym") -> "📖 Vocabulary"

            else -> "💡 General"
        }
    }

    private fun fetchJson(urlString: String): JSONObject? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.4 (Android)")
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
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.4 (Android)")
            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONArray(response)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchString(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
