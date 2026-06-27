package com.example.calmsource.core.discoveryengine.normalization

import java.nio.ByteBuffer

object Vectorizer {
    const val DIMENSIONS = 128
    const val VERSION = 1
    const val NAME = "feature_hash_v1"

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", 
        "to", "of", "in", "on", "at", "by", "for", "with", "about", 
        "against", "between", "into", "through", "during", "before", 
        "after", "above", "below", "from", "up", "down", "out", "off", 
        "over", "under", "again", "further", "then", "once", "here", 
        "there", "when", "where", "why", "how", "all", "any", "both", 
        "each", "few", "more", "most", "other", "some", "such", "no", 
        "nor", "not", "only", "own", "same", "so", "than", "too", 
        "very", "s", "t", "can", "will", "just", "don", "should", "now"
    )

    fun vectorize(
        title: String,
        overview: String?,
        genres: List<String>,
        cast: List<String>,
        director: String?,
        language: String?,
        source: String?
    ): FloatArray {
        val vector = FloatArray(DIMENSIONS)

        // 1. Title (Weight = 3.0)
        val titleTokens = tokenize(title)
        for (token in titleTokens) {
            addTokenToVector(vector, token, 3.0f)
        }

        // 2. Normalized Title and Aliases (Weight = 3.0)
        val normTitle = MetadataNormalizer.normalizeTitle(title)
        addTokenToVector(vector, "norm_title_$normTitle", 3.0f)
        
        val titleAliases = MetadataNormalizer.generateTitleAliases(title)
        for (alias in titleAliases) {
            addTokenToVector(vector, "alias_$alias", 3.0f)
        }

        // 3. Genres (Weight = 2.5)
        genres.forEach { g ->
            val cleanGenre = "genre_" + g.lowercase().replace(" ", "_")
            addTokenToVector(vector, cleanGenre, 2.5f)
        }

        // 4. Language & Region (Weight: Lang = 2.0, Region = 1.5)
        language?.let { lang ->
            val cleanLang = lang.lowercase().trim()
            if (cleanLang.contains("-") || cleanLang.contains("_")) {
                val parts = cleanLang.split(Regex("[-_]"))
                if (parts.isNotEmpty()) {
                    addTokenToVector(vector, "lang_" + parts[0], 2.0f)
                }
                if (parts.size > 1) {
                    addTokenToVector(vector, "region_" + parts[1], 1.5f)
                }
            } else {
                addTokenToVector(vector, "lang_" + cleanLang, 2.0f)
            }
        }

        // 5. Cast/Director (Weight = 1.5)
        cast.forEach { c ->
            val cleanCast = "cast_" + c.lowercase().replace(" ", "_")
            addTokenToVector(vector, cleanCast, 1.5f)
        }
        director?.let { d ->
            val cleanDir = "director_" + d.lowercase().replace(" ", "_")
            addTokenToVector(vector, cleanDir, 1.5f)
        }

        // 6. Overview (Weight = 1.0) and Keywords (Weight = 2.0)
        overview?.let { ov ->
            val ovTokens = tokenize(ov)
            for (token in ovTokens) {
                addTokenToVector(vector, token, 1.0f)
                if (token.length >= 4) {
                    addTokenToVector(vector, "keyword_$token", 2.0f)
                }
            }
        }

        // 7. Addon/Source (Weight = 0.5)
        source?.let { src ->
            val cleanSrc = "source_" + src.lowercase().replace(" ", "_")
            addTokenToVector(vector, cleanSrc, 0.5f)
        }

        return normalize(vector)
    }

    fun vectorizeQuery(query: String): FloatArray {
        val vector = FloatArray(DIMENSIONS)
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return vector

        for (token in tokens) {
            addTokenToVector(vector, token, 1.0f)
            if (token.length >= 4) {
                addTokenToVector(vector, "keyword_$token", 1.5f)
            }
        }

        val normQuery = MetadataNormalizer.normalizeSearchQuery(query)
        if (normQuery.isNotEmpty()) {
            addTokenToVector(vector, "query_$normQuery", 2.0f)
        }

        return normalize(vector)
    }

    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Double {
        if (vecA.size != DIMENSIONS || vecB.size != DIMENSIONS) return 0.0
        var dotProduct = 0.0
        for (i in 0 until DIMENSIONS) {
            dotProduct += vecA[i] * vecB[i]
        }
        return dotProduct
    }

    fun vectorToBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4)
        for (v in vector) {
            buffer.putFloat(v)
        }
        return buffer.array()
    }

    fun bytesToVector(bytes: ByteArray): FloatArray {
        if (bytes.size != DIMENSIONS * 4) {
            android.util.Log.w("Vectorizer", "Embedding size mismatch: expected ${DIMENSIONS * 4} bytes, got ${bytes.size}. Returning zero vector.")
            return FloatArray(DIMENSIONS)
        }
        val floatArray = FloatArray(DIMENSIONS)
        val buffer = ByteBuffer.wrap(bytes)
        for (i in 0 until DIMENSIONS) {
            floatArray[i] = buffer.float
        }
        return floatArray
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]"))
            .filter { it.isNotEmpty() && it !in STOP_WORDS }
    }

    private fun addTokenToVector(vector: FloatArray, token: String, weight: Float) {
        val hash1 = fnv1a(token, 0x811c9dc5.toInt())
        val index = Math.floorMod(hash1, DIMENSIONS)
        val hash2 = fnv1a(token, 0xda1e2d3f.toInt())
        val sign = if ((hash2 and 1) == 0) 1.0f else -1.0f
        vector[index] += sign * weight
    }

    private fun fnv1a(str: String, seed: Int): Int {
        var hash = seed
        for (i in 0 until str.length) {
            hash = hash xor str[i].code
            hash = hash * 16777619
        }
        return hash
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSq = 0.0f
        for (v in vector) {
            sumSq += v * v
        }
        if (sumSq > 0.0f) {
            val norm = Math.sqrt(sumSq.toDouble()).toFloat()
            for (i in 0 until DIMENSIONS) {
                vector[i] /= norm
            }
        }
        return vector
    }
}
