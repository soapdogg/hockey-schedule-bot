package lambda

import io.mockk.every
import io.mockk.mockk
import lambda.CheckInTestData.answer
import lambda.CheckInTestData.game
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInStoreTest {
    private val ddb = mockk<DynamoDbClient>()
    private val store = CheckInStore(ddb, "TestCheckIns")

    @Test
    fun `saves the post and answers, and reads them back`() {
        val puts = mutableListOf<PutItemRequest>()
        every { ddb.putItem(capture(puts)) } returns PutItemResponse.builder().build()
        val game = game(remindedSlot = "2026-11-12-am", reminderMessageId = "77", gameDayPosted = true)
        val answer = answer("2", "Ben", Position.DEFENSE)
        store.saveGame(game)
        store.saveResponse(game, answer)

        assertTrue(puts.all { it.tableName() == "TestCheckIns" })
        val ttl = puts[0].item().getValue("ttl").n().toLong()
        assertEquals(game.start.atZone(CheckIns.ZONE).toInstant().plus(CheckInStore.RETENTION).epochSecond, ttl)

        val query = mutableListOf<QueryRequest>()
        every { ddb.query(capture(query)) } returns QueryResponse.builder().items(puts.map { it.item() }).build()
        assertEquals(CheckInState(game, listOf(answer)), store.load(game.key))
        assertTrue(query.single().consistentRead())
    }

    @Test
    fun `nothing saved means no check-in`() {
        every { ddb.query(any<QueryRequest>()) } returns QueryResponse.builder().items(emptyList()).build()
        assertNull(store.load("10159:2026-11-16:Fossils"))
    }
}
