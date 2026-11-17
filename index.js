module.exports = function handler(_req, res) {
  return res.status(200).send("Misha Search API. Use /api/health or /api/search?q=your-query.");
};
