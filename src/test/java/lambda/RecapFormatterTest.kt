package lambda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecapFormatterTest {

    private val baseGame = Game(
        number = "1",
        date = "Mon, May 11",
        time = "7:45 PM",
        arena = "KVIC",
        ha = "AWAY",
        opponent = "Dirty Couch Hockey Club",
        opponentLink = "/team/17768/home",
        hash = "abc123",
        gameId = "55483",
        result = "L",
        score = "8 - 2",
    )

    private fun teamLine(name: String, total: Int, periods: List<Int>) =
        Recap.TeamLine(name = name, periods = periods, total = total)

    @Test
    fun `formats a typical recap into a single message`() {
        val recap = Recap(
            teams = listOf(
                teamLine("Rainier Reindeer HC-6", 2, listOf(0, 0, 2)),
                teamLine("Dirty Couch Hockey Club", 8, listOf(1, 5, 2)),
            ),
            goals = listOf(
                Recap.Goal("1ST PERIOD", "5:52", "Dirty Couch Hockey Club", "Tyler Smith", listOf("Jon Cargille")),
                Recap.Goal("3RD PERIOD", "16:24", "Rainier Reindeer HC-6", "Jazz Palley", emptyList()),
            ),
            penalties = listOf(
                Recap.Penalty("3RD PERIOD", "1:32", "Rainier Reindeer HC-6", "Eddie Parker", "Body Check", "2:00"),
            ),
            shots = listOf(
                teamLine("Rainier Reindeer HC-6", 30, listOf(8, 12, 10)),
                teamLine("Dirty Couch Hockey Club", 31, listOf(5, 15, 11)),
            ),
        )

        val msgs = RecapFormatter.format(baseGame, recap)
        assertEquals(1, msgs.size)
        val text = msgs[0]
        assertTrue(text.contains("FINAL"))
        assertTrue(text.contains("Rainier Reindeer HC-6 2"))
        assertTrue(text.contains("— 8 Dirty Couch Hockey Club"))
        assertTrue(text.contains("Tyler Smith"))
        assertTrue(text.contains("Jon Cargille"))
        assertTrue(text.contains("unassisted"))
        assertTrue(text.contains("Body Check"))
        assertTrue(text.contains("(2:00)"))
        assertTrue(text.contains("Shots:"))
        assertTrue(text.contains("Tyler Smith (DCH)"))
        assertTrue(text.contains("Eddie Parker (RRH)"))
    }

    @Test
    fun `teamTag abbreviates names sensibly`() {
        assertEquals("DCH", RecapFormatter.teamTag("Dirty Couch Hockey Club"))
        assertEquals("RRH", RecapFormatter.teamTag("Rainier Reindeer HC-6"))
        assertEquals("REA", RecapFormatter.teamTag("Reapers"))
        assertEquals("BSO", RecapFormatter.teamTag("BSOD"))
    }

    @Test
    fun `formats zero goals and zero penalties without crashing`() {
        val recap = Recap(
            teams = listOf(
                teamLine("Team A", 0, listOf(0, 0, 0)),
                teamLine("Team B", 0, listOf(0, 0, 0)),
            ),
            goals = emptyList(),
            penalties = emptyList(),
            shots = emptyList(),
        )
        val msgs = RecapFormatter.format(baseGame, recap)
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].contains("No goals"))
        assertTrue(msgs[0].contains("None"))
    }

    @Test
    fun `chunks long recaps across multiple messages`() {
        val manyGoals = (1..100).map {
            Recap.Goal("1ST PERIOD", "$it:00", "Some Long Team Name Here", "Player Number $it", listOf("Assist $it"))
        }
        val recap = Recap(
            teams = listOf(
                teamLine("Some Long Team Name Here", 100, listOf(100, 0, 0)),
                teamLine("Other Team", 0, listOf(0, 0, 0)),
            ),
            goals = manyGoals,
            penalties = emptyList(),
            shots = emptyList(),
        )
        val msgs = RecapFormatter.format(baseGame, recap)
        assertTrue(msgs.size > 1, "expected chunking, got ${msgs.size} message(s)")
        msgs.forEach { assertTrue(it.length <= 1900, "chunk too large: ${it.length}") }
    }
}
