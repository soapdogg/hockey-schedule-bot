package lambda

/** Season-to-date stats from the league's `/team/{id}/stats` page. */
data class TeamStats(
    val skaters: List<Skater>,
    val goalies: List<Goalie>,
) {
    data class Skater(
        val name: String,
        val gamesPlayed: Int,
        val goals: Int,
        val assists: Int,
        val points: Int,
    )

    data class Goalie(
        val name: String,
        val gamesPlayed: Int,
        val goalsAgainstAverage: String,
        val savePercentage: String,
        val shutouts: Int,
    )
}
