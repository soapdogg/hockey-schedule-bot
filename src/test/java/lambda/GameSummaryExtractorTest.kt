package lambda

import io.mockk.*
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameSummaryExtractorTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadFixture(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "fixture not found: $path" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `parses a finalized game summary from production HTML`() {
        val html = loadFixture("/fixtures/game_55483_summary.html")
        val recap = GameSummaryExtractor(mockk()).parseIfReady(Jsoup.parse(html))

        assertNotNull(recap)
        assertEquals(2, recap.teams.size)

        val rainier = recap.teams.first { it.name.startsWith("Rainier") }
        val dirty = recap.teams.first { it.name.startsWith("Dirty") }
        assertEquals(2, rainier.total)
        assertEquals(8, dirty.total)
        assertEquals(listOf(0, 0, 2), rainier.periods)
        assertEquals(listOf(1, 5, 2), dirty.periods)

        assertEquals(10, recap.goals.size)
        val firstGoal = recap.goals.first()
        assertEquals("5:52", firstGoal.time)
        assertEquals("Tyler Smith", firstGoal.scorer)
        assertEquals(listOf("Jon Cargille"), firstGoal.assists)
        assertEquals("Dirty Couch Hockey Club", firstGoal.team)
        assertTrue(firstGoal.period.contains("1ST", ignoreCase = true))

        val unassisted = recap.goals.first { it.scorer == "Jazz Palley" }
        assertTrue(unassisted.assists.isEmpty())

        assertEquals(1, recap.penalties.size)
        val pen = recap.penalties.first()
        assertEquals("Eddie Parker", pen.player)
        assertEquals("Body Check", pen.infraction)
        assertEquals("2:00", pen.duration)
        assertEquals("1:32", pen.time)
        assertTrue(pen.period.contains("3RD", ignoreCase = true))

        assertEquals(2, recap.shots.size)
        assertEquals(30, recap.shots.first { it.name.startsWith("Rainier") }.total)
        assertEquals(31, recap.shots.first { it.name.startsWith("Dirty") }.total)
    }

    @Test
    fun `returns null when SCORING SUMMARY header is missing`() {
        val html = """<html><body><h1>SCORING</h1><table><tbody>
            <tr><td><img/></td><td>Team A</td><td>1</td><td>0</td><td>0</td><td>1</td></tr>
            <tr><td><img/></td><td>Team B</td><td>0</td><td>0</td><td>0</td><td>0</td></tr>
        </tbody></table></body></html>""".trimIndent()
        val recap = GameSummaryExtractor(mockk()).parseIfReady(Jsoup.parse(html))
        assertNull(recap)
    }

    @Test
    fun `returns null when scoring table has wrong row count`() {
        val html = """<html><body>
            <h1>SCORING</h1><table><tbody>
              <tr><td><img/></td><td>Only Team</td><td>1</td><td>1</td></tr>
            </tbody></table>
            <h1>SCORING SUMMARY</h1>
        </body></html>""".trimIndent()
        val recap = GameSummaryExtractor(mockk()).parseIfReady(Jsoup.parse(html))
        assertNull(recap)
    }

    @Test
    fun `fetchIfReady builds correct URL`() {
        val client = mockk<HttpClient>()
        val urlSlot = slot<String>()
        every { client.executeHttpGetRequest(capture(urlSlot)) } returns "<html></html>"
        GameSummaryExtractor(client).fetchIfReady("12345")
        assertTrue(urlSlot.captured.endsWith("/game/12345/summary"))
        assertTrue(urlSlot.captured.startsWith(Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE))
    }

    @Test
    fun `parses the current page layout, including goal type badges`() {
        val html = loadFixture("/fixtures/game_57309_summary.html")
        val recap = GameSummaryExtractor(mockk()).parseIfReady(Jsoup.parse(html))

        assertNotNull(recap)
        assertEquals(listOf(5, 4), recap.teams.map { it.total })
        assertEquals(9, recap.goals.size)
        assertEquals(
            Recap.Goal("1ST PERIOD", "5:08", "Rainier Reindeer HC-6B", "Phantasia Jerdro", listOf("Dzucka Lin")),
            recap.goals.first(),
        )
        val powerPlayGoal = recap.goals.single { it.type != null }
        assertEquals("Jazz Palley", powerPlayGoal.scorer)
        assertEquals("PPG", powerPlayGoal.type)
        assertEquals("0:04", powerPlayGoal.time)
        assertEquals(listOf("Diviyan Matheendran", "Jazz Palley"), recap.goals.single { it.scorer == "Chris Flynn" }.assists)
        assertEquals(5, recap.penalties.size)
    }

    @Test
    fun `accepts shootout games, whose final score includes the shootout winner`() {
        val html = loadFixture("/fixtures/game_56621_summary.html")
        val recap = GameSummaryExtractor(mockk()).parseIfReady(Jsoup.parse(html))

        assertNotNull(recap)
        assertEquals(listOf(3, 2), recap.teams.map { it.total })
        assertEquals(4, recap.goals.size)
    }

    @Test
    fun `returns null when parsed goals do not add up to the scoring table`() {
        val doc = Jsoup.parse(loadFixture("/fixtures/game_57309_summary.html"))
        doc.select("h1").first { it.text().trim() == "SCORING SUMMARY" }
            .nextElementSiblings()
            .first { it.hasClass("row") && it.hasClass("mb-3") }
            .remove()
        assertNull(GameSummaryExtractor(mockk()).parseIfReady(doc))
    }
}
