package lambda

import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArchivedGameTest {

    private val sheet = ScoresheetExtractor(mockk()).parse(
        Jsoup.parse(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/scoresheet_51961.html"))
                .bufferedReader().use { it.readText() }
        )
    )

    private val game = Game(
        number = "3",
        date = "Sun, May 18",
        time = "9:15 PM",
        arena = "KVIC",
        ha = "AWAY",
        opponent = "Razor Clams",
        opponentLink = "",
        hash = "hash",
        gameId = "51961",
        result = "L",
        score = "5 - 3",
    )

    @Test
    fun `builds from the official result and both sides of the scoresheet`() {
        val archived = assertNotNull(ArchivedGame.from(game, sheet, "2129", "Summer 2025"))
        assertEquals("L", archived.result)
        assertEquals(3, archived.ourScore)
        assertEquals(5, archived.oppScore)
        assertEquals("2025-05-18", archived.date)
        assertEquals("Rainier Reindeer HC-6B", archived.us.teamName)
        assertEquals("Razor Clams", archived.them?.teamName)
        assertEquals("2129", archived.seasonId)
    }

    @Test
    fun `is not ready without an official result`() {
        assertNull(ArchivedGame.from(game.copy(result = null), sheet, "2129", "Summer 2025"))
        assertNull(ArchivedGame.from(game.copy(score = null), sheet, "2129", "Summer 2025"))
    }

    @Test
    fun `is not ready without a home or away column, or before our roster is filled in`() {
        assertNull(ArchivedGame.from(game.copy(ha = ""), sheet, "2129", "Summer 2025"))
        val emptyRoster = sheet.copy(away = sheet.away?.copy(roster = emptyList()))
        assertNull(ArchivedGame.from(game, emptyRoster, "2129", "Summer 2025"))
    }

    @Test
    fun `officialScore reads the schedule's higher-first score using the result`() {
        assertEquals(10 to 5, ArchivedGame.officialScore("W", "10 - 5"))
        assertEquals(3 to 5, ArchivedGame.officialScore("L", "5 - 3"))
        assertEquals(2 to 3, ArchivedGame.officialScore("SOL", "3 - 2"))
        assertEquals(4 to 4, ArchivedGame.officialScore("T", "4 - 4"))
        assertNull(ArchivedGame.officialScore("W", null))
        assertNull(ArchivedGame.officialScore("W", "5"))
    }
}
