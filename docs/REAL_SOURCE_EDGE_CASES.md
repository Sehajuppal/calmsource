# Real Source Edge Cases

This document details the edge cases handled during the stabilization phase, based on the reports from various sub-agents.

## IPTV Real Source Stabilization (SA2)

- **Garbled Data / HTML Handling:** M3U and XMLTV parsers correctly handle HTML or garbled data instead of crashing, emitting `isSuccess = false`.
- **Empty Playlists:** Parsers gracefully handle completely empty playlists.
- **Duplicate Channels:** `M3UParser` natively bypasses duplicate channels.
- **URL Sanitization:** URLs are properly sanitized via `UrlRedactor`.
- **Missing Metadata:** Missing `tvg-id/name` safely falls back to `#EXTINF` duration names.
- **Missing EPG Data:** Complete lack of EPG renders a calm "No guide data available" text in `TvLiveGuideScreen` rather than causing an NPE.

## Universal Search Stabilization (SA6)

- **Search Deduplication:** `SearchResultDeduplicator` effectively merges duplicate items title-first across IPTV, Debrid, and Extension sources.
- **Missing Poster URLs:** Null or missing `posterUrl` properties are fully tolerated in ranking.
- **Disabled Extensions:** Disabled extensions natively drop out of search results.
- **Slow Addons Handling:** Slow addons are isolated via `withTimeout` and penalized instead of hanging the overall search pipeline.

## Stream Picker and Deduplication (SA4)

- **Hardened Deduplication:** `SearchResultDeduplicator` logic was updated to strictly group by `mediaItem.id` instead of a normalized title string. This resolves edge cases where unrelated movies with identical names collided, or where identical items from different providers failed to merge.
- **Label Generation:** Fixed a malformed bullet point separator in `SourceIntelligence.generateDisplayLabel`.
- **Inline URL Redaction:** `WatchOptionResolver.cleanStreamTitle` was updated with a regex to successfully obfuscate URLs embedded anywhere in the stream name, not just at the start.
- **Fallback Verification:** `FallbackManager` was audited and confirmed to safely prevent infinite loops via its `AUTO_FALLBACK_UNTIL_PLAYABLE` state machine limit and strict `attemptCount`.


