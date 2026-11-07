package lambda

import java.time.LocalDateTime

/**
 * Recaps are only posted for games that started within the last few days. Without this, the
 * first run after deploying (or after the dedupe table is cleared) would post a recap for
 * every completed game of the season at once.
 */
object RecapWindow {
    fun isOpen(now: LocalDateTime, game: Game, maxAgeDays: Long = Fixtures.RECAP_MAX_AGE_DAYS): Boolean {
        val start = GameDateParser.parse(now, "${game.date} @ ${game.time}")
        return !start.isAfter(now) && start.isAfter(now.minusDays(maxAgeDays))
    }
}
