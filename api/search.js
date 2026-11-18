module.exports = async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");
  res.setHeader("Cache-Control", "public, s-maxage=60, stale-while-revalidate=300");
  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") return res.status(405).json({ error: "Use GET." });

  const query = String(req.query.q || "").trim();
  if (query.length < 2 || query.length > 200) {
    return res.status(400).json({ error: "Query must contain 2 to 200 characters." });
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
    return res.status(200).json({
      query,
      provider: "Wikipedia Search",
      results,
      notice: "Key-free encyclopedia search, not a complete general web index. Open the source to verify important information."
    });
  } catch (_error) {
    return res.status(502).json({ error: "Web search is temporarily unavailable." });
  }
};
