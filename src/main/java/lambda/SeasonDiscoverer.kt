package lambda

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SeasonDiscoverer(
    private val httpClient: HttpClient,
) {
    fun discover(teamId: String): List<DiscoveredSeason> = parse(fetchSchedule(teamId), teamId)

    /** The season the team's default schedule page shows, which is the one in progress. */
    fun discoverCurrent(teamId: String): DiscoveredSeason? = parseCurrent(fetchSchedule(teamId), teamId)

    /** Every season the team has played, newest first, as listed in the page's season menu. */
    fun parse(doc: Document, teamId: String): List<DiscoveredSeason> {
        val teamPrefix = "/team/$teamId/"
        val ordered = mutableMapOf<String, String>()
        for (anchor in doc.select("a[href*=season=]")) {
            val href = anchor.attr("href")
            // Skip the home/away filters and the menu of other teams, which links to the
            // current season under the team's name rather than the season's.
            if (!href.contains(teamPrefix) || href.contains("location=") || isInTeamMenu(anchor, teamPrefix)) continue
            val seasonId = SEASON_PARAM.find(href)?.groupValues?.get(1) ?: continue
            val label = anchor.text().trim()
            if (label.isEmpty()) continue
            ordered.putIfAbsent(seasonId, label)
        }
        return ordered.entries.map { (id, label) -> DiscoveredSeason(id, label) }
    }

    /** The home/away filter links point at the season on display; fall back to the newest. */
    fun parseCurrent(doc: Document, teamId: String): DiscoveredSeason? {
        val seasons = parse(doc, teamId)
        val shownId = doc.select("a[href*=location=]")
            .map { it.attr("href") }
            .firstOrNull { it.contains("/team/$teamId/") }
            ?.let { SEASON_PARAM.find(it)?.groupValues?.get(1) }
        return seasons.firstOrNull { it.seasonId == shownId } ?: seasons.firstOrNull()
    }

    private fun fetchSchedule(teamId: String): Document {
        val url = "${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/team/$teamId/schedule"
        return Jsoup.parse(httpClient.executeHttpGetRequest(url))
    }

    private fun isInTeamMenu(anchor: Element, teamPrefix: String): Boolean =
        anchor.closest("ul")
            ?.select("a[href*=/team/]")
            ?.any { !it.attr("href").contains(teamPrefix) }
            ?: false

    data class DiscoveredSeason(val seasonId: String, val label: String)

    companion object {
        private val SEASON_PARAM = Regex("[?&]season=(\\d+)")
    }
}
