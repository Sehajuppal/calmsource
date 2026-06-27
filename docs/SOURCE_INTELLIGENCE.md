# Source Intelligence

## Overview
Source Intelligence refers to the robust parsing, modeling, ranking, and UI integration layer that takes raw, unstructured metadata from various streaming sources and extensions and formats it cleanly and smartly for the end-user. The primary goal is to present high-quality, relevant source data without overwhelming the user or exposing raw URLs and API queries.

## Parser Architecture
The architecture comprises several independent modules:
1. **Source Parsers (`core/sourceintelligence/.../parsers/`)**: Normalizes metadata from distinct API providers, scrapers, and extensions. Transforms varying structures into a uniform `ParsedSource` model.
2. **Metadata Model (`core/sourceintelligence/.../models/`)**: The strongly-typed internal representation of source metadata. Ensures that all data passing through the system conforms to known fields (e.g., quality, size, language, audio).
3. **Source Ranker (`core/sourceintelligence/.../ranking/`)**: Evaluates `ParsedSource` objects and ranks them based on heuristics (e.g., resolution, file size, user's network preferences, debrid availability).
4. **UI Integrations (`DetailsScreen.kt`, `TvDetailsScreen.kt`)**: Jetpack Compose components designed to display the ranked sources cleanly. Raw technical details are hidden behind "More Info" toggles if necessary, keeping the UI minimal and user-friendly.

## Privacy Rules & Security
Source Intelligence enforces strict rules regarding how source data is exposed or stored:
- **No Raw URLs Exposed**: Raw filenames, direct links, and private query parameters are NEVER exposed to the UI by default.
- **Abstraction Over Concrete Providers**: The system relies on abstracted interfaces. It does not contain code for unauthorized scraping or piracy API implementations; it safely handles unknown or messy data dynamically.
- **Query Stripping**: Any telemetry or diagnostic logging strips personally identifiable parameters and sensitive access tokens before writing to disk or console.
- **Fail-Safe Parsing**: All parsers strictly validate inbound payloads. Unknown fields are dropped or sanitized to prevent arbitrary injection.

## Testing & Reliability
- **Unit Testing**: Parsers include extensive unit tests covering both well-formed payloads and malformed/malicious garbage data.
- **Fallback Chains**: In the event of an unparseable response, the layer smoothly degrades, logging an error internally without breaking the user playback experience.

