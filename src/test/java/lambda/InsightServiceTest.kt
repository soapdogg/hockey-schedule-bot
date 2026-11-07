package lambda

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import lambda.InsightTestGames.game
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightServiceTest {
    private val archive = mockk<GameArchive>()
    private val log = mockk<InsightLog>()
    private val now = Instant.parse("2026-11-11T17:00:00Z")
    private val service = InsightService(archive, log, InsightEngine(kotlin.random.Random(1)), Clock.fixed(now, ZoneOffset.UTC))
    private val team = Team("Reindeer", "10159", "", "")
    private val games = (0 until 40).map { i ->
        val day = LocalDate.of(2025, 9, 7).plusWeeks(i.toLong())
        if (i % 2 == 0) game(i, day, "W", shotsAgainst = 15) else game(i, day, "L", shotsAgainst = 35)
    }

    @Test
    fun `picks an insight from the archive`() {
        every { log.recent("10159", any()) } returns emptyList()
        every { archive.games("10159") } returns games
        assertNotNull(service.pick(team, Occasion.Midweek))
    }

    @Test
    fun `no midweek post right after another insight`() {
        every { log.recent("10159", any()) } returns
            listOf(PostedInsight("scored-first", "first-goal", emptyList(), now.minus(Duration.ofDays(1))))
        every { archive.games("10159") } returns games
        assertNull(service.pick(team, Occasion.Midweek))
    }

    @Test
    fun `a failure means no insight rather than an error`() {
        every { log.recent(any(), any()) } throws RuntimeException("DynamoDB is down")
        assertNull(service.pick(team, Occasion.Recap("1")))

        every { log.record(any(), any(), any(), any()) } throws RuntimeException("DynamoDB is down")
        service.recordPosted(team, Insight("k", "t", emptyList(), "text", 1.0), Occasion.Midweek)
        verify { log.record("10159", any(), "midweek", now) }
    }

    @Test
    fun `the midweek run is Wednesday morning`() {
        assertTrue(InsightService.isMidweekRun(LocalDateTime.of(2026, 11, 11, 9, 0)))
        assertFalse(InsightService.isMidweekRun(LocalDateTime.of(2026, 11, 11, 20, 0)))
        assertFalse(InsightService.isMidweekRun(LocalDateTime.of(2026, 11, 12, 9, 0)))
        assertEquals("pre-game", InsightService.occasionName(Occasion.PreGame("x", null, null, null)))
    }
}
