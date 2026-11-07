package lambda

import java.util.Locale

object RecapFormatter {
    private const val DISCORD_MAX_CHARS = 1900

    fun format(game: Game, recap: Recap, season: SeasonContext? = null, insight: String? = null): List<String> {
        val lines = buildLines(game, recap) +
            (season?.let { seasonLines(it) } ?: emptyList()) +
            (insight?.let { listOf("", insightLine(it)) } ?: emptyList())
        return chunk(lines, DISCORD_MAX_CHARS)
    }

    fun insightLine(insight: String): String = "📊 $insight"

    private fun buildLines(game: Game, recap: Recap): List<String> {
        val out = mutableListOf<String>()
        val a = recap.teams[0]
        val b = recap.teams[1]
        out += ":ice_hockey: **FINAL** — Game #${game.number} — ${game.date}"
        out += "**${a.name} ${a.total} — ${b.total} ${b.name}**"
        if (recap.shots.size == 2) {
            out += "Shots: ${recap.shots[0].name} ${recap.shots[0].total}, ${recap.shots[1].name} ${recap.shots[1].total}"
        }
        goaltendingLine(game, recap)?.let { out += it }

        out += ""
        out += "**Scoring**"
        if (recap.goals.isEmpty()) {
            out += "_No goals._"
        } else {
            var lastPeriod: String? = null
            for (g in recap.goals) {
                if (g.period != lastPeriod) {
                    if (lastPeriod != null) out += ""
                    out += "*${g.period}*"
                    lastPeriod = g.period
                }
                val assistText = if (g.assists.isEmpty()) "unassisted" else g.assists.joinToString(", ")
                val tag = listOfNotNull(teamTag(g.team), g.type).joinToString(", ")
                out += "• ${g.time} — ${g.scorer} ($tag) — $assistText"
            }
        }

        out += ""
        out += "**Penalties**"
        if (recap.penalties.isEmpty()) {
            out += "_None._"
        } else {
            for (p in recap.penalties) {
                val periodShort = p.period.replace(" PERIOD", "", ignoreCase = true).lowercase()
                out += "• $periodShort ${p.time} (${p.duration}) — ${p.player} (${teamTag(p.team)}) — ${p.infraction}"
            }
        }
        return out
    }

    /**
     * Our goalie's saves: the opponent's shots minus the opponent's goals. Skipped for shootout
     * results, because the final score then includes a shootout goal that wasn't a shot.
     */
    internal fun goaltendingLine(game: Game, recap: Recap): String? {
        if (game.result?.trim()?.uppercase()?.startsWith("SO") == true) return null
        val opponentGoals = recap.teams.firstOrNull { sameTeam(it.name, game.opponent) }?.total ?: return null
        val opponentShots = recap.shots.firstOrNull { sameTeam(it.name, game.opponent) }?.total ?: return null
        val saves = opponentShots - opponentGoals
        if (opponentShots <= 0 || saves < 0) return null
        val pct = String.format(Locale.US, "%.3f", saves.toDouble() / opponentShots).removePrefix("0")
        return "Goaltending: $saves saves on $opponentShots shots ($pct)"
    }

    private fun seasonLines(season: SeasonContext): List<String> {
        val lines = mutableListOf<String>()
        season.record?.let { lines += "Record: $it" }
        season.stats?.let { stats ->
            val leaders = stats.skaters
                .filter { it.points > 0 }
                .sortedWith(compareByDescending<TeamStats.Skater> { it.points }.thenByDescending { it.goals })
                .take(Fixtures.RECAP_TOP_SCORERS)
            if (leaders.isNotEmpty()) {
                lines += "Points: " + leaders.joinToString(" · ") { "${it.name} ${it.points} (${it.goals}G, ${it.assists}A)" }
            }
            val goalies = stats.goalies.filter { it.gamesPlayed > 0 }.sortedByDescending { it.gamesPlayed }
            if (goalies.isNotEmpty()) {
                val label = if (goalies.size == 1) "Goalie" else "Goalies"
                lines += "$label: " + goalies.joinToString(" · ") { g ->
                    val shutouts = if (g.shutouts > 0) ", ${g.shutouts} SO" else ""
                    "${g.name} ${g.savePercentage} SV%, ${g.goalsAgainstAverage} GAA$shutouts"
                }
            }
        }
        if (lines.isEmpty()) return emptyList()
        return listOf("", "**Season so far**") + lines
    }

    private fun sameTeam(a: String, b: String): Boolean {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return key(a).isNotEmpty() && key(a) == key(b)
    }

    internal fun teamTag(name: String): String {
        val uppers = name.filter { it.isUpperCase() }
        if (uppers.length >= 3) return uppers.take(3)
        val letters = name.filter { it.isLetter() }.uppercase()
        return letters.take(3).ifEmpty { name.take(3).uppercase() }
    }

    private fun chunk(lines: List<String>, maxChars: Int): List<String> {
        val messages = mutableListOf<String>()
        val current = StringBuilder()
        for (line in lines) {
            val addedLen = if (current.isEmpty()) line.length else line.length + 1
            if (current.length + addedLen > maxChars && current.isNotEmpty()) {
                messages += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
        if (current.isNotEmpty()) messages += current.toString()
        return messages
    }
}
