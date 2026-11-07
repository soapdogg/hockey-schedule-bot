package lambda

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class GameArchiverTest {

    private val extractor = mockk<ScoresheetExtractor>()
    private val discoverer = mockk<SeasonDiscoverer>()
    private val archive = mockk<GameArchive>(relaxed = true)
    private val archiver = GameArchiver(extractor, discoverer, archive)
    private val team = Team("Reindeer5D", "10159", "", "")

    private val ourSide = Scoresheet(
        teamName = "Rainier Reindeer HC-6B",
        roster = (1..10).map { Scoresheet.RosterEntry("$it", "Player $it", isGoalie = false) },
        goals = emptyList(),
        penalties = emptyList(),
        goalies = emptyList(),
    )

    private fun game(id: String?, result: String? = "W") = Game(
        number = "1",
        date = "Mon, Nov 2",
        time = "9:00 PM",
        arena = "KVIC",
        ha = "HOME",
        opponent = "Opponent",
        opponentLink = "",
        hash = "hash-$id",
        gameId = id,
        result = result,
        score = if (result != null) "4 - 2" else null,
    )

    @Test
    fun `archives completed games that are not stored yet`() {
        every { archive.gameIds("10159") } returns setOf("1")
        every { discoverer.discoverCurrent("10159") } returns
            SeasonDiscoverer.DiscoveredSeason("2143", "Kraken Hockey League Fall/Winter 26/27")
        every { extractor.fetch("2") } returns ParsedScoresheet(LocalDate.of(2026, 11, 2), home = ourSide, away = null)

        val added = archiver.archiveNewGames(team, listOf(game("1"), game("2"), game(null, result = null)))

        assertEquals(1, added)
        verify(exactly = 1) {
            archive.put("10159", match { it.gameId == "2" && it.seasonId == "2143" && it.ourScore == 4 && it.date == "2026-11-02" })
        }
        verify(exactly = 0) { extractor.fetch("1") }
    }

    @Test
    fun `leaves a game whose scoresheet is not filled in for a later run`() {
        every { archive.gameIds(any()) } returns emptySet()
        every { discoverer.discoverCurrent(any()) } returns SeasonDiscoverer.DiscoveredSeason("2143", "Fall 26/27")
        every { extractor.fetch("2") } returns ParsedScoresheet(null, home = null, away = null)

        assertEquals(0, archiver.archiveNewGames(team, listOf(game("2"))))
        verify(exactly = 0) { archive.put(any(), any()) }
    }

    @Test
    fun `fetches nothing when every completed game is already archived`() {
        every { archive.gameIds(any()) } returns setOf("1")

        assertEquals(0, archiver.archiveNewGames(team, listOf(game("1"))))
        verify(exactly = 0) { discoverer.discoverCurrent(any()) }
        verify(exactly = 0) { extractor.fetch(any()) }
    }

    @Test
    fun `skips the archive lookup before any game has been played`() {
        assertEquals(0, archiver.archiveNewGames(team, listOf(game(null, result = null))))
        verify(exactly = 0) { archive.gameIds(any()) }
    }
}
