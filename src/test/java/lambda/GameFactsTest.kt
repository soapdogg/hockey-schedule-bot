package lambda

import lambda.InsightTestGames.game
import lambda.InsightTestGames.goal
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameFactsTest {
    private val day = LocalDate.of(2026, 10, 4)

    @Test
    fun `outcomes count shootout losses as losses`() {
        assertEquals(Outcome.WIN, Outcome.of("W"))
        assertEquals(Outcome.LOSS, Outcome.of("L"))
        assertEquals(Outcome.TIE, Outcome.of("T"))
        assertEquals(Outcome.LOSS, Outcome.of("SOL"))
        assertEquals(Outcome.WIN, Outcome.of("*W"))
        assertEquals("1-1-1", Record.of(listOf(Outcome.WIN, Outcome.LOSS, Outcome.TIE)).toString())
        assertEquals(0.5, Record.of(listOf(Outcome.WIN, Outcome.LOSS, Outcome.TIE)).pointsShare)
    }

    @Test
    fun `the first goal is the earliest period, then the most time left on the clock`() {
        val theyScoredEarlier = game(1, day, "W", goals = listOf(goal("Ava", period = "2", time = "15:00")), theirGoals = listOf(goal("X", period = "1", time = "1:00")))
        assertEquals(false, GameFacts(theyScoredEarlier, null).scoredFirst)

        val weScoredEarlier = game(2, day, "W", goals = listOf(goal("Ava", period = "1", time = "14:00")), theirGoals = listOf(goal("X", period = "1", time = "10:00")))
        assertEquals(true, GameFacts(weScoredEarlier, null).scoredFirst)

        assertEquals(true, GameFacts(game(3, day, "W", goals = listOf(goal("Ava"))), null).scoredFirst)
        assertNull(GameFacts(game(4, day, "T"), null).scoredFirst)
    }

    @Test
    fun `derives shots, power plays, bench size and start time`() {
        val g = game(
            1, day, "W", shotsAgainst = 18, shotsFor = 31, time = "10:30 PM",
            goals = listOf(goal("Ava", strength = "PP"), goal("Ben", strength = "SH")),
            theirGoals = listOf(goal("X", strength = "PP")),
        )
        val facts = GameFacts(g, null)
        assertEquals(18, facts.shotsAgainst)
        assertEquals(31, facts.shotsFor)
        assertEquals(1, facts.powerPlayGoalsFor)
        assertEquals(1, facts.powerPlayGoalsAgainst)
        assertEquals(1, facts.shortHandedGoalsFor)
        assertEquals(10, facts.skaters)
        assertEquals(LocalTime.of(22, 30), facts.startTime)
    }

    @Test
    fun `zero shots means the shots weren't recorded`() {
        assertNull(GameFacts(game(1, day, "W", shotsAgainst = 0), null).shotsAgainst)
    }

    @Test
    fun `games are put in date order with the days since the previous one`() {
        val facts = GameFacts.of(listOf(game(2, day.plusDays(14), "W"), game(1, day, "L"), game(3, day.plusDays(21), "T")))
        assertEquals(listOf("1", "2", "3"), facts.map { it.game.gameId })
        assertEquals(listOf(null, 14L, 7L), facts.map { it.daysSincePrevious })
        assertTrue(facts.first().outcome == Outcome.LOSS)
        assertFalse(facts.last().outcome == Outcome.WIN)
    }
}
