package lambda

import java.time.LocalDate

/** Builds archived games for insight tests. */
object InsightTestGames {
    val DEFAULT_SKATERS = listOf("Ava", "Ben", "Cal", "Dee", "Eli", "Fay", "Gus", "Hal", "Ida", "Jo")

    fun goal(scorer: String, assist1: String? = null, period: String = "2", time: String = "10:00", strength: String = "EV") =
        Scoresheet.ScoringEvent(period, time, scorer, assist1, null, strength)

    fun game(
        id: Int,
        date: LocalDate,
        result: String,
        shotsAgainst: Int = 25,
        shotsFor: Int = 25,
        skaters: List<String> = DEFAULT_SKATERS,
        goalie: String = "Gil",
        goals: List<Scoresheet.ScoringEvent> = emptyList(),
        theirGoals: List<Scoresheet.ScoringEvent> = emptyList(),
        arena: String = "KVIC",
        time: String = "9:00 PM",
        opponent: String = "Opponents",
        homeAway: String = "HOME",
        seasonId: String = "1",
        ourScore: Int = goals.size,
        oppScore: Int = theirGoals.size,
    ) = ArchivedGame(
        gameId = id.toString(),
        seasonId = seasonId,
        seasonLabel = "Season $seasonId",
        date = date.toString(),
        dateText = date.toString(),
        timeText = time,
        arena = arena,
        opponent = opponent,
        homeAway = homeAway,
        result = result,
        ourScore = ourScore,
        oppScore = oppScore,
        us = Scoresheet(
            teamName = "Rainier Reindeer",
            roster = skaters.mapIndexed { i, name -> Scoresheet.RosterEntry("${i + 2}", name, isGoalie = false) } +
                Scoresheet.RosterEntry("1", goalie, isGoalie = true),
            goals = goals,
            penalties = emptyList(),
            goalies = listOf(Scoresheet.GoalieLine(goalie, "54:00", oppScore, shotsAgainst, shotsAgainst - oppScore)),
        ),
        them = Scoresheet(
            teamName = opponent,
            roster = emptyList(),
            goals = theirGoals,
            penalties = emptyList(),
            goalies = listOf(Scoresheet.GoalieLine("Their Goalie", "54:00", ourScore, shotsFor, shotsFor - ourScore)),
        ),
    )
}
