package lambda

import kotlinx.serialization.Serializable

/**
 * A completed game as stored in the archive. The result and score come from the league's
 * schedule, which is authoritative: scoresheets occasionally disagree with the final score,
 * and a shootout winner counts in the score without appearing as a goal.
 */
@Serializable
data class ArchivedGame(
    val gameId: String,
    val seasonId: String,
    val seasonLabel: String,
    /** ISO date (yyyy-MM-dd) from the scoresheet title. */
    val date: String?,
    val dateText: String,
    val timeText: String?,
    val arena: String?,
    val opponent: String,
    val homeAway: String?,
    /** Official result: W, L, T, SOL, ... */
    val result: String,
    val ourScore: Int,
    val oppScore: Int,
    val us: Scoresheet,
    val them: Scoresheet?,
) {
    companion object {
        private const val MIN_ROSTER_FOR_READY = 3

        /** Null until the game has an official result and our side of the scoresheet is filled in. */
        fun from(game: Game, sheet: ParsedScoresheet, seasonId: String, seasonLabel: String): ArchivedGame? {
            val gameId = game.gameId ?: return null
            val result = game.result?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            val (ourScore, oppScore) = officialScore(result, game.score) ?: return null
            val (us, them) = sheet.sides(game.ha) ?: return null
            if (us.roster.size < MIN_ROSTER_FOR_READY) return null
            return ArchivedGame(
                gameId = gameId,
                seasonId = seasonId,
                seasonLabel = seasonLabel,
                date = sheet.date?.toString(),
                dateText = game.date,
                timeText = game.time.takeIf { it.isNotBlank() },
                arena = game.arena.takeIf { it.isNotBlank() },
                opponent = game.opponent,
                homeAway = game.ha.takeIf { it.isNotBlank() },
                result = result,
                ourScore = ourScore,
                oppScore = oppScore,
                us = us,
                them = them,
            )
        }

        /**
         * The schedule lists the higher score first ("5 - 3"), whoever won, so the result
         * says which number is ours.
         */
        internal fun officialScore(result: String, score: String?): Pair<Int, Int>? {
            val numbers = Regex("\\d+").findAll(score ?: return null).map { it.value.toInt() }.toList()
            if (numbers.size != 2) return null
            val high = maxOf(numbers[0], numbers[1])
            val low = minOf(numbers[0], numbers[1])
            return when {
                result == "W" || result.endsWith("W") -> high to low
                result == "L" || result.endsWith("L") -> low to high
                else -> numbers[0] to numbers[1]
            }
        }
    }
}
