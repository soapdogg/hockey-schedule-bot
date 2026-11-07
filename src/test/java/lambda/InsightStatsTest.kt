package lambda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightStatsTest {

    @Test
    fun `benjamini-hochberg keeps p-values under the step-up line`() {
        // Sorted: 0.01 <= 0.025, 0.03 <= 0.05, 0.04 <= 0.075, 0.20 > 0.10.
        assertEquals(
            listOf(true, true, true, false),
            InsightStats.benjaminiHochberg(listOf(0.01, 0.04, 0.03, 0.20), falseDiscoveryRate = 0.10),
        )
        assertEquals(listOf(false, false), InsightStats.benjaminiHochberg(listOf(0.5, 0.9), 0.10))
        assertEquals(emptyList(), InsightStats.benjaminiHochberg(emptyList(), 0.10))
    }

    @Test
    fun `a clear difference gets a small p-value and no difference a large one`() {
        val wins = List(20) { 1.0 }
        val losses = List(20) { 0.0 }
        assertTrue(InsightStats.permutationPValue(wins, losses, seed = 1) < 0.001)

        val mixed = List(20) { if (it % 2 == 0) 1.0 else 0.0 }
        assertTrue(InsightStats.permutationPValue(mixed, mixed, seed = 1) > 0.9)
    }

    @Test
    fun `the same seed gives the same p-value`() {
        val a = listOf(1.0, 1.0, 0.5, 0.0, 1.0, 1.0)
        val b = listOf(0.0, 0.5, 0.0, 1.0, 0.0, 0.0)
        assertEquals(InsightStats.permutationPValue(a, b, seed = 7), InsightStats.permutationPValue(a, b, seed = 7))
    }
}
