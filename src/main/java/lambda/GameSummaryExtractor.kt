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
                val assists = card.selectFirst("div.mt-1")
                    ?.select("a")
                    ?.map { it.text().trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
                val timeLine = card.selectFirst("div.text-nowrap")?.text()?.trim().orEmpty()
                val time = timeLine.substringBefore("|").trim()
                if (scorer.isNotEmpty() && time.isNotEmpty()) {
                    goals.add(
                        Recap.Goal(
                            period = currentPeriod,
                            time = time,
                            team = team,
                            scorer = scorer,
                            assists = assists,
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
                            player = player,
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
}
