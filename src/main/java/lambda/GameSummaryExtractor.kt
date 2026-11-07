package lambda

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GameSummaryExtractor(
    private val httpClient: HttpClient,
) {
    fun fetchIfReady(gameId: String): Recap? {
        val url = "${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/game/$gameId/summary"
        val html = httpClient.executeHttpGetRequest(url)
        val doc = Jsoup.parse(html)
        return parseIfReady(doc)
    }

    fun parseIfReady(doc: Document): Recap? {
        val h1ByText = doc.select("h1").associateBy { it.text().trim().uppercase() }
        val scoringSummary = h1ByText["SCORING SUMMARY"] ?: return null
        val scoringHeader = h1ByText["SCORING"] ?: return null

        val scoringTable = findTableAfter(scoringHeader) ?: return null
        val scoringRows = scoringTable.select("tbody > tr")
        if (scoringRows.size != 2) return null

        val teams = scoringRows.mapNotNull { parseTeamLine(it) }
        if (teams.size != 2) return null

        val shots = h1ByText["SHOTS"]
            ?.let { findTableAfter(it) }
            ?.select("tbody > tr")
            ?.mapNotNull { parseTeamLine(it) }
            ?: emptyList()

        val goals = parseGoals(scoringSummary)
        // Per-period goals, not final totals: a shootout winner counts in the total but has no goal entry.
        val expectedGoals = teams.sumOf { it.periods.sum() }
        if (goals.size != expectedGoals) {
            println("WARNING: Scoring table shows $expectedGoals goal(s) but ${goals.size} goal entries were parsed; not posting the recap.")
            return null
        }
        val penalties = h1ByText["PENALTY SUMMARY"]?.let { parsePenalties(it) } ?: emptyList()

        return Recap(
            teams = teams,
            goals = goals,
            penalties = penalties,
            shots = shots,
        )
    }

    private fun findTableAfter(header: Element): Element? {
        var sibling = header.nextElementSibling()
        while (sibling != null) {
            if (sibling.tagName() == "h1") return null
            if (sibling.tagName() == "table") return sibling
            sibling.selectFirst("table")?.let { return it }
            sibling = sibling.nextElementSibling()
        }
        return null
    }

    private fun parseTeamLine(row: Element): Recap.TeamLine? {
        val cells = row.select("> td")
        if (cells.size < 3) return null
        val nameCell = cells[1]
        val name = nameCell.selectFirst("a")?.text() ?: nameCell.text()
        val total = cells[cells.size - 1].text().trim().toIntOrNull() ?: return null
        val periods = cells.subList(2, cells.size - 1).mapNotNull { it.text().trim().toIntOrNull() }
        return Recap.TeamLine(name = name.trim(), periods = periods, total = total)
    }

    private fun parseGoals(start: Element): List<Recap.Goal> {
        val goals = mutableListOf<Recap.Goal>()
        var currentPeriod = ""
        forEachSiblingUntilNextH1(start) { el ->
            if (isPeriodHeader(el)) {
                currentPeriod = el.text().trim()
            } else if (el.hasClass("row") && el.hasClass("mb-3")) {
                val card = el.selectFirst("div.bg-light") ?: return@forEachSiblingUntilNextH1
                val team = card.selectFirst("img[title]")?.attr("title")?.trim() ?: ""
                val scorer = card.selectFirst("a.font-weight-bold")?.text()?.trim().orEmpty()
                val assists = card.select("a.text-secondary")
                    .map { it.text().trim() }
                    .filter { it.isNotEmpty() }
                    .map { PlayerNames.display(it) }
                // The time sits in a div in older pages and a td in newer ones, as "5:08 | 1st".
                val time = card.allElements
                    .firstNotNullOfOrNull { GOAL_TIME.find(it.ownText())?.groupValues?.get(1) }
                    .orEmpty()
                val type = card.selectFirst(".badge")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                if (scorer.isNotEmpty()) {
                    goals.add(
                        Recap.Goal(
                            period = currentPeriod,
                            time = time,
                            team = team,
                            scorer = PlayerNames.display(scorer),
                            assists = assists,
                            type = type,
                        )
                    )
                }
            }
        }
        return goals
    }

    private fun parsePenalties(start: Element): List<Recap.Penalty> {
        val penalties = mutableListOf<Recap.Penalty>()
        var currentPeriod = ""
        forEachSiblingUntilNextH1(start) { el ->
            if (isPeriodHeader(el)) {
                currentPeriod = el.text().trim()
            } else if (el.hasClass("row") && el.hasClass("mb-3")) {
                val card = el.selectFirst("div.bg-light") ?: return@forEachSiblingUntilNextH1
                val team = card.selectFirst("img[title]")?.attr("title")?.trim() ?: ""
                val visibleRow = card.selectFirst("div.col_1") ?: card
                val player = visibleRow.selectFirst("a.font-weight-bold")?.text()?.trim().orEmpty()
                val infraction = visibleRow.selectFirst("span.border-left")?.text()?.trim()
                    ?: visibleRow.selectFirst("div:not(.col_1):not(.col_2)")?.text()?.trim()
                    ?: ""
                val timeLine = card.select("div").firstOrNull { it.ownText().contains("|") }?.ownText()?.trim().orEmpty()
                val time = timeLine.substringBefore("|").trim()
                val duration = card.selectFirst("div.badge")?.text()?.trim() ?: ""
                if (player.isNotEmpty()) {
                    penalties.add(
                        Recap.Penalty(
                            period = currentPeriod,
                            time = time,
                            team = team,
                            player = PlayerNames.display(player),
                            infraction = infraction,
                            duration = duration,
                        )
                    )
                }
            }
        }
        return penalties
    }

    private fun isPeriodHeader(el: Element): Boolean =
        el.hasClass("bg-secondary") && el.text().trim().uppercase().endsWith("PERIOD")

    private inline fun forEachSiblingUntilNextH1(start: Element, block: (Element) -> Unit) {
        var sibling = start.nextElementSibling()
        while (sibling != null) {
            if (sibling.tagName() == "h1") return
            block(sibling)
            sibling = sibling.nextElementSibling()
        }
    }

    companion object {
        private val GOAL_TIME = Regex("^\\s*(\\d{1,2}:\\d{2})\\s*\\|")
    }
}
