package com.misha.assistant

import android.text.Html
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

internal data class SearchResponse(
    val provider: String,
    val results: List<SearchResult>,
    val notice: String
)

internal object SearchClient {
    private const val BACKEND = "https://misha-search-backend.vercel.app/api/search"

    fun search(query: String, callback: (Result<SearchResponse>) -> Unit) {
        Thread {
            val result = runCatching { requestBackend(query) }
                .recoverCatching { requestWikipedia(query) }
            callback(result)
        }.start()
    }

    private fun requestBackend(query: String): SearchResponse {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return parse(get(BACKEND + "?q=" + encoded), "Misha Search Backend")
    }

    private fun requestWikipedia(query: String): SearchResponse {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + encoded + "&format=json&utf8=1"
        val root = JSONObject(get(endpoint))
        val items = root.optJSONObject("query")?.optJSONArray("search")
        val results = mutableListOf<SearchResult>()
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val title = item.optString("title")
                if (title.isBlank()) continue
                val snippet = Html.fromHtml(item.optString("snippet"), Html.FROM_HTML_MODE_LEGACY).toString()
                results += SearchResult(
                    title,
                    snippet,
                    "https://en.wikipedia.org/wiki/" + title.replace(" ", "_"),
                    "Wikipedia"
                )
            }
        }
        return SearchResponse(
            "Wikipedia Search",
            results,
            "Key-free encyclopedia search. It is not a complete general web search engine; open sources to verify important information."
        )
    }

    private fun parse(raw: String, fallbackProvider: String): SearchResponse {
        val root = JSONObject(raw)
        val items = root.optJSONArray("results")
        val results = mutableListOf<SearchResult>()
        if (items != null) {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val title = item.optString("title")
                val address = item.optString("url")
                if (title.isBlank() || !address.startsWith("https://")) continue
                results += SearchResult(
                    title,
                    item.optString("snippet"),
                    address,
                    item.optString("source", fallbackProvider)
                )
            }
        }
        return SearchResponse(
            root.optString("provider", fallbackProvider),
            results,
            root.optString("notice", "Open the source link to read the original information.")
        )
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "MishaAndroid/2.0")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Search request failed with HTTP " + code)
            return body
        } finally {
            connection.disconnect()
        }
    }
}
