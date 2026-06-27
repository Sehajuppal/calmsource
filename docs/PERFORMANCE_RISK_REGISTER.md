# Performance Risk Register

This document tracks known architectural limits and the mitigations applied to prevent them from surfacing in production.

| Risk ID | Component | Severity | Description | Mitigation Strategy |
|---------|-----------|----------|-------------|---------------------|
| PR-01 | `XMLTVParser` | Critical | Massive `<channel>` preamble sections in XMLTV feeds can easily exceed 50MB. If `Scanner` attempts to load this string into memory, OOM crashes occur instantly. | We implemented streaming `BufferedReader` skip-logic to completely bypass the preamble before feeding tokenized blocks to `Scanner`. |
| PR-02 | `SearchEngine` | Critical | Scoring loop triggered blocking Room Database reads `runBlocking(Dispatchers.IO)`, which crippled search times when dealing with 100+ match instances. | Decoupled health state from synchronous database lookups. Adopted pre-fetching and in-memory caches. |
| PR-03 | UI Components | Medium | Launching unbounded coroutines (e.g., 20+ parallel network fetches) for multiple installed extensions can choke low-end ARM processors. | Added `Semaphore` limits to queue HTTP fetching, restricting parallel concurrency. |
| PR-04 | `M3UParser` | Low | Memory leak from unclosed `BufferedReader` objects. | Strict enforcement of Kotlin's `.use { }` block closures for all File and Stream handlers. |
| PR-05 | `IPTVRepository` | Medium | Inefficient `Regex` allocations and normalizations inside a double nested loop during `fuzzy match` operations. | String structures are normalized once and cached before the Tier 4 matching loop executes. |
