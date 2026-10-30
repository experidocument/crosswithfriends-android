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

data class ClueSearchResult(
    val title: String,
    val snippet: String,
    val sourceUrl: String
)

object EncyclopediaService {

    // ==========================================
    // OPTION 1: WORD / TERM (ENCYCLOPEDIA STYLE)
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
    // OPTION 2: CLUE SEARCH (TOP 4-5 WEB RESULTS)
    // ==========================================
    suspend fun searchClueWeb(clue: String): Result<List<ClueSearchResult>> = withContext(Dispatchers.IO) {
        val trimmed = clue.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Clue cannot be empty"))
        }

        val searchTerm = "$trimmed crossword clue"
        val results = mutableListOf<ClueSearchResult>()

        try {
            // Strategy 1: Fetch DuckDuckGo HTML search results
            val encodedQuery = URLEncoder.encode(searchTerm, "UTF-8")
            val urlString = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val html = fetchString(urlString)

            if (!html.isNullOrBlank()) {
                val snippetPattern = Pattern.compile("<a[^>]+class=[\"']result__snippet[\"'][^>]*>(.*?)</a>", Pattern.DOTALL)
                val titlePattern = Pattern.compile("<h2[^>]+class=[\"']result__title[\"'][^>]*>.*?<a[^>]+href=[\"'](.*?)[\"'][^>]*>(.*?)</a>", Pattern.DOTALL)

                val snippetMatcher = snippetPattern.matcher(html)
                val titleMatcher = titlePattern.matcher(html)

                while (titleMatcher.find() && snippetMatcher.find() && results.size < 5) {
                    val rawUrl = titleMatcher.group(1).orEmpty()
                    val rawTitle = titleMatcher.group(2).orEmpty()
                    val rawSnippet = snippetMatcher.group(1).orEmpty()

                    val cleanTitle = cleanHtml(rawTitle)
                    val cleanSnippet = cleanHtml(rawSnippet)
                    val cleanUrl = decodeDuckDuckGoUrl(rawUrl)

                    if (cleanSnippet.isNotBlank() && cleanTitle.isNotBlank()) {
                        results.add(ClueSearchResult(cleanTitle, cleanSnippet, cleanUrl))
                    }
                }
            }

            // Strategy 2: If HTML parser yielded fewer than 3 results, supplement with Wikipedia Search
            if (results.size < 3) {
                val wikiSearchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&utf8=&format=json&srlimit=4"
                val wikiJson = fetchJson(wikiSearchUrl)
                val searchArr = wikiJson?.optJSONObject("query")?.optJSONArray("search")
                if (searchArr != null) {
                    for (i in 0 until searchArr.length()) {
                        if (results.size >= 5) break
                        val item = searchArr.getJSONObject(i)
                        val title = item.optString("title", "")
                        val snippet = cleanHtml(item.optString("snippet", ""))
                        val pageUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                        if (title.isNotBlank()) {
                            results.add(ClueSearchResult(title, snippet, pageUrl))
                        }
                    }
                }
            }

            // Fallback: If absolutely no results could be parsed, provide a Google direct solver entry
            if (results.isEmpty()) {
                results.add(
                    ClueSearchResult(
                        title = "Google Crossword Solver: $trimmed",
                        snippet = "Search web and crossword solvers for potential solutions to: \"$trimmed\".",
                        sourceUrl = "https://www.google.com/search?q=$encodedQuery"
                    )
                )
            }

            Result.success(results)
        } catch (e: Exception) {
            // Even on network error, return direct Google link so user is never blocked
            val fallback = listOf(
                ClueSearchResult(
                    title = "Google Search: $trimmed",
                    snippet = "Tap below to view full clue solver answers and letter combinations on Google.",
                    sourceUrl = "https://www.google.com/search?q=" + URLEncoder.encode(searchTerm, "UTF-8")
                )
            )
            Result.success(fallback)
        }
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

    private fun decodeDuckDuckGoUrl(rawUrl: String): String {
        return try {
            if (rawUrl.contains("uddg=")) {
                val parts = rawUrl.split("uddg=")
                if (parts.size > 1) {
                    val encoded = parts[1].split("&")[0]
                    java.net.URLDecoder.decode(encoded, "UTF-8")
                } else rawUrl
            } else if (rawUrl.startsWith("//")) {
                "https:$rawUrl"
            } else rawUrl
        } catch (e: Exception) {
            rawUrl
        }
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
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.3 (Android)")
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
            connection.setRequestProperty("User-Agent", "CrossWithFriendsCompanion/1.3 (Android)")
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
