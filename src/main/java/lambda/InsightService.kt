package lambda

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

/**
 * Chooses insights for posts and records what was posted. Insights are extras: any failure
 * here is logged and the post goes out without one.
 */
class InsightService(
    private val archive: GameArchive,
    private val log: InsightLog,
    private val engine: InsightEngine = InsightEngine(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val gamesByTeam = mutableMapOf<String, List<ArchivedGame>>()

    fun pick(team: Team, occasion: Occasion): Insight? = try {
        val now = clock.instant()
        val recent = log.recent(team.krakenWebsiteId, now.minus(InsightEngine.KEY_COOLDOWN))
        if (occasion == Occasion.Midweek && recent.any { it.postedAt.isAfter(now.minus(MIDWEEK_QUIET_PERIOD)) }) {
            null
        } else {
            val games = gamesByTeam.getOrPut(team.krakenWebsiteId) { archive.games(team.krakenWebsiteId) }
            val minScore = if (occasion == Occasion.Midweek) InsightEngine.MIDWEEK_MIN_SCORE else 0.0
            engine.choose(engine.candidates(games, occasion), recent, now, minScore)
        }
    } catch (e: Exception) {
        println("WARNING: Could not pick an insight for ${team.name}: ${e.message}")
        null
    }

    fun recordPosted(team: Team, insight: Insight, occasion: Occasion) {
        try {
            log.record(team.krakenWebsiteId, insight, occasionName(occasion), clock.instant())
        } catch (e: Exception) {
            println("WARNING: Could not record posted insight ${insight.key}: ${e.message}")
        }
    }

    companion object {
        /** No midweek post if any insight went out in the last few days. */
        val MIDWEEK_QUIET_PERIOD: Duration = Duration.ofDays(3)

        /** Announcements only get an insight when the game is this close; schedules can be posted weeks ahead. */
        val PRE_GAME_HORIZON: Duration = Duration.ofDays(8)

        /** The Wednesday morning run. */
        fun isMidweekRun(now: LocalDateTime): Boolean = now.dayOfWeek == DayOfWeek.WEDNESDAY && now.hour < 12

        fun occasionName(occasion: Occasion): String = when (occasion) {
            is Occasion.Recap -> "recap"
            is Occasion.PreGame -> "pre-game"
            Occasion.Midweek -> "midweek"
        }
    }
}
