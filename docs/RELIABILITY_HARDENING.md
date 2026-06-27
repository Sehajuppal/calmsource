# Application Reliability Hardening

This document summarizes the strategic changes implemented to transition CalmSource from a "happy path" prototype into a highly resilient, fault-tolerant system capable of sustaining real-world abuse.

## 1. Memory and Scaling Hardening
- **SQLite Parameter Limits**: Chunked DB inserts by `500` rows prevent crash failures when parsing 100k+ line M3U playlists.
- **`withTransaction` Encapsulation**: Refactored mass-persistence pipelines into true transactions, avoiding CPU/memory spikes from progressive list array copies.
- **OOM Guardians**: The parsers proactively detect massive payload tag builds and drop them (`> 2MB` per tag) before Java Heap exhaustion occurs.

## 2. ExoPlayer State Hardening
- **Zombie Prevention**: Incomplete/failed `MediaItem` builds now trigger immediate `ExoPlayer.release()` to ensure garbage collection claims the defunct buffers.
- **Infinite Retry Loop Safeties**: `needsPrepare` flags track real ExoPlayer `PlayerState` mapping. Hardcoded `PREPARING` updates are suppressed to prevent fallback-manager timeout hangs.

## 3. Threat and Injection Hardening
- **Input Neutralization**: Credentials, usernames, and secret tokens in repositories are wrapped in strict UTF-8 `URLEncoder.encode` blocks, preventing logical injection.
- **Cascading Ephemerality**: Provider database linkages employ `FOREIGN KEY CASCADE` constraints guaranteeing secure removal of localized child records the moment a master provider is scrubbed.
- **Atomic Concurrency**: Shared preferences utilize `Mutex.withLock` to halt asynchronous write-races in multi-threaded initialization.

## 4. UI Focus and Navigation Hardening
- **State Thrashing Prevention**: Heavy list diffs in Compose leverage decoupled state-nodes (`mutableStateOf`) rather than snapshot list destructuring, halting full-tree recomposition loops.
- **TV D-pad Trap Eradication**: `AnimatedContent` blocks and nested mobile-dialog fragments have been removed from the TV tree, guaranteeing `TvFocusCard` deterministic routing.
