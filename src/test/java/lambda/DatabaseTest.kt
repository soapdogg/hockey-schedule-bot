package lambda

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `hasItem returns true when item exists`() {
        val mockClient = mockk<DynamoDbClient>()
        val mockResponse = mockk<GetItemResponse>()
        
        every { mockResponse.hasItem() } returns true
        every { mockClient.getItem(any<GetItemRequest>()) } returns mockResponse

        val database = Database(mockClient)
        
        val result = database.hasItem(
            "testTable",
            "hashKey",
            "rangeKey",
            "hashValue",
            "rangeValue"
        )

        assertTrue(result)
        verify(exactly = 1) { mockClient.getItem(any<GetItemRequest>()) }
    }

    @Test
    fun `hasItem returns false when item does not exist`() {
        val mockClient = mockk<DynamoDbClient>()
        val mockResponse = mockk<GetItemResponse>()
        
        every { mockResponse.hasItem() } returns false
        every { mockClient.getItem(any<GetItemRequest>()) } returns mockResponse

        val database = Database(mockClient)
        
        val result = database.hasItem(
            "testTable",
            "hashKey",
            "rangeKey",
            "hashValue",
            "rangeValue"
        )

        assertFalse(result)
        verify(exactly = 1) { mockClient.getItem(any<GetItemRequest>()) }
    }

    @Test
    fun `hasItem sends correct request parameters`() {
        val mockClient = mockk<DynamoDbClient>()
        val mockResponse = mockk<GetItemResponse>()
        val capturedRequest = slot<GetItemRequest>()
        
        every { mockResponse.hasItem() } returns false
        every { mockClient.getItem(capture(capturedRequest)) } returns mockResponse

        val database = Database(mockClient)
        
        database.hasItem(
            "myTable",
            "myHashKey",
            "myRangeKey",
            "myHashValue",
            "myRangeValue"
        )

        val request = capturedRequest.captured
        kotlin.test.assertEquals("myTable", request.tableName())
        kotlin.test.assertEquals(2, request.key().size)
        kotlin.test.assertEquals("myHashValue", request.key()["myHashKey"]?.s())
        kotlin.test.assertEquals("myRangeValue", request.key()["myRangeKey"]?.s())
    }

    @Test
    fun `putItem calls dynamodb client correctly`() {
        val mockClient = mockk<DynamoDbClient>()
        val mockResponse = mockk<PutItemResponse>()
        
        every { mockClient.putItem(any<PutItemRequest>()) } returns mockResponse

        val database = Database(mockClient)
        
        database.putItem(
            "testTable",
            "hashKey",
            "rangeKey",
            "hashValue",
            "rangeValue"
        )

        verify(exactly = 1) { mockClient.putItem(any<PutItemRequest>()) }
    }

    @Test
    fun `putItem sends correct request parameters`() {
        val mockClient = mockk<DynamoDbClient>()
        val mockResponse = mockk<PutItemResponse>()
        val capturedRequest = slot<PutItemRequest>()
        
        every { mockClient.putItem(capture(capturedRequest)) } returns mockResponse

        val database = Database(mockClient)
        
        database.putItem(
            "myTable",
            "myHashKey",
            "myRangeKey",
            "myHashValue",
            "myRangeValue"
        )

        val request = capturedRequest.captured
        kotlin.test.assertEquals("myTable", request.tableName())
        kotlin.test.assertEquals(2, request.item().size)
        kotlin.test.assertEquals("myHashValue", request.item()["myHashKey"]?.s())
        kotlin.test.assertEquals("myRangeValue", request.item()["myRangeKey"]?.s())
    }
}
