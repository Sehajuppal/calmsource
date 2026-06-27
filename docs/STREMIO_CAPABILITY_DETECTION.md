# Stremio Capability Detection

This document outlines how CalmSource detects and categorizes the capabilities of installed Stremio extensions. This robust capability detection model allows the app to dynamically route requests—such as catalog fetching, metadata resolution, stream retrieval, and search operations—only to those extensions that natively support them. 

## Overview

Stremio manifests use a highly flexible format. By analyzing the `resources`, `catalogs`, and `behaviorHints` fields of an `ExtensionManifest`, we can deterministically map what an extension provides. CalmSource uses the `ExtensionCapability` enum for internal capability flags.

## Recognized Capabilities

### 1. `CatalogProvider`
An extension acts as a catalog provider if it meets either of the following criteria:
- **Resource Flag:** The `resources` array contains the `"catalog"` element.
- **Explicit Catalogs:** The `catalogs` array is non-empty.

### 2. `SearchCatalogProvider`
The extension supports text-based searching against its catalog.
- **Criteria:** The extension must be a `CatalogProvider`, and at least one entry in its `catalogs` array must include an `extra` parameter named `"search"`.
- **Usage:** Used by the `ExtensionSearchProviderImpl` to route search queries. Providers without this capability are bypassed to prevent unnecessary network requests.

### 3. `MetadataProvider`
The extension provides detailed metadata (titles, overviews, ratings, poster URLs) for a given item.
- **Criteria:** The `resources` array contains the `"meta"` element.

### 4. `StreamProvider`
The extension resolves media identifiers (e.g., IMDb IDs) into playable video streams (HTTP URLs or BitTorrent info hashes).
- **Criteria:** The `resources` array contains the `"stream"` element.

### 5. `SubtitleProvider`
The extension supplies subtitle tracks for video streams.
- **Criteria:** The `resources` array contains the `"subtitles"` element.

### 6. `ConfigRequired`
The extension cannot function properly until user-specific configuration (like an API key or preferences) is provided.
- **Criteria:** The `behaviorHints` object has the `configurationRequired` property set to `true`.

### 7. `UnsupportedResource`
The extension declares resources that are not yet recognized or supported by CalmSource.
- **Criteria:** The `resources` array contains elements other than `"catalog"`, `"meta"`, `"stream"`, or `"subtitles"`.

## Supported Content Types

The detection pipeline also identifies what categories of media the extension can handle:
- **`movie`**
- **`series`**
- **`tv`**
- **`channel`**
- **`events`**
- **`anime`**
- **`other`** (Catch-all for unrecognized types)

**Detection Logic:** Content types are harvested from both the `types` array and the `type` property of individual objects within the `catalogs` array. The values are mapped to the recognized set, with any unknowns categorized as `other`.

## Capability Routing

The `ExtensionSearchProviderImpl` uses the `SearchCatalogProvider` capability to decide whether an extension should be included in a search operation. Before issuing network requests to an extension, the search engine checks:
```kotlin
if (provider.capabilities.contains(ExtensionCapability.SearchCatalogProvider)) {
    // Perform catalog search query
}
```
This ensures that we do not infer unsupported capabilities, optimizing both the device's battery life and the extension server load.
