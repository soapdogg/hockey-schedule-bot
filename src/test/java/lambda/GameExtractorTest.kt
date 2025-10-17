package lambda

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameExtractorTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `extractGames parses HTML schedule correctly`() {
        val mockHttpClient = mockk<HttpClient>()
        val htmlSchedule = """
            <html>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>Wed, Oct 16</td>
                            <td>7:00 PM</td>
                            <td>KVIC</td>
                            <td>Home</td>
                            <td><a href="/team/12345/schedule/">Opponent Team</a></td>
                        </tr>
                    </tbody>
                </table>
            </html>
        """.trimIndent()
        
        val team = Team("TestTeam", "10159", "webhook", "channelId")
        
        every { mockHttpClient.executeHttpGetRequest(any()) } returns htmlSchedule

        val extractor = GameExtractor(mockHttpClient)
        val games = extractor.extractGames(team)

        assertEquals(1, games.size)
        val game = games[0]
        assertEquals("1", game.number)
        assertEquals("Wed, Oct 16", game.date)
        assertEquals("7:00 PM", game.time)
        assertEquals("KVIC", game.arena)
        assertEquals("Home", game.ha)
        assertEquals("Opponent Team", game.opponent)
        assertEquals("/team/12345/schedule/", game.opponentLink)
    }

    @Test
    fun `extractGames handles multiple games`() {
        val mockHttpClient = mockk<HttpClient>()
        val htmlSchedule = """
            <html>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>Wed, Oct 16</td>
                            <td>7:00 PM</td>
                            <td>KVIC</td>
                            <td>Home</td>
                            <td><a href="/team/1/schedule/">Team 1</a></td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>Thu, Oct 17</td>
                            <td>8:30 PM</td>
                            <td>LIC</td>
                            <td>Away</td>
                            <td><a href="/team/2/schedule/">Team 2</a></td>
                        </tr>
                    </tbody>
                </table>
            </html>
        """.trimIndent()
        
        val team = Team("TestTeam", "10159", "webhook", "channelId")
        
        every { mockHttpClient.executeHttpGetRequest(any()) } returns htmlSchedule

        val extractor = GameExtractor(mockHttpClient)
        val games = extractor.extractGames(team)

        assertEquals(2, games.size)
        assertEquals("1", games[0].number)
        assertEquals("2", games[1].number)
    }

    @Test
    fun `extractGames handles multiple tables`() {
        val mockHttpClient = mockk<HttpClient>()
        val htmlSchedule = """
            <html>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>Wed, Oct 16</td>
                            <td>7:00 PM</td>
                            <td>KVIC</td>
                            <td>Home</td>
                            <td><a href="/team/1/schedule/">Team 1</a></td>
                        </tr>
                    </tbody>
                </table>
                <table>
                    <tbody>
                        <tr>
                            <td>2</td>
                            <td>Thu, Oct 17</td>
                            <td>8:30 PM</td>
                            <td>LIC</td>
                            <td>Away</td>
                            <td><a href="/team/2/schedule/">Team 2</a></td>
                        </tr>
                    </tbody>
                </table>
            </html>
        """.trimIndent()
        
        val team = Team("TestTeam", "10159", "webhook", "channelId")
        
        every { mockHttpClient.executeHttpGetRequest(any()) } returns htmlSchedule

        val extractor = GameExtractor(mockHttpClient)
        val games = extractor.extractGames(team)

        assertEquals(2, games.size)
    }

    @Test
    fun `extractGames returns empty list when no valid games found`() {
        val mockHttpClient = mockk<HttpClient>()
        val htmlSchedule = """
            <html>
                <table>
                    <tbody>
                        <tr>
                            <td>Incomplete</td>
                            <td>Row</td>
                        </tr>
                    </tbody>
                </table>
            </html>
        """.trimIndent()
        
        val team = Team("TestTeam", "10159", "webhook", "channelId")
        
        every { mockHttpClient.executeHttpGetRequest(any()) } returns htmlSchedule

        val extractor = GameExtractor(mockHttpClient)
        val games = extractor.extractGames(team)

        assertEquals(0, games.size)
    }

    @Test
    fun `extractGames skips rows with insufficient columns`() {
        val mockHttpClient = mockk<HttpClient>()
        val htmlSchedule = """
            <html>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>Not enough columns</td>
                        </tr>
                        <tr>
                            <td>2</td>
                            <td>Wed, Oct 16</td>
                            <td>7:00 PM</td>
                            <td>KVIC</td>
                            <td>Home</td>
                            <td><a href="/team/1/schedule/">Team 1</a></td>
                        </tr>
                    </tbody>
                </table>
            </html>
        """.trimIndent()
        
        val team = Team("TestTeam", "10159", "webhook", "channelId")
        
        every { mockHttpClient.executeHttpGetRequest(any()) } returns htmlSchedule

        val extractor = GameExtractor(mockHttpClient)
        val games = extractor.extractGames(team)

        assertEquals(1, games.size)
        assertEquals("2", games[0].number)
    }

    @Test
    fun `extractGames generates consistent hash for same game data`() {
        val mockHttpClient = mockk<HttpClient>()
        val htmlSchedule = """
            <html>
                <table>
                    <tbody>
                        <tr>
                            <td>1</td>
                            <td>Wed, Oct 16</td>
                            <td>7:00 PM</td>
                            <td>KVIC</td>
                            <td>Home</td>
                            <td><a href="/team/1/schedule/">Team 1</a></td>
                        </tr>
                    </tbody>
                </table>
            </html>
        """.trimIndent()
        
        val team = Team("TestTeam", "10159", "webhook", "channelId")
        
        every { mockHttpClient.executeHttpGetRequest(any()) } returns htmlSchedule

        val extractor = GameExtractor(mockHttpClient)
        val games1 = extractor.extractGames(team)
        val games2 = extractor.extractGames(team)

        assertEquals(games1[0].hash, games2[0].hash)
    }

    @Test
    fun `extractGames uses correct URL for team`() {
        val mockHttpClient = mockk<HttpClient>()
        val urlSlot = slot<String>()
        
        every { mockHttpClient.executeHttpGetRequest(capture(urlSlot)) } returns "<html></html>"

        val team = Team("TestTeam", "12345", "webhook", "channelId")
        val extractor = GameExtractor(mockHttpClient)
        extractor.extractGames(team)

        assertTrue(urlSlot.captured.contains("12345"))
        assertTrue(urlSlot.captured.startsWith(Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE))
    }
}
