package lambda

/**
 * The "season so far" part of a recap: the record from the schedule page, plus leaders from
 * the team stats page when that page has caught up with the schedule.
 */
data class SeasonContext(
    val record: String?,
    val stats: TeamStats?,
) {
    companion object {
        private val STANDARD_RESULTS = listOf("W", "L", "T")

        fun from(games: List<Game>, stats: TeamStats?): SeasonContext {
            val results = games.mapNotNull { g -> g.result?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } }
            return SeasonContext(
                record = formatRecord(results),
                stats = stats?.takeIf { isCaughtUp(it, results.size) },
            )
        }

        /** "2-1-3 (W-L-T)". Other results, such as SOL for shootout losses, are appended only when they occur. */
        internal fun formatRecord(results: List<String>): String? {
            if (results.isEmpty()) return null
            val counts = results.groupingBy { it }.eachCount()
            val categories = STANDARD_RESULTS + counts.keys.filter { it !in STANDARD_RESULTS }.sorted()
            val numbers = categories.joinToString("-") { (counts[it] ?: 0).toString() }
            return "$numbers (${categories.joinToString("-")})"
        }

        /**
         * Every completed game credits at least one goalie with a game played. If the goalies'
         * games played add up to fewer than the completed games, the stats page doesn't include
         * the latest game yet, and its leaders would be out of date.
         */
        internal fun isCaughtUp(stats: TeamStats, completedGames: Int): Boolean =
            stats.goalies.sumOf { it.gamesPlayed } >= completedGames
    }
}
