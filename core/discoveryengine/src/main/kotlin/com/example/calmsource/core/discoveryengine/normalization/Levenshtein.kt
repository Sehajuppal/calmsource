package com.example.calmsource.core.discoveryengine.normalization

/**
 * Utility class for calculating Levenshtein distance between char sequences.
 */
object Levenshtein {

    /**
     * Calculates the Levenshtein distance between two CharSequences.
     * Levenshtein distance is the minimum number of single-character edits (insertions,
     * deletions or substitutions) required to change one word into the other.
     */
    fun distance(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        if (lhsLength == 0) return rhsLength
        if (rhsLength == 0) return lhsLength

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val temp = cost
            cost = newCost
            newCost = temp
        }
        return cost[lhsLength]
    }
}
