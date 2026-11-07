package lambda

/**
 * Archives completed games from the team's current schedule that aren't stored yet. Games
 * whose scoresheet isn't filled in are skipped and picked up on a later run.
 */
class GameArchiver(
    private val scoresheetExtractor: ScoresheetExtractor,
    private val seasonDiscoverer: SeasonDiscoverer,
    private val archive: GameArchive,
) {
    /** Returns how many games were added. */
    fun archiveNewGames(team: Team, games: List<Game>): Int {
        val completed = games.filter { it.gameId != null && !it.result.isNullOrBlank() }
        if (completed.isEmpty()) return 0
        val known = archive.gameIds(team.krakenWebsiteId)
        val missing = completed.filter { it.gameId !in known }
        if (missing.isEmpty()) return 0

        val season = seasonDiscoverer.discoverCurrent(team.krakenWebsiteId)
        if (season == null) {
            println("WARNING: Could not determine the current season for ${team.name}; not archiving games.")
            return 0
        }

        var added = 0
        for (game in missing) {
            val gameId = game.gameId ?: continue
            val archived = ArchivedGame.from(game, scoresheetExtractor.fetch(gameId), season.seasonId, season.label)
            if (archived == null) {
                println("Scoresheet for game $gameId is not ready yet; will archive it on a later run.")
                continue
            }
            archive.put(team.krakenWebsiteId, archived)
            added += 1
        }
        return added
    }
}
