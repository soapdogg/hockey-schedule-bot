package lambda

import io.mockk.*
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScoresheetExtractorTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun loadFixture(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "fixture not found: $path" }
            .bufferedReader().use { it.readText() }

    private fun parseFixture(name: String): ParsedScoresheet =
        ScoresheetExtractor(mockk()).parse(Jsoup.parse(loadFixture("/fixtures/$name")))

    @Test
    fun `parses the Reindeer side of a complete scoresheet`() {
        val parsed = parseFixture("scoresheet_51961.html")
        val sheet = parsed.away

        assertNotNull(sheet)
        assertEquals("Rainier Reindeer HC-6B", sheet.teamName)

        assertEquals(11, sheet.roster.size)
        val soppi = sheet.roster.first { it.name == "Eric Soppi" }
        assertEquals("31", soppi.jersey)
        assertTrue(soppi.isGoalie)
        val nonGoalies = sheet.roster.filter { !it.isGoalie }
        assertEquals(10, nonGoalies.size)
        assertTrue(sheet.roster.any { it.name == "Jazz Palley" && it.jersey == "75" })
        assertTrue(sheet.roster.any { it.name == "Tyler Smith" && it.jersey == "22" })

        assertEquals(3, sheet.goals.size)
        val first = sheet.goals[0]
        assertEquals("2", first.period)
        assertEquals("9:37", first.time)
        assertEquals("Tyler Smith", first.scorer)
        assertEquals("Jarrett Schultz", first.assist1)
        assertNull(first.assist2)
        assertEquals("EV", first.strength)

        val unassisted = sheet.goals.first { it.scorer == "Dade Wilson" }
        assertNull(unassisted.assist1)
        assertNull(unassisted.assist2)

        assertEquals(3, sheet.penalties.size)
        assertEquals("Tara Parker", sheet.penalties[0].player)
        assertEquals("Interferenc", sheet.penalties[0].infraction)
        assertEquals("2", sheet.penalties[0].minutes)
        assertEquals("Tyler Smith", sheet.penalties[1].player)
        assertEquals("Cross Check", sheet.penalties[1].infraction)
        assertEquals("Jarrett Schultz", sheet.penalties[2].player)
        assertEquals("Tripping", sheet.penalties[2].infraction)

        assertEquals(1, sheet.goalies.size)
        val goalie = sheet.goalies[0]
        assertEquals("Eric Soppi", goalie.name)
        assertEquals("54:00", goalie.minutes)
        assertEquals(5, goalie.goalsAgainst)
        assertEquals(18, goalie.shotsAgainst)
        assertEquals(13, goalie.saves)
    }

    @Test
    fun `parses the opponent's side and the game date`() {
        val parsed = parseFixture("scoresheet_51961.html")
        assertEquals(LocalDate.of(2025, 5, 18), parsed.date)

        val home = assertNotNull(parsed.home)
        assertEquals("Razor Clams", home.teamName)
        assertEquals(5, home.goals.size)
        assertEquals("Essie Toivanen", home.roster.single { it.jersey == "12" }.name, "all-caps names are title-cased")
        assertEquals(34, home.goalies.single().shotsAgainst)
    }

    @Test
    fun `sides picks ours by the schedule's home or away column`() {
        val parsed = parseFixture("scoresheet_51961.html")
        val (us, them) = assertNotNull(parsed.sides("AWAY"))
        assertSame(parsed.away, us)
        assertSame(parsed.home, them)
        assertSame(parsed.home, parsed.sides("home")?.first)
        assertNull(parsed.sides(""))
        assertNull(parsed.sides(null))
    }

    @Test
    fun `credits a shared jersey number to the skater rather than the goalie`() {
        val away = assertNotNull(parseFixture("scoresheet_56190.html").away)
        val wearingZero = away.roster.filter { it.jersey == "0" }
        assertEquals(setOf("Eric Soppi", "Jarrett Schultz"), wearingZero.map { it.name }.toSet())

        val firstGoal = away.goals.first()
        assertEquals("Jazz Palley", firstGoal.scorer)
        assertEquals("Chris Flynn", firstGoal.assist1)
        assertEquals("Jarrett Schultz", firstGoal.assist2)
    }

    @Test
    fun `ignores the per-period scoring and shots tables after the penalties`() {
        val parsed = parseFixture("scoresheet_57278.html")
        val home = assertNotNull(parsed.home)
        val away = assertNotNull(parsed.away)

        assertEquals(listOf("5:41", "15:47", "7:00"), home.penalties.map { it.time })
        assertEquals(listOf("Interferenc", "Tripping", "Roughing"), home.penalties.map { it.infraction })
        assertEquals(listOf("5:41", "7:00"), away.penalties.map { it.time })
        assertTrue((home.penalties + away.penalties).all { it.minutes == "2" })
    }

    @Test
    fun `returns empty sides when section markers are missing`() {
        val html = """<html><body><table><tr><td>nothing here</td></tr></table></body></html>"""
        val parsed = ScoresheetExtractor(mockk()).parse(Jsoup.parse(html))
        assertNull(parsed.home)
        assertNull(parsed.away)
        assertNull(parsed.date)
    }

    @Test
    fun `fetch builds the correct URL`() {
        val client = mockk<HttpClient>()
        val urlSlot = slot<String>()
        every { client.executeHttpGetRequest(capture(urlSlot)) } returns "<html></html>"
        ScoresheetExtractor(client).fetch("51961")
        assertTrue(urlSlot.captured.endsWith("/scoresheet-complete/51961"))
        assertTrue(urlSlot.captured.startsWith(Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE))
    }
}
