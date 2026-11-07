package lambda

import kotlinx.serialization.Serializable

/** One team's side of a league scoresheet. Goal and penalty times are the game clock, which counts down. */
@Serializable
data class Scoresheet(
    val teamName: String,
    val roster: List<RosterEntry>,
    val goals: List<ScoringEvent>,
    val penalties: List<PenaltyEvent>,
    val goalies: List<GoalieLine>,
) {
    @Serializable
    data class RosterEntry(
        val jersey: String,
        val name: String,
        val isGoalie: Boolean,
    )

    @Serializable
    data class ScoringEvent(
        val period: String,
        val time: String,
        val scorer: String,
        val assist1: String?,
        val assist2: String?,
        val strength: String,
    )

    @Serializable
    data class PenaltyEvent(
        val period: String,
        val time: String,
        val player: String,
        val infraction: String,
        val minutes: String,
    )

    @Serializable
    data class GoalieLine(
        val name: String,
        val minutes: String,
        val goalsAgainst: Int,
        val shotsAgainst: Int,
        val saves: Int,
    )
}
