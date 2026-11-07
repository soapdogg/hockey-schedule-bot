package lambda

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamStatsExtractorTest {

    private fun loadFixture(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "fixture not found: $path" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `parses skater and goalie tables from production HTML`() {
        val html = loadFixture("/fixtures/team_10159_stats.html")
        val stats = TeamStatsExtractor(mockk()).parse(Jsoup.parse(html))

        assertEquals(30, stats.skaters.size)
        assertEquals(7, stats.goalies.size)
        assertEquals(TeamStats.Skater("Chris Flynn", 6, 5, 5, 10), stats.skaters.single { it.name == "Chris Flynn" })
        assertTrue(stats.skaters.any { it.name == "Dzucka Lin" }, "all-caps names should be title-cased")
        assertEquals(TeamStats.Goalie("Eric Soppi", 6, "2.67", ".876", 1), stats.goalies.single { it.name == "Eric Soppi" })
    }

    @Test
    fun `ignores tables without the expected columns`() {
        val html = """<html><body><table>
            <thead><tr><th>Team</th><th>W</th></tr></thead>
            <tbody><tr><td>Someone</td><td>3</td></tr></tbody>
        </table></body></html>""".trimIndent()
        val stats = TeamStatsExtractor(mockk()).parse(Jsoup.parse(html))
        assertTrue(stats.skaters.isEmpty())
        assertTrue(stats.goalies.isEmpty())
    }

    @Test
    fun `fetch requests the team stats page`() {
        val client = mockk<HttpClient>()
        val url = slot<String>()
        every { client.executeHttpGetRequest(capture(url)) } returns "<html></html>"
        TeamStatsExtractor(client).fetch("10159")
        assertEquals("${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}/team/10159/stats", url.captured)
    }
}
