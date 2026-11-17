module.exports = async function handler(req, res) {
  const host = req.headers.host || "misha-search-backend.vercel.app";
  const url = new URL(req.url || "/", "https://" + host);
  const sendJson = (status, value) => {
    res.statusCode = status;
    res.setHeader("Content-Type", "application/json; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");
    res.end(JSON.stringify(value));
  };

  if (url.pathname === "/api/health") {
    return sendJson(200, {
      ok: true,
      service: "misha-search-backend",
      aiApiEnabled: false,
      timestamp: new Date().toISOString()
    });
  }

  if (url.pathname === "/api/search") {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type");
    if (req.method === "OPTIONS") {
      res.statusCode = 204;
      return res.end();
    }
    if (req.method !== "GET") return sendJson(405, { error: "Use GET." });

    const query = String(url.searchParams.get("q") || "").trim();
    if (query.length < 2 || query.length > 200) {
      return sendJson(400, { error: "Query must contain 2 to 200 characters." });
    }

    try {
      const endpoint = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch="
        + encodeURIComponent(query) + "&format=json&utf8=1";
      const response = await fetch(endpoint, {
        headers: { "User-Agent": "MishaAssistant/2.0 (key-free search)" },
        signal: AbortSignal.timeout(8000)
      });
      if (!response.ok) throw new Error("Search provider returned HTTP " + response.status);
      const data = await response.json();
      const rows = data && data.query && data.query.search ? data.query.search : [];
      const results = rows.slice(0, 8).map(item => ({
        title: String(item.title || ""),
        url: "https://en.wikipedia.org/wiki/" + encodeURIComponent(String(item.title || "").replace(/ /g, "_")),
        snippet: String(item.snippet || "").replace(/<[^>]*>/g, "").replace(/&quot;/g, '"').replace(/&amp;/g, "&"),
        source: "Wikipedia"
      }));
      res.setHeader("Cache-Control", "public, s-maxage=60, stale-while-revalidate=300");
      return sendJson(200, {
        query,
        provider: "Wikipedia Search",
        results,
        notice: "Key-free encyclopedia search, not a complete general web index. Open the source to verify important information."
      });
    } catch (_error) {
      return sendJson(502, { error: "Web search is temporarily unavailable." });
    }
  }

  res.statusCode = 200;
  res.setHeader("Content-Type", "text/plain; charset=utf-8");
  res.end("Misha Search API. Use /api/health or /api/search?q=your-query.");
};
