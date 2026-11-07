package lambda

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class Outcome(val points: Double) {
    WIN(1.0), LOSS(0.0), TIE(0.5);

    companion object {
        /** Shootout and overtime losses count as losses, as they do in the W-L-T record. */
        fun of(result: String): Outcome {
            val r = result.trim().uppercase().removePrefix("*")
            return when {
                r == "T" -> TIE
                r.endsWith("W") -> WIN
                else -> LOSS
            }
        }
    }
}

data class Record(val wins: Int, val losses: Int, val ties: Int) {
    val games: Int get() = wins + losses + ties

    /** Share of available points: a win is worth 1, a tie 1/2. */
    val pointsShare: Double get() = if (games == 0) 0.0 else (wins + ties * 0.5) / games

    override fun toString(): String = "$wins-$losses-$ties"

    companion object {
        fun of(outcomes: List<Outcome>) = Record(
            wins = outcomes.count { it == Outcome.WIN },
            losses = outcomes.count { it == Outcome.LOSS },
            ties = outcomes.count { it == Outcome.TIE },
        )
    }
}

/** The numbers insights are built from, derived from one archived game. */
class GameFacts(
    val game: ArchivedGame,
    /** Days since the team's previous game; null for the first game on record. */
    val daysSincePrevious: Long?,
) {
    val date: LocalDate? = game.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val startTime: LocalTime? = parseStartTime(game.timeText)
    val outcome: Outcome = Outcome.of(game.result)

    /** Shots on our goalie, and shots on theirs. Shots include goals. */
    val shotsAgainst: Int? = game.us.goalies.takeIf { it.isNotEmpty() }?.sumOf { it.shotsAgainst }?.takeIf { it > 0 }
    val shotsFor: Int? = game.them?.goalies?.takeIf { it.isNotEmpty() }?.sumOf { it.shotsAgainst }?.takeIf { it > 0 }

    val powerPlayGoalsFor: Int = game.us.goals.count { it.strength == "PP" }
    val powerPlayGoalsAgainst: Int? = game.them?.goals?.count { it.strength == "PP" }
    val shortHandedGoalsFor: Int = game.us.goals.count { it.strength == "SH" }

    val skaters: Int = game.us.roster.count { !it.isGoalie }
    val penaltiesTaken: Int = game.us.penalties.size
    val penaltiesDrawn: Int? = game.them?.penalties?.size

    /** True if we scored the first goal, false if they did, null without goals or their scoresheet. */
    val scoredFirst: Boolean? = run {
        val them = game.them ?: return@run null
        val ours = game.us.goals.mapNotNull { goalOrder(it) }.minOrNull()
        val theirs = them.goals.mapNotNull { goalOrder(it) }.minOrNull()
        when {
            ours == null && theirs == null -> null
            theirs == null -> true
            ours == null -> false
            ours == theirs -> null
            else -> ours < theirs
        }
    }

    companion object {
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        private val PERIODS = mapOf("1" to 1, "2" to 2, "3" to 3, "OT" to 4)

        /** Facts for every game, oldest first. */
        fun of(games: List<ArchivedGame>): List<GameFacts> {
            val sorted = games.sortedWith(compareBy<ArchivedGame> { it.date ?: "" }.thenBy { it.gameId.toLongOrNull() ?: 0L })
            var previous: LocalDate? = null
            return sorted.map { game ->
                val date = game.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val gap = if (date != null && previous != null) ChronoUnit.DAYS.between(previous, date) else null
                if (date != null) previous = date
                GameFacts(game, gap)
            }
        }

        fun parseStartTime(text: String?): LocalTime? =
            text?.trim()?.uppercase()?.let { runCatching { LocalTime.parse(it, TIME_FORMAT) }.getOrNull() }

        /**
         * Sorts goals into the order they were scored. The clock counts down, so within a period
         * a goal with more time left came first.
         */
        internal fun goalOrder(goal: Scoresheet.ScoringEvent): Int? {
            val period = PERIODS[goal.period.trim().uppercase()] ?: return null
            val clock = goal.time.split(":").takeIf { it.size == 2 } ?: return null
            val seconds = (clock[0].toIntOrNull() ?: return null) * 60 + (clock[1].toIntOrNull() ?: return null)
            return period * 100_000 - seconds
        }
    }
}
