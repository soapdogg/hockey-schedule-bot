package lambda

import org.jsoup.Jsoup

class GameExtractor(
    private val httpClient: HttpClient,
) {

    fun extractGames(team: Team): List<Game> {
        val scheduleUrl = "${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/team/${team.krakenWebsiteId}/schedule/"
        val schedule = httpClient.executeHttpGetRequest(scheduleUrl)
        val doc = Jsoup.parse(schedule)
        val games = mutableListOf<Game>()
        doc.select("table")?.forEach { month ->
            month.select("tbody")?.forEach { m ->
                m.select("tr")?.forEach { game ->
                    val gameData = game?.select("td")
                    if (gameData != null && gameData.size >= 6) {
                        val gameNumber = gameData[0]?.text()!!
                        val gameDate = gameData[1]?.text()!!
                        val gameTime = gameData[2]?.text()!!
                        val arena = gameData[3]?.text()!!
                        val ha = gameData[4]?.text()!!
                        val opponent = gameData[5]?.text()!!
                        val opponentLink = gameData[5]?.select("a")?.attr("href")!!

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
        return games
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