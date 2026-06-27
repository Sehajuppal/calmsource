# Large Dataset Testing

When testing CalmSource, developers must prove that the application does not degrade when faced with datasets exceeding 50,000 items (Channels, VODs, Series, EPG records).

## Generating Test Data
To properly replicate heavy real-world environments, generate:
- **100,000 Channel M3U**: Use a script to spit out an M3U file with 100k distinct channels. 
- **100 MB XMLTV Guide**: Download or generate a massive EPG schedule covering 7 days for 5,000 networks.

## Metrics to Validate
1. **Memory Ceiling**: Use Android Studio Profiler. Parsing the 100,000 channel file must not exceed a `200MB` memory heap allocation spike.
2. **SQLite Locking**: Monitor the DB interactions during import. Ensure the app uses batched `@Transaction` imports of 500 items, and check that the UI does not freeze while the import completes asynchronously on `Dispatchers.IO`.
3. **Scroll Jank (Frames Per Second)**: Once the massive list is imported, navigate to the Live Guide. Press and hold "Down" on the D-Pad. The application must maintain 60 FPS (16ms per frame) without hitching, thanks to strict lazy evaluation and stable composition keys.
4. **Search Degradation**: Type "News" into the search bar. The result must return in under 1.5 seconds even with thousands of local hits. Ensure scoring algorithms don't bog down thread execution.
