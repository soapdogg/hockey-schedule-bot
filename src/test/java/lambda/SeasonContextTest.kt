package lambda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SeasonContextTest {

    private fun game(result: String?) = Game(
        number = "1",
        date = "Mon, Nov 2",
        time = "9:00 PM",
        arena = "KVIC",
        ha = "HOME",
        opponent = "Opponent",
        opponentLink = "",
        hash = "hash",
        gameId = if (result != null) "1" else null,
        result = result,
    )

    private fun statsWithGoalieGamesPlayed(vararg gamesPlayed: Int) = TeamStats(
        skaters = emptyList(),
        goalies = gamesPlayed.mapIndexed { i, gp -> TeamStats.Goalie("Goalie $i", gp, "2.00", ".900", 0) },
    )

    @Test
    fun `record counts results and ignores games not yet played`() {
        val games = listOf(game("W"), game("T"), game("w"), game("L"), game("T"), game("T"), game(null))
        assertEquals("2-1-3 (W-L-T)", SeasonContext.from(games, null).record)
    }

    @Test
    fun `record always shows ties and appends other results only when they occur`() {
        assertEquals("1-0-0 (W-L-T)", SeasonContext.formatRecord(listOf("W")))
        assertEquals("1-1-0-1 (W-L-T-SOL)", SeasonContext.formatRecord(listOf("W", "SOL", "L")))
    }

    @Test
    fun `no record before any game is played`() {
        assertNull(SeasonContext.from(listOf(game(null)), null).record)
    }

    @Test
    fun `drops stats while the stats page is behind the schedule`() {
        val games = listOf(game("W"), game("L"), game("T"))
        assertNull(SeasonContext.from(games, statsWithGoalieGamesPlayed(2)).stats)
        assertNotNull(SeasonContext.from(games, statsWithGoalieGamesPlayed(3)).stats)
        assertNotNull(SeasonContext.from(games, statsWithGoalieGamesPlayed(2, 1)).stats, "games split between goalies")
    }
}
