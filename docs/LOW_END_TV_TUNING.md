# Low-End TV Tuning Guidelines

This guide is intended for developers building for low-resource Android TV hardware (typically 1GB–1.5GB RAM and entry-level quad-core ARM chips).

## Memory Management

### 1. Coil Image Scaling
Never load raw 4K or 1080p images into memory. Low-end Android TVs will instantly hit Out-Of-Memory exceptions if scaling is not strictly enforced. 
- Ensure Coil's `ImageLoader` enforces `size()` constraints before decoding.
- Limit the memory cache policy to use a maximum of 15% of the total system heap space.

### 2. XML Parser Constraints
When parsing large XMLTV feeds, never load the entire document or huge preamble chunks into a single `String` via `Scanner.next()`. Always skip preambles (like the `<channel>` list) using a streaming `BufferedReader` before tokenizing the actual `<programme>` blocks.

## CPU Management

### 1. Re-use Media Decoders
Initializing and destroying the `ExoPlayer` instance and its underlying MediaCodec can take upwards of 500ms on a weak chipset. Always attempt safe player reuse when users switch between channels.

### 2. Isolate Progress Updates
The 1-second progress ticks for video playback should solely update a small `progressState` Flow instead of recomposing the entire `PlayerScreen` overlay.

### 3. Cap Network Coroutines
When reaching out to Stremio extensions, use `Semaphore(4)` (or similar bounds) to guarantee you do not spawn 30 concurrent coroutines doing HTTP and JSON work. 

## Input Handling
D-Pad remote inputs can be spammy. Set a minimum `debounce` period of **400ms** on Search input bars to ensure transient keypresses do not continuously reset and restart search pipelines.
