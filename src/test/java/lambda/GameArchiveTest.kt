package lambda

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameArchiveTest {

    private val ddb = mockk<DynamoDbClient>()
    private val archive = GameArchive(ddb, "TestArchive")

    private val game = ArchivedGame(
        gameId = "57309",
        seasonId = "2143",
        seasonLabel = "Kraken Hockey League Fall/Winter 26/27",
        date = "2026-11-02",
        dateText = "Mon, Nov 2",
        timeText = "9:00 PM",
        arena = "KVIC",
        opponent = "Barrel Aged Brewzers",
        homeAway = "HOME",
        result = "L",
        ourScore = 4,
        oppScore = 5,
        us = Scoresheet(
            teamName = "Rainier Reindeer HC-6B",
            roster = listOf(Scoresheet.RosterEntry("0", "Eric Soppi", isGoalie = true)),
            goals = listOf(Scoresheet.ScoringEvent("1", "0:04", "Jazz Palley", "Dzucka Lin", null, "PP")),
            penalties = emptyList(),
            goalies = listOf(Scoresheet.GoalieLine("Eric Soppi", "54:00", 5, 21, 16)),
        ),
        them = null,
    )

    private fun capturePuts(): MutableList<PutItemRequest> {
        val puts = mutableListOf<PutItemRequest>()
        every { ddb.putItem(capture(puts)) } returns PutItemResponse.builder().build()
        return puts
    }

    @Test
    fun `put stores the keys, season, date and the game as JSON`() {
        val puts = capturePuts()
        archive.put("10159", game)

        val request = puts.single()
        val item = request.item()
        assertEquals("TestArchive", request.tableName())
        assertEquals("10159", item.getValue("team").s())
        assertEquals("57309", item.getValue("game-id").s())
        assertEquals("2143", item.getValue("season-id").s())
        assertEquals("2026-11-02", item.getValue("date").s())
        assertEquals(GameArchive.SCHEMA_VERSION.toString(), item.getValue("schema").n())
    }

    @Test
    fun `put leaves out the date when the scoresheet had none`() {
        val puts = capturePuts()
        archive.put("10159", game.copy(date = null))
        assertNull(puts.single().item()["date"])
    }

    @Test
    fun `games returns what put stored, following pagination`() {
        val puts = capturePuts()
        val second = game.copy(gameId = "57278", result = "W", ourScore = 5, oppScore = 4)
        archive.put("10159", game)
        archive.put("10159", second)

        val pageKey = mapOf("team" to AttributeValue.fromS("10159"), "game-id" to AttributeValue.fromS("57309"))
        val queries = mutableListOf<QueryRequest>()
        every { ddb.query(capture(queries)) } returnsMany listOf(
            QueryResponse.builder().items(puts[0].item()).lastEvaluatedKey(pageKey).build(),
            QueryResponse.builder().items(puts[1].item()).build(),
        )

        assertEquals(listOf(game, second), archive.games("10159"))
        assertEquals(2, queries.size)
        assertEquals(pageKey, queries[1].exclusiveStartKey())
        assertNull(queries[0].projectionExpression())
    }

    @Test
    fun `stores the league's spelling and merges aliases when reading`() {
        val puts = capturePuts()
        val oldSpelling = game.copy(
            us = game.us.copy(goals = listOf(Scoresheet.ScoringEvent("1", "0:04", "Jazz Palley", "Dzuka Lin", null, "PP")))
        )
        archive.put("10159", oldSpelling)
        assertTrue(puts.single().item()["data"]!!.s().contains("Dzuka Lin"))

        every { ddb.query(any<QueryRequest>()) } returns QueryResponse.builder().items(puts[0].item()).build()
        assertEquals(listOf(game), archive.games("10159"))
    }

    @Test
    fun `gameIds reads only the game id`() {
        val query = slot<QueryRequest>()
        every { ddb.query(capture(query)) } returns
            QueryResponse.builder().items(mapOf("game-id" to AttributeValue.fromS("57309"))).build()

        assertEquals(setOf("57309"), archive.gameIds("10159"))
        assertEquals("#projected", query.captured.projectionExpression())
        assertEquals("game-id", query.captured.expressionAttributeNames()["#projected"])
        assertEquals("10159", query.captured.expressionAttributeValues().getValue(":team").s())
        verify(exactly = 1) { ddb.query(any<QueryRequest>()) }
    }
}
