package lambda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlayerAliasesTest {

    @Test
    fun `maps other spellings to one name and leaves the rest alone`() {
        assertEquals("Dzucka Lin", PlayerAliases.canonical("Dzuka Lin"))
        assertEquals("Dzucka Lin", PlayerAliases.canonical("Dzuka Lynn"))
        assertEquals("Dzucka Lin", PlayerAliases.canonical("Dzucka Lin"))
        assertEquals("Yazz Henderson", PlayerAliases.canonical("Yossi"))
        assertEquals("Tara Parker", PlayerAliases.canonical("Tara Parker"))
    }

    @Test
    fun `applies to every name on a scoresheet`() {
        val sheet = Scoresheet(
            teamName = "Rainier Reindeer",
            roster = listOf(
                Scoresheet.RosterEntry("9", "Mike Scanner", isGoalie = false),
                Scoresheet.RosterEntry("1", "Yossi", isGoalie = true),
            ),
            goals = listOf(Scoresheet.ScoringEvent("2", "5:00", "Dave Wilson", "Eddy Parker", "Ray Haro", "EV")),
            penalties = listOf(Scoresheet.PenaltyEvent("3", "1:00", "Andrew Girovyi", "Tripping", "2")),
            goalies = listOf(Scoresheet.GoalieLine("Yossi Henderson", "54:00", 2, 20, 18)),
        )

        val merged = PlayerAliases.apply(sheet)

        assertEquals(listOf("Mike Scanlon", "Yazz Henderson"), merged.roster.map { it.name })
        val goal = merged.goals.single()
        assertEquals(listOf("Dade Wilson", "Eddie Parker", "Raymond Haro"), listOf(goal.scorer, goal.assist1, goal.assist2))
        assertEquals("Andrew Gaiovyi", merged.penalties.single().player)
        assertEquals("Yazz Henderson", merged.goalies.single().name)
    }
}
