package com.misha.assistant

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class SearchClient(private val backendBaseUrl: String = "") {
    fun search(query: String): List<SearchResult> {
        val endpoint = if (backendBaseUrl.isNotBlank()) {
            backendBaseUrl.trimEnd('/') + "/api/search?q=" + Uri.encode(query)
        } else {
            "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                Uri.encode(query) + "&format=json&utf8=1"
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MishaAssistant/1.0 (Android web search)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) throw IllegalStateException("Search service returned HTTP $status")
            val root = JSONObject(body)
            val backendResults = root.optJSONArray("results")
            if (backendResults != null) {
                return (0 until backendResults.length()).mapNotNull { index ->
                    val item = backendResults.optJSONObject(index) ?: return@mapNotNull null
                    SearchResult(item.optString("title"), item.optString("snippet"), item.optString("url"), item.optString("source", "Web"))
                }.filter { it.title.isNotBlank() && it.url.startsWith("https://") }
            }
            val items = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
            return (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title")
                val snippet = android.text.Html.fromHtml(item.optString("snippet"), android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                if (title.isBlank()) null else SearchResult(
                    title,
                    snippet,
                    "https://en.wikipedia.org/wiki/" + Uri.encode(title.replace(" ", "_")),
                    "Wikipedia"
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
