package lambda

data class Recap(
    val teams: List<TeamLine>,
    val goals: List<Goal>,
    val penalties: List<Penalty>,
    val shots: List<TeamLine>,
) {
    data class TeamLine(
        val name: String,
        val periods: List<Int>,
        val total: Int,
    )

    data class Goal(
        val period: String,
        val time: String,
        val team: String,
        val scorer: String,
        val assists: List<String>,
        /** Goal type badge from the league site, e.g. "PPG". Null for even-strength goals. */
        val type: String? = null,
    )

    data class Penalty(
        val period: String,
        val time: String,
        val team: String,
        val player: String,
        val infraction: String,
        val duration: String,
    )
}
