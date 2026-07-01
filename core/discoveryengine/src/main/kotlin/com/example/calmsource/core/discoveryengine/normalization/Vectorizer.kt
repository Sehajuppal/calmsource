package com.example.calmsource.core.discoveryengine.normalization

import java.nio.ByteBuffer

object Vectorizer {
    const val DIMENSIONS = 256
    const val VERSION = 2
    const val NAME = "feature_hash_v2"

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

    private val COMMON_MOVIE_WORDS = setOf(
        "movie", "movies", "show", "shows", "series", "season", "seasons", 
        "episode", "episodes", "film", "films", "story", "stories", "drama", 
        "comedy", "action", "thriller", "world", "life", "finds", "must", 
        "young", "new", "two", "man", "woman", "time", "first", "years", 
        "living", "lives", "star", "stars", "plays", "playing", "about"
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
            addTokenToVector(vector, token, computeTfidfWeight(token, 3.0f))
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
                addTokenToVector(vector, token, computeTfidfWeight(token, 1.0f))
                if (token.length >= 4) {
                    addTokenToVector(vector, "keyword_$token", computeTfidfWeight(token, 2.0f))
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
            addTokenToVector(vector, token, computeTfidfWeight(token, 1.0f))
            if (token.length >= 4) {
                addTokenToVector(vector, "keyword_$token", computeTfidfWeight(token, 1.5f))
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

    fun tokenize(text: String): List<String> {
        val unigrams = text.lowercase()
            .split(Regex("[^a-z0-9]"))
            .filter { it.isNotEmpty() && it !in STOP_WORDS }
        
        val bigrams = unigrams.zipWithNext { a, b -> "${a}_${b}" }
        return unigrams + bigrams
    }

    private fun computeTfidfWeight(token: String, baseWeight: Float): Float {
        // If it's a structured metadata token, do not scale by length or common words
        if (token.startsWith("genre_") || 
            token.startsWith("director_") || 
            token.startsWith("cast_") || 
            token.startsWith("lang_") || 
            token.startsWith("region_") || 
            token.startsWith("norm_title_") || 
            token.startsWith("alias_") || 
            token.startsWith("source_")) {
            return baseWeight
        }
        
        val cleanToken = token.substringAfter("keyword_").substringAfter("query_")
        val isBigram = cleanToken.contains("_")
        
        val lengthFactor = if (isBigram) 1.2f else (cleanToken.length / 5.0f).coerceIn(0.6f, 1.8f)
        val commonWordPenalty = if (cleanToken in COMMON_MOVIE_WORDS) 0.25f else 1.0f
        
        return baseWeight * lengthFactor * commonWordPenalty
    }

    private fun addTokenToVector(vector: FloatArray, token: String, weight: Float) {
        val hash = murmur3(token, 0x811c9dc5.toInt())
        val index = Math.floorMod(hash, DIMENSIONS)
        vector[index] += weight
    }

    /**
     * MurmurHash3 32-bit implementation for fast, uniform hashing.
     */
    private fun murmur3(data: String, seed: Int): Int {
        var h1 = seed
        val bytes = data.toByteArray(Charsets.UTF_8)
        val length = bytes.size
        val nblocks = length / 4

        for (i in 0 until nblocks) {
            val index = i * 4
            var k1 = (bytes[index].toInt() and 0xff) or
                     ((bytes[index + 1].toInt() and 0xff) shl 8) or
                     ((bytes[index + 2].toInt() and 0xff) shl 16) or
                     ((bytes[index + 3].toInt() and 0xff) shl 24)

            k1 *= 0xcc9e2d51.toInt()
            k1 = Integer.rotateLeft(k1, 15)
            k1 *= 0x1b873593

            h1 = h1 xor k1
            h1 = Integer.rotateLeft(h1, 13)
            h1 = h1 * 5 + 0xe6546b64.toInt()
        }

        var k1 = 0
        val tailStart = nblocks * 4
        val remaining = length - tailStart
        if (remaining > 0) {
            if (remaining >= 3) k1 = k1 or ((bytes[tailStart + 2].toInt() and 0xff) shl 16)
            if (remaining >= 2) k1 = k1 or ((bytes[tailStart + 1].toInt() and 0xff) shl 8)
            if (remaining >= 1) {
                k1 = k1 or (bytes[tailStart].toInt() and 0xff)
                k1 *= 0xcc9e2d51.toInt()
                k1 = Integer.rotateLeft(k1, 15)
                k1 *= 0x1b873593
                h1 = h1 xor k1
            }
        }

        h1 = h1 xor length
        h1 = h1 xor (h1 ushr 16)
        h1 *= 0x85ebca6b.toInt()
        h1 = h1 xor (h1 ushr 13)
        h1 *= 0xc2b2ae35.toInt()
        h1 = h1 xor (h1 ushr 16)

        return h1
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
