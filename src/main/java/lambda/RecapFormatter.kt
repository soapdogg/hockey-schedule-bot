package lambda

object RecapFormatter {
    private const val DISCORD_MAX_CHARS = 1900

    fun format(game: Game, recap: Recap): List<String> {
        val lines = buildLines(game, recap)
        return chunk(lines, DISCORD_MAX_CHARS)
    }

    private fun buildLines(game: Game, recap: Recap): List<String> {
        val out = mutableListOf<String>()
        val a = recap.teams[0]
        val b = recap.teams[1]
        out += ":ice_hockey: **FINAL** — Game #${game.number} — ${game.date}"
        out += "**${a.name} ${a.total} — ${b.total} ${b.name}**"
        if (recap.shots.size == 2) {
            out += "Shots: ${recap.shots[0].name} ${recap.shots[0].total}, ${recap.shots[1].name} ${recap.shots[1].total}"
        }

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
                out += "• ${g.time} — ${g.scorer} (${teamTag(g.team)}) — $assistText"
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
