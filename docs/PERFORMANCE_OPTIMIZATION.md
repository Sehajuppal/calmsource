# Performance Optimization

This document outlines the specific optimizations introduced during **Mission 19** to improve performance, responsiveness, and memory management across the application.

## Search Pipeline Optimizations
- **Non-blocking Scoring**: Replaced `runBlocking` calls in the scoring loops with efficient memory cache lookups, offloading the database retrieval for `SourceHealth` entirely.
- **Regex Caching**: Hoisted heavily used `Regex` expressions to static companion objects to avoid high-volume instantiation during string normalization loops.

## Database Write Throttling
- **Batch Imports**: All bulk data imports (Xtream, M3U) enforce an SQLite transaction batch limit of 500 items, significantly reducing transaction overhead and lock contention.
- **In-Memory Fuzzy Matching**: The fuzzy string matching logic in the `IPTVRepository` now relies on pre-normalized EPG strings to avoid performing heavy regex operations sequentially over a list of thousands of strings.

## UI List Render Consistency
- **Stable List Keys**: All `LazyColumn` and `LazyRow` composables for channels, VOD, and search results now utilize completely stable unique IDs to prevent excessive view recomposition during rapid TV remote D-Pad navigation.
- **Math Offloading**: All heavy math, scoring, and sorting operations have been securely placed in `remember` blocks to isolate them from frame-rendering loops.

## Network Request Bounding
- **Strict Concurrency Limits**: We introduced `Semaphore` control structures when pinging multiple extensions in parallel. This guarantees that no more than a fixed number of coroutines can consume device resources at one time, preventing thread pool starvation on Android TV.
