package lambda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private val typicalRecap = Recap(
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

    private val season = SeasonContext(
        record = "2-1-3 (W-L-T)",
        stats = TeamStats(
            skaters = listOf(
                TeamStats.Skater("Jarrett Schultz", 4, 3, 2, 5),
                TeamStats.Skater("Chris Flynn", 6, 5, 5, 10),
                TeamStats.Skater("Tyler Smith", 2, 3, 1, 4),
                TeamStats.Skater("Jazz Palley", 4, 2, 1, 3),
                TeamStats.Skater("Eric Soppi", 0, 0, 0, 0),
            ),
            goalies = listOf(
                TeamStats.Goalie("Yazz Henderson", 0, "0.00", ".000", 0),
                TeamStats.Goalie("Eric Soppi", 6, "2.67", ".876", 1),
            ),
        ),
    )

    @Test
    fun `formats a typical recap into a single message`() {
        val msgs = RecapFormatter.format(baseGame, typicalRecap)
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
    fun `ends with the insight when there is one`() {
        val msgs = RecapFormatter.format(baseGame, typicalRecap, insight = "We scored first in this one.")
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].endsWith("\n\n📊 We scored first in this one."), msgs[0])
        assertTrue(RecapFormatter.format(baseGame, typicalRecap).none { "📊" in it })
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

    @Test
    fun `adds our goaltending from the opponent's shots and goals`() {
        val text = RecapFormatter.format(baseGame, typicalRecap).single()
        assertTrue(text.contains("Goaltending: 23 saves on 31 shots (.742)"), text)
    }

    @Test
    fun `skips goaltending for shootout results`() {
        val text = RecapFormatter.format(baseGame.copy(result = "SOL"), typicalRecap).single()
        assertFalse(text.contains("Goaltending"), text)
    }

    @Test
    fun `skips goaltending when the opponent name does not match the summary`() {
        val text = RecapFormatter.format(baseGame.copy(opponent = "Somebody Else"), typicalRecap).single()
        assertFalse(text.contains("Goaltending"), text)
    }

    @Test
    fun `appends season so far with record, top three scorers and goalies who have played`() {
        val text = RecapFormatter.format(baseGame, typicalRecap, season).single()
        assertTrue(text.contains("**Season so far**"), text)
        assertTrue(text.contains("Record: 2-1-3 (W-L-T)"), text)
        assertTrue(
            text.contains("Points: Chris Flynn 10 (5G, 5A) · Jarrett Schultz 5 (3G, 2A) · Tyler Smith 4 (3G, 1A)"),
            text,
        )
        assertFalse(text.contains("Jazz Palley 3 ("), text)
        assertTrue(text.contains("Goalie: Eric Soppi .876 SV%, 2.67 GAA, 1 SO"), text)
        assertFalse(text.contains("Yazz Henderson"), text)
        assertTrue(text.indexOf("**Season so far**") > text.indexOf("**Penalties**"), text)
    }

    @Test
    fun `shows only the record when season stats are unavailable`() {
        val text = RecapFormatter.format(baseGame, typicalRecap, SeasonContext("2-1-3 (W-L-T)", null)).single()
        assertTrue(text.contains("Record: 2-1-3 (W-L-T)"), text)
        assertFalse(text.contains("Points:"), text)
        assertFalse(text.contains("Goalie:"), text)
    }

    @Test
    fun `omits the season block when there is nothing to show`() {
        val text = RecapFormatter.format(baseGame, typicalRecap, SeasonContext(null, null)).single()
        assertFalse(text.contains("Season so far"), text)
    }

    @Test
    fun `labels goal types such as power-play goals`() {
        val recap = typicalRecap.copy(
            goals = listOf(
                Recap.Goal("1ST PERIOD", "0:04", "Rainier Reindeer HC-6", "Jazz Palley", listOf("Dzucka Lin"), type = "PPG"),
            ),
        )
        val text = RecapFormatter.format(baseGame, recap).single()
        assertTrue(text.contains("• 0:04 — Jazz Palley (RRH, PPG) — Dzucka Lin"), text)
    }
}
