package lambda

import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeasonDiscovererTest {

    private val doc = Jsoup.parse(
        requireNotNull(javaClass.getResourceAsStream("/fixtures/team_10159_schedule.html"))
            .bufferedReader().use { it.readText() }
    )

    @Test
    fun `lists every season with its real label, newest first`() {
        val seasons = SeasonDiscoverer(mockk()).parse(doc, "10159")

        assertEquals(21, seasons.size)
        assertEquals(SeasonDiscoverer.DiscoveredSeason("2140", "Summer 2026"), seasons.first())
        assertEquals(SeasonDiscoverer.DiscoveredSeason("87", "Kraken Hockey League Fall/Winter 2021"), seasons.last())
        assertTrue(seasons.none { it.label in setOf("Home", "Away", "Rainier Reindeer HC-6") })
    }

    @Test
    fun `the current season is the one the page is showing`() {
        assertEquals(
            SeasonDiscoverer.DiscoveredSeason("2140", "Summer 2026"),
            SeasonDiscoverer(mockk()).parseCurrent(doc, "10159"),
        )
    }
}
