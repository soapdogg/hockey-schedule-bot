package lambda

import kotlin.math.abs
import kotlin.random.Random

object InsightStats {
    const val PERMUTATIONS = 2000

    /**
     * Two-sided permutation test: how often a random group of the same size does at least as
     * well, or as badly, compared with the rest as the real group did. Seeded, so the same data
     * always gives the same p-value.
     */
    fun permutationPValue(inGroup: List<Double>, outGroup: List<Double>, seed: Int, permutations: Int = PERMUTATIONS): Double {
        val all = (inGroup + outGroup).toDoubleArray()
        val n = all.size
        val k = inGroup.size
        if (k == 0 || k == n) return 1.0
        val total = all.sum()
        val observed = abs(meanDifference(inGroup.sum(), k, total, n))
        val random = Random(seed)
        var atLeastAsExtreme = 0
        repeat(permutations) {
            // Partial Fisher-Yates shuffle: the first k entries become a random group.
            var sum = 0.0
            for (i in 0 until k) {
                val j = i + random.nextInt(n - i)
                val tmp = all[i]; all[i] = all[j]; all[j] = tmp
                sum += all[i]
            }
            if (abs(meanDifference(sum, k, total, n)) >= observed - 1e-9) atLeastAsExtreme++
        }
        return (atLeastAsExtreme + 1).toDouble() / (permutations + 1)
    }

    private fun meanDifference(inSum: Double, k: Int, total: Double, n: Int): Double =
        inSum / k - (total - inSum) / (n - k)

    /**
     * Benjamini-Hochberg: which of these p-values survive when testing all of them at once,
     * keeping the expected share of false discoveries at [falseDiscoveryRate].
     */
    fun benjaminiHochberg(pValues: List<Double>, falseDiscoveryRate: Double): List<Boolean> {
        val m = pValues.size
        if (m == 0) return emptyList()
        val order = pValues.indices.sortedBy { pValues[it] }
        var cutoffRank = 0
        order.forEachIndexed { rank, index ->
            if (pValues[index] <= (rank + 1).toDouble() / m * falseDiscoveryRate) cutoffRank = rank + 1
        }
        val passing = order.take(cutoffRank).toSet()
        return pValues.indices.map { it in passing }
    }
}
