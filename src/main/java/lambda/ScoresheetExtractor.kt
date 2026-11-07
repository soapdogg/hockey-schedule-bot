package lambda

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Both sides of a league scoresheet, plus the game date from its title. */
data class ParsedScoresheet(
    val date: LocalDate?,
    val home: Scoresheet?,
    val away: Scoresheet?,
) {
    /** Our side and the opponent's, chosen by the schedule's "HOME"/"AWAY" column. */
    fun sides(homeAway: String?): Pair<Scoresheet, Scoresheet?>? =
        when (homeAway?.trim()?.uppercase()) {
            "HOME" -> home?.let { it to away }
            "AWAY" -> away?.let { it to home }
            else -> null
        }
}

class ScoresheetExtractor(
    private val httpClient: HttpClient,
) {
    fun fetch(gameId: String): ParsedScoresheet {
        val url = "${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/scoresheet-complete/$gameId"
        return parse(Jsoup.parse(httpClient.executeHttpGetRequest(url)))
    }

    fun parse(doc: Document): ParsedScoresheet =
        ParsedScoresheet(
            date = parseDate(doc),
            home = parseSide(doc, Side.HOME),
            away = parseSide(doc, Side.AWAY),
        )

    private enum class Side(val label: String) { HOME("HOME"), AWAY("AWAY") }

    private enum class Section { NONE, ROSTER_TEAM_NAME, ROSTER, SCORING, GOALIE, PENALTY }

    private fun parseDate(doc: Document): LocalDate? {
        val text = doc.title() + " " + (doc.body()?.text() ?: "")
        val match = TITLE_DATE.find(text) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1], TITLE_DATE_FORMAT)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseSide(doc: Document, side: Side): Scoresheet? {
        val rosterMarker = "${side.label} TEAM"
        val scoringMarker = "${side.label} TEAM SCORING SUMMARY"
        val goalieMarker = "${side.label} GOALIE STATS"
        val penaltyMarker = "${side.label} TEAM PENALTY SUMMARY"
        val otherSideMarkerPrefix = "${otherSide(side).label} "

        var teamName = ""
        val roster = mutableListOf<Scoresheet.RosterEntry>()
        val rawGoals = mutableListOf<RawGoal>()
        val rawPenalties = mutableListOf<RawPenalty>()
        val goalies = mutableListOf<Scoresheet.GoalieLine>()
        var section = Section.NONE

        for (row in leafRows(doc)) {
            val cells = row.select("> td").map { it.cleanText() }
            if (cells.isEmpty()) continue
            val singleCell = if (cells.size == 1) cells[0] else null

            if (singleCell != null) {
                when (singleCell) {
                    rosterMarker -> { section = Section.ROSTER_TEAM_NAME; continue }
                    scoringMarker -> { section = Section.SCORING; continue }
                    goalieMarker -> { section = Section.GOALIE; continue }
                    penaltyMarker -> { section = Section.PENALTY; continue }
                    "${otherSide(side).label} TEAM" -> { section = Section.NONE; continue }
                }
                if (singleCell.startsWith(otherSideMarkerPrefix)) {
                    section = Section.NONE
                    continue
                }
            }

            when (section) {
                Section.ROSTER_TEAM_NAME -> {
                    if (singleCell != null && singleCell.isNotEmpty() && singleCell != ".") {
                        teamName = singleCell
                        section = Section.ROSTER
                    }
                }
                Section.ROSTER -> {
                    if (cells.size == 2) {
                        val jersey = cells[0]
                        val name = cells[1]
                        if (jersey.toIntOrNull() != null && name.isNotEmpty() && name != ".") {
                            val isGoalie = name.endsWith("(G)")
                            roster += Scoresheet.RosterEntry(
                                jersey = jersey,
                                name = PlayerNames.display(name.removeSuffix("(G)")),
                                isGoalie = isGoalie,
                            )
                        }
                    }
                }
                Section.SCORING -> {
                    if (cells.size == 8 && cells != SCORING_HEADER) {
                        val period = cells[1]
                        val time = cells[2]
                        val scorerJ = cells[3]
                        if (period != "." && time != "." && scorerJ != "." && scorerJ.isNotEmpty()) {
                            rawGoals += RawGoal(period, time, scorerJ, cells[4], cells[6], cells[7])
                        }
                    }
                }
                Section.GOALIE -> {
                    if (cells.size == 5 && cells != GOALIE_HEADER) {
                        val name = cells[0]
                        val ga = cells[2].toIntOrNull()
                        val sa = cells[3].toIntOrNull()
                        val sv = cells[4].toIntOrNull()
                        if (name.isNotEmpty() && name != "." && ga != null && sa != null && sv != null) {
                            goalies += Scoresheet.GoalieLine(PlayerNames.display(name), cells[1], ga, sa, sv)
                        }
                    }
                }
                Section.PENALTY -> {
                    // The per-period scoring and shots tables further down also have six columns,
                    // so a penalty row must have a game-clock time.
                    if (cells.size == 6 && cells != PENALTY_HEADER && CLOCK_TIME.matches(cells[2])) {
                        val period = cells[1]
                        val time = cells[2]
                        val playerJ = cells[3]
                        if (period != "." && time != "." && playerJ != "." && playerJ.isNotEmpty()) {
                            rawPenalties += RawPenalty(period, time, playerJ, cells[4], cells[5])
                        }
                    }
                }
                Section.NONE -> {}
            }
        }

        if (teamName.isEmpty()) return null

        // Two players can share a number (a skater and the goalie both wearing #0). Goals,
        // assists and penalties then go to the skater, since goalies rarely figure in them.
        val jerseyToName = roster.groupBy { it.jersey }
            .mapValues { (_, entries) -> (entries.firstOrNull { !it.isGoalie } ?: entries.first()).name }
        val goals = rawGoals.mapNotNull { raw ->
            val scorer = jerseyToName[raw.scorerJersey] ?: return@mapNotNull null
            Scoresheet.ScoringEvent(
                period = raw.period,
                time = raw.time,
                scorer = scorer,
                assist1 = jerseyToName[raw.assist1Jersey],
                assist2 = jerseyToName[raw.assist2Jersey],
                strength = raw.strength,
            )
        }
        val penalties = rawPenalties.mapNotNull { raw ->
            val player = jerseyToName[raw.playerJersey] ?: return@mapNotNull null
            Scoresheet.PenaltyEvent(raw.period, raw.time, player, raw.infraction, raw.minutes)
        }

        return Scoresheet(teamName, roster, goals, penalties, goalies)
    }

    private fun leafRows(doc: Document): List<Element> =
        doc.select("tr").filter { it.select("table").isEmpty() }

    private fun otherSide(side: Side): Side = if (side == Side.HOME) Side.AWAY else Side.HOME

    private fun Element.cleanText(): String = text().replace(' ', ' ').trim().normalizeSpaces()

    private fun String.normalizeSpaces(): String = this.replace(Regex("\\s+"), " ").trim()

    private data class RawGoal(
        val period: String,
        val time: String,
        val scorerJersey: String,
        val assist1Jersey: String,
        val assist2Jersey: String,
        val strength: String,
    )

    private data class RawPenalty(
        val period: String,
        val time: String,
        val playerJersey: String,
        val infraction: String,
        val minutes: String,
    )

    companion object {
        private val SCORING_HEADER = listOf("No", "Per", "Time", "Goal", "Assist", "", "Assist", "Type")
        private val PENALTY_HEADER = listOf("No", "Per", "Time", "#", "Infraction", "Min")
        private val GOALIE_HEADER = listOf("Name", "Mins", "GA", "SA", "SV")
        private val CLOCK_TIME = Regex("\\d{1,2}:\\d{2}")
        private val TITLE_DATE = Regex("Scoresheet - ([A-Z][a-z]{2} \\d{1,2}, \\d{4})")
        private val TITLE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
    }
}
