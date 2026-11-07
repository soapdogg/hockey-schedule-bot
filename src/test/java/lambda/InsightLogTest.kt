package lambda

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightLogTest {
    private val ddb = mockk<DynamoDbClient>()
    private val log = InsightLog(ddb, "TestLog")
    private val at = Instant.parse("2026-11-07T21:30:15.123Z")
    private val insight = Insight("duo-Ava-Ben-9", "duo", listOf("Ava", "Ben"), "Ava has assisted on 9 of Ben's goals.", 2.0)

    @Test
    fun `records a post and reads it back`() {
        val put = slot<PutItemRequest>()
        every { ddb.putItem(capture(put)) } returns PutItemResponse.builder().build()
        log.record("10159", insight, "recap", at)

        val item = put.captured.item()
        assertEquals("TestLog", put.captured.tableName())
        assertEquals("2026-11-07T21:30:15Z#duo-Ava-Ben-9", item.getValue("posted-at").s())
        assertEquals(listOf("Ava", "Ben"), item.getValue("subjects").l().map { it.s() })
        assertEquals("recap", item.getValue("occasion").s())
        assertEquals((at.epochSecond + 365L * 24 * 3600).toString(), item.getValue("ttl").n())

        val query = slot<QueryRequest>()
        every { ddb.query(capture(query)) } returns QueryResponse.builder().items(item).build()
        val recent = log.recent("10159", Instant.parse("2026-09-08T00:00:00Z"))

        assertEquals(listOf(PostedInsight("duo-Ava-Ben-9", "duo", listOf("Ava", "Ben"), Instant.parse("2026-11-07T21:30:15Z"))), recent)
        assertTrue(query.captured.consistentRead())
        assertEquals("2026-09-08T00:00:00Z", query.captured.expressionAttributeValues().getValue(":since").s())
    }
}
