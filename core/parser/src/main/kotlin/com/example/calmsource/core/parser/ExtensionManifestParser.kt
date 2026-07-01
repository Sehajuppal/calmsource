/**
 * Parser for Stremio-compatible extension manifest JSON files.
 *
 * ## Stremio Manifest Format
 * A Stremio addon manifest is a JSON object that describes an extension's identity,
 * capabilities, and content catalogs. Required fields are `id` and `name`. Optional
 * fields include `description`, `version`, `logo`, `resources` (capability list),
 * `types` (content type list), `catalogs` (catalog definitions), and `behaviorHints`.
 *
 * The `resources` array may contain either plain strings (e.g. `"catalog"`, `"stream"`)
 * or objects with a `name` field — both formats are supported.
 *
 * Any JSON fields not in the known set are preserved in [ExtensionManifest.rawAttributes]
 * for forward compatibility with future Stremio protocol versions.
 *
 * @see ExtensionManifest for the parsed output model
 * @see ExtensionInstallResult for the result wrapper with error/warning handling
 */
package com.example.calmsource.core.parser

import com.example.calmsource.core.model.*
import kotlinx.serialization.json.*

object ExtensionManifestParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a Stremio-compatible manifest JSON string into an [ExtensionInstallResult].
     *
     * Validates that required fields (`id`, `name`) are present, extracts all known
     * manifest fields, and collects any unrecognized fields into [ExtensionManifest.rawAttributes].
     * Returns a failure result with a descriptive [ExtensionError] if the JSON is malformed
     * or missing required fields.
     *
     * @param jsonString Raw JSON content of the manifest file
     * @return [ExtensionInstallResult] with the parsed manifest on success, or error details on failure
     */
    fun parse(jsonString: String): ExtensionInstallResult {
        if (jsonString.isBlank()) {
            return ExtensionInstallResult(
                isSuccess = false,
                error = ExtensionError.InvalidManifest("Manifest content is empty"),
                warnings = listOf("Cannot install extension: manifest is empty")
            )
        }

        val warnings = mutableListOf<String>()

        try {
            val element = json.parseToJsonElement(jsonString)
            if (element !is JsonObject) {
                return ExtensionInstallResult(
                    isSuccess = false,
                    error = ExtensionError.InvalidManifest("Manifest must be a JSON object"),
                    warnings = listOf("Malformed JSON layout")
                )
            }

            val id = element["id"]?.jsonPrimitive?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.contentOrNull
            val name = element["name"]?.jsonPrimitive?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.contentOrNull

            if (id.isNullOrEmpty()) {
                return ExtensionInstallResult(
                    isSuccess = false,
                    error = ExtensionError.InvalidManifest("Missing required 'id' field"),
                    warnings = listOf("Field 'id' is empty or missing")
                )
            }

            if (name.isNullOrEmpty()) {
                return ExtensionInstallResult(
                    isSuccess = false,
                    error = ExtensionError.InvalidManifest("Missing required 'name' field"),
                    warnings = listOf("Field 'name' is empty or missing")
                )
            }

            val description = element["description"]?.jsonPrimitive?.contentOrNull
            val version = element["version"]?.jsonPrimitive?.contentOrNull
            val logo = element["logo"]?.jsonPrimitive?.contentOrNull

            // Parse resources / capabilities
            val resources = mutableListOf<String>()
            val resourceTypes = mutableMapOf<String, List<String>>()
            try {
                val resourcesElement = element["resources"]
                if (resourcesElement is JsonArray) {
                    resourcesElement.forEach { res ->
                        if (res is JsonPrimitive) {
                            res.contentOrNull?.let { resources.add(it) }
                        } else if (res is JsonObject) {
                            // Stremio supports objects in resources list sometimes
                            res["name"]?.jsonPrimitive?.contentOrNull?.let { resourceName ->
                                resources.add(resourceName)
                                val scopedTypes = (res["types"] as? JsonArray)
                                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                                    .orEmpty()
                                if (scopedTypes.isNotEmpty()) {
                                    resourceTypes[resourceName] = scopedTypes
                                }
                            }
                        } else {
                            warnings.add("Unrecognized resource entry format: ${res::class.simpleName}")
                        }
                    }
                } else if (resourcesElement != null && resourcesElement !is JsonNull) {
                    warnings.add("Expected 'resources' to be an array, got ${resourcesElement::class.simpleName}")
                }
            } catch (e: Exception) {
                warnings.add("Failed to parse resources: ${e.message}")
            }

            val types = try {
                val typesElement = element["types"]
                if (typesElement is JsonArray) {
                    typesElement.mapNotNull { entry ->
                        if (entry is JsonPrimitive) entry.contentOrNull
                        else {
                            warnings.add("Non-primitive type entry skipped: ${entry::class.simpleName}")
                            null
                        }
                    }
                } else {
                    if (typesElement != null && typesElement !is JsonNull) {
                        warnings.add("Expected 'types' to be an array, got ${typesElement::class.simpleName}")
                    }
                    emptyList()
                }
            } catch (e: Exception) {
                warnings.add("Failed to parse types: ${e.message}")
                emptyList<String>()
            }

            // Parse catalogs
            val catalogs = mutableListOf<ExtensionCatalog>()
            val catalogsElement = element["catalogs"]
            if (catalogsElement != null && catalogsElement !is JsonNull) {
                if (catalogsElement is JsonArray) {
                    catalogsElement.forEach { cat ->
                        try {
                            val catObj = cat.jsonObject
                            val type = catObj["type"]?.jsonPrimitive?.contentOrNull ?: ""
                            val catId = catObj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                            val catName = catObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            val extraElement = catObj["extra"]
                            val extraList = if (extraElement != null && extraElement !is JsonNull) {
                                try {
                                    json.decodeFromJsonElement<List<StremioCatalogExtra>>(extraElement)
                                } catch (e: Exception) {
                                    warnings.add("Failed to parse catalog extra fields: ${e.message}")
                                    null
                                }
                            } else {
                                null
                            }
                            if (type.isNotEmpty() && catId.isNotEmpty()) {
                                catalogs.add(ExtensionCatalog(type, catId, catName, extraList))
                            } else {
                                warnings.add("Catalog entry skipped due to missing fields (type/id)")
                            }
                        } catch (e: Exception) {
                            warnings.add("Failed to parse catalog entry: ${e.message}")
                        }
                    }
                } else {
                    warnings.add("Expected 'catalogs' to be an array, got ${catalogsElement::class.simpleName}")
                }
            }

            // Behavior hints
            val behaviorHints = mutableMapOf<String, String>()
            try {
                val hintsElement = element["behaviorHints"]
                if (hintsElement is JsonObject) {
                    hintsElement.forEach { (k, v) ->
                        behaviorHints[k] = when (v) {
                            is JsonPrimitive -> v.contentOrNull ?: v.toString()
                            else -> v.toString()
                        }
                    }
                } else if (hintsElement != null && hintsElement !is JsonNull) {
                    warnings.add("Expected 'behaviorHints' to be an object, got ${hintsElement::class.simpleName}")
                }
            } catch (e: Exception) {
                warnings.add("Failed to parse behaviorHints: ${e.message}")
            }

            // Raw attributes (extra fields)
            val knownKeys = setOf("id", "name", "description", "version", "logo", "resources", "types", "catalogs", "behaviorHints")
            val rawAttributes = mutableMapOf<String, String>()
            element.forEach { (k, v) ->
                if (k !in knownKeys) {
                    rawAttributes[k] = when (v) {
                        is JsonPrimitive -> v.contentOrNull ?: v.toString()
                        else -> v.toString()
                    }
                }
            }

            val manifest = ExtensionManifest(
                id = id,
                name = name,
                description = description,
                version = version,
                logo = logo,
                resources = resources,
                types = types,
                catalogs = catalogs,
                behaviorHints = behaviorHints,
                rawAttributes = rawAttributes,
                resourceTypes = resourceTypes
            )

            return ExtensionInstallResult(
                isSuccess = true,
                manifest = manifest,
                warnings = warnings
            )

        } catch (e: Exception) {
            return ExtensionInstallResult(
                isSuccess = false,
                error = ExtensionError.ParseError("JSON parsing failed: ${e.message}"),
                warnings = listOf("Malformed JSON syntax: ${e.message}")
            )
        }
    }
}
