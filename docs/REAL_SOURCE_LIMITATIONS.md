# Real Source Limitations

## System & Network Constraints (Verified)
- **Timeouts**: Network timeouts natively cull unresponsive or slow endpoint responses. Any addon taking over 1000ms to respond will be safely skipped to maintain UI fluidity.
- **Error Handling**: Failed addons or misconfigured manifests map cleanly to safe JSON parsing failures rather than triggering application-wide crashes.
- **Resource Parsing**: Stremio endpoints logic requires strict adherence to manifest formats. It uses `isResourceSupported` checks to parse both String and JSON formatted `resources` properly before fetching `meta`, `stream`, and `subtitle` endpoints.
- **Search Deduplication**: `SearchResultDeduplicator` heavily relies on `normalizeForTitleMatch()` to deduplicate `MediaItem` search hashes. Exact naming is crucial for deduplication.

Status: Real source limits documented and verified.
