package lambda

import org.jsoup.Jsoup

class GameExtractor(
    private val httpClient: HttpClient,
) {

    fun extractGames(team: Team): List<Game> {
        val scheduleUrl = "${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/team/${team.krakenWebsiteId}/schedule/"
        return try {
            val schedule = httpClient.executeHttpGetRequest(scheduleUrl)
            val doc = Jsoup.parse(schedule)
            val games = mutableListOf<Game>()
            doc.select("table")?.forEach { month ->
                month.select("tbody")?.forEach { m ->
                    m.select("tr")?.forEach { game ->
                        val gameData = game?.select("td")
                        if (gameData != null && gameData.size >= 6) {
                            val gameNumber = gameData[0]?.text() ?: return@forEach
                            val gameDate = gameData[1]?.text() ?: return@forEach
                            val gameTime = gameData[2]?.text() ?: return@forEach
                            val arena = gameData[3]?.text() ?: return@forEach
                            val ha = gameData[4]?.text() ?: return@forEach
                            val opponent = gameData[5]?.text() ?: return@forEach
                            val opponentLink = gameData[5]?.select("a")?.attr("href") ?: ""

                            val hash = hashAll(gameNumber, gameDate, gameTime, arena, ha, opponent)

                            val g = Game(
                                number = gameNumber,
                                date = gameDate,
                                time = gameTime,
                                arena = arena,
                                ha = ha,
                                opponent = opponent,
                                opponentLink = opponentLink,
                                hash = hash,
                            )
                            games.add(g)
                        }
                    }
                }
            }
            games
        } catch (e: Exception) {
            println("ERROR: Failed to extract games for team ${team.name}: ${e.message}")
            emptyList()
        }
    }

    private fun hashAll(vararg vals: Any?): String {
        var res = 0L
        for (v in vals) {
            res += v.hashCode().toLong()
            res *= 31L
        }
        return res.toString()
    }
}
