package lambda

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class TeamStatsExtractor(
    private val httpClient: HttpClient,
) {
    fun fetch(teamId: String): TeamStats {
        val url = "${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/team/$teamId/stats"
        return parse(Jsoup.parse(httpClient.executeHttpGetRequest(url)))
    }

    /** Tables are identified by their header columns, not their position on the page. */
    fun parse(doc: Document): TeamStats {
        val skaters = mutableListOf<TeamStats.Skater>()
        val goalies = mutableListOf<TeamStats.Goalie>()
        for (table in doc.select("table")) {
            val header = table.select("thead th").map { it.text().trim().uppercase() }
            val col = header.withIndex().associate { (i, h) -> h to i }
            val rows = bodyRows(table, header.size)
            when {
                col.keys.containsAll(SKATER_COLUMNS) -> rows.mapNotNullTo(skaters) { r -> parseSkater(r, col) }
                col.keys.containsAll(GOALIE_COLUMNS) -> rows.mapNotNullTo(goalies) { r -> parseGoalie(r, col) }
            }
        }
        return TeamStats(skaters, goalies)
    }

    private fun bodyRows(table: Element, width: Int): List<List<String>> =
        table.select("tbody > tr")
            .map { tr -> tr.select("> td").map { it.text().trim() } }
            .filter { it.size == width }

    private fun parseSkater(r: List<String>, col: Map<String, Int>): TeamStats.Skater? {
        val name = r[col.getValue("PLAYER")].takeIf { it.isNotEmpty() } ?: return null
        return TeamStats.Skater(
            name = PlayerNames.display(name),
            gamesPlayed = r[col.getValue("GP")].toIntOrNull() ?: return null,
            goals = r[col.getValue("G")].toIntOrNull() ?: return null,
            assists = r[col.getValue("A")].toIntOrNull() ?: return null,
            points = r[col.getValue("PTS")].toIntOrNull() ?: return null,
        )
    }

    private fun parseGoalie(r: List<String>, col: Map<String, Int>): TeamStats.Goalie? {
        val name = r[col.getValue("PLAYER")].takeIf { it.isNotEmpty() } ?: return null
        return TeamStats.Goalie(
            name = PlayerNames.display(name),
            gamesPlayed = r[col.getValue("GP")].toIntOrNull() ?: return null,
            goalsAgainstAverage = r[col.getValue("GAA")],
            savePercentage = r[col.getValue("SV%")],
            shutouts = r[col.getValue("SO")].toIntOrNull() ?: 0,
        )
    }

    companion object {
        private val SKATER_COLUMNS = setOf("PLAYER", "GP", "G", "A", "PTS")
        private val GOALIE_COLUMNS = setOf("PLAYER", "GP", "GAA", "SV%", "SO")
    }
}
