package lambda

import lambda.InsightTestGames.DEFAULT_SKATERS
import lambda.InsightTestGames.game
import lambda.InsightTestGames.goal
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightEngineTest {
    private val engine = InsightEngine(Random(42))
    private val start = LocalDate.of(2025, 9, 7)
    private fun week(i: Int) = start.plusWeeks(i.toLong())

    /** Wins when opponents get 15 shots, losses when they get 35, alternating. */
    private val shotsGames = (0 until 60).map { i ->
        if (i % 2 == 0) game(i, week(i), "W", shotsAgainst = 15) else game(i, week(i), "L", shotsAgainst = 35)
    }

    private fun texts(games: List<ArchivedGame>, occasion: Occasion) = engine.candidates(games, occasion).map { it.text }

    @Test
    fun `finds a strong split and describes both sides`() {
        assertContains(
            texts(shotsGames, Occasion.Midweek),
            "We're 30-0-0 when we hold opponents under 20 shots, and 0-30-0 when we don't.",
        )
    }

    @Test
    fun `a recap leads with what happened in the game just played`() {
        assertContains(
            texts(shotsGames, Occasion.Recap("58")),
            "We held them under 20 shots in this one. We're now 30-0-0 when we hold opponents under 20 shots, and 0-30-0 when we don't.",
        )
    }

    @Test
    fun `splits without enough games on both sides are left out`() {
        val keys = engine.evaluateSplits(GameFacts.of(shotsGames)).map { it.split.key }
        assertTrue("venue-KVIC" !in keys, "every game was at KVIC")
        assertTrue("home" !in keys, "every game was at home")
    }

    @Test
    fun `players are only mentioned when the team does better with them`() {
        val games = (0 until 40).map { i ->
            val skaters = DEFAULT_SKATERS + listOfNotNull(if (i < 25) "Star" else null, if (i >= 15) "Jinx" else null)
            game(i, week(i), if (i < 25) "W" else "L", skaters = skaters)
        }
        val texts = texts(games, Occasion.Midweek)
        assertContains(texts, "We're 25-0-0 with Star in the lineup, and 0-15-0 without.")
        assertTrue(texts.none { "Jinx" in it }, texts.toString())
        val jinx = engine.evaluateSplits(GameFacts.of(games)).single { it.split.key == "with-Jinx" }
        assertTrue(jinx.significant && jinx.difference < 0, "the split is real but negative, so it's not posted")
    }

    @Test
    fun `an announcement mentions the upcoming venue when it matters`() {
        val games = (0 until 30).map { i ->
            if (i % 2 == 0) game(i, week(i), "W", arena = "OVA") else game(i, week(i), "L", arena = "KVIC")
        }
        val upcoming = Occasion.PreGame("Opponents", "OVA", LocalDateTime.of(2026, 9, 6, 21, 0), "HOME")
        assertContains(texts(games, upcoming), "This one's at OVA. We're 15-0-0 there, and 0-15-0 everywhere else.")
    }

    @Test
    fun `head-to-head record with the last meeting`() {
        val games = listOf(
            game(1, LocalDate.of(2025, 9, 21), "W", opponent = "Dumb Pucks", ourScore = 4, oppScore = 1),
            game(2, LocalDate.of(2025, 9, 28), "L", opponent = "Dumb Pucks", ourScore = 1, oppScore = 2),
            game(3, LocalDate.of(2025, 10, 5), "W", opponent = "Dumb Pucks", ourScore = 3, oppScore = 2),
        )
        val upcoming = Occasion.PreGame("Dumb Pucks", "KVIC", LocalDateTime.of(2025, 10, 12, 21, 0), "HOME")
        assertContains(texts(games, upcoming), "We're 2-1-0 all-time against Dumb Pucks. Last time: a 3-2 win on Oct 5, 2025.")
    }

    @Test
    fun `milestones reached in the game just played and within reach`() {
        val games = (1..10).map { i -> game(i, week(i), "W", goals = listOf(goal("Ava"))) }
        assertContains(texts(games, Occasion.Recap("10")), "Ava reached 10 goals for the team in this one.")

        val busy = (1..12).map { i -> game(i, week(i), "W", goals = List(4) { goal("Ava") }) }
        val midweek = texts(busy, Occasion.PreGame("Opponents", "KVIC", null, "HOME"))
        assertContains(midweek, "Ava is 2 goals away from 50 for the team.")
        assertContains(midweek, "Ava is 2 points away from 50 for the team.")
    }

    @Test
    fun `an assist connection in the game just played`() {
        val games = (1..5).map { i -> game(i, week(i), "W", goals = listOf(goal("Ben", assist1 = "Ava"))) }
        assertContains(
            texts(games, Occasion.Recap("5")),
            "Ava has now assisted on 5 of Ben's goals, including one in this game, more than any other pair on the team.",
        )
    }

    @Test
    fun `choose skips anything on cooldown`() {
        val now = Instant.parse("2026-11-07T00:00:00Z")
        val shots = Insight("shots-against-under-20", "shots-against", emptyList(), "shots", 5.0)
        val duo = Insight("duo-Ava-Ben-9", "duo", listOf("Ava", "Ben"), "duo", 4.0)
        val venue = Insight("venue-OVA", "venue", emptyList(), "venue", 3.0)
        val candidates = listOf(shots, duo, venue)

        val recent = listOf(
            PostedInsight("scored-first", "shots-against", emptyList(), now.minus(Duration.ofDays(5))),
            PostedInsight("milestone-Ava-goals-50", "milestone", listOf("Ava"), now.minus(Duration.ofDays(10))),
        )
        assertEquals(venue, engine.choose(candidates, recent, now), "same type and same player are on cooldown")

        val sameFactLastMonth = listOf(PostedInsight("shots-against-under-20", "shots-against", emptyList(), now.minus(Duration.ofDays(30))))
        val pick = engine.choose(listOf(shots), sameFactLastMonth, now)
        assertNull(pick, "the same fact waits 60 days, even though its type is off cooldown")

        assertNull(engine.choose(listOf(venue), emptyList(), now, minScore = 3.5))
        assertTrue(engine.choose(candidates, emptyList(), now) in candidates)
    }
}
