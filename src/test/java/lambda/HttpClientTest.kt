package lambda

import io.mockk.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals

class HttpClientTest {

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `executeHttpGetRequest returns response body`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val mockResponseBody = mockk<ResponseBody>()
        
        every { mockResponseBody.string() } returns "test response"
        every { mockResponse.body } returns mockResponseBody
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(any()) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        val result = httpClient.executeHttpGetRequest("http://test.com")

        assertEquals("test response", result)
        verify(exactly = 1) { mockOkHttpClient.newCall(any()) }
        verify(exactly = 1) { mockCall.execute() }
    }

    @Test
    fun `executeHttpGetRequest builds correct request`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val mockResponseBody = mockk<ResponseBody>()
        val requestSlot = slot<Request>()
        
        every { mockResponseBody.string() } returns "response"
        every { mockResponse.body } returns mockResponseBody
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(capture(requestSlot)) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        httpClient.executeHttpGetRequest("http://example.com/path")

        val capturedRequest = requestSlot.captured
        assertEquals("http://example.com/path", capturedRequest.url.toString())
        assertEquals("GET", capturedRequest.method)
    }

    @Test
    fun `executeHttpPostRequest sends content in body`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val requestSlot = slot<Request>()
        
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(capture(requestSlot)) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        httpClient.executeHttpPostRequest("http://webhook.com", "Hello World")

        val capturedRequest = requestSlot.captured
        assertEquals("POST", capturedRequest.method)
        assertEquals("http://webhook.com/", capturedRequest.url.toString())
        verify(exactly = 1) { mockCall.execute() }
    }

    @Test
    fun `executeHttpPostRequest sets correct headers`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val requestSlot = slot<Request>()
        
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(capture(requestSlot)) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        httpClient.executeHttpPostRequest("http://webhook.com", "test content")

        val capturedRequest = requestSlot.captured
        assertEquals("application/json", capturedRequest.header("accept"))
        assertEquals("application/json", capturedRequest.header("content-type"))
    }

    @Test
    fun `publishPoll sends correct request to Discord API`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val requestSlot = slot<Request>()
        
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(capture(requestSlot)) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        httpClient.publishPoll("123456", "Are you coming?", 24)

        val capturedRequest = requestSlot.captured
        assertEquals("POST", capturedRequest.method)
        assertEquals("https://discord.com/api/v10/channels/123456/messages", capturedRequest.url.toString())
        // Authorization header will have whatever is in DISCORDBOTTOKEN env var
        assertEquals("application/json", capturedRequest.header("Content-Type"))
    }

    @Test
    fun `publishPoll includes poll configuration in request body`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val requestSlot = slot<Request>()
        
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(capture(requestSlot)) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        httpClient.publishPoll("channelId", "Test question?", 12)

        val capturedRequest = requestSlot.captured
        val bodyBuffer = Buffer()
        capturedRequest.body?.writeTo(bodyBuffer)
        val bodyString = bodyBuffer.readUtf8()
        
        // Verify the poll structure
        assert(bodyString.contains("\"poll\""))
        assert(bodyString.contains("\"question\""))
        assert(bodyString.contains("Test question?"))
        assert(bodyString.contains("\"duration\""))
        assert(bodyString.contains("12"))
        assert(bodyString.contains("\"answers\""))
        
        // Verify poll options
        assert(bodyString.contains("Forward"))
        assert(bodyString.contains("Defense"))
        assert(bodyString.contains("Flex"))
        assert(bodyString.contains("Goalie"))
        assert(bodyString.contains("Out"))
    }

    @Test
    fun `publishPoll sets allow_multiselect to false`() {
        val mockOkHttpClient = mockk<OkHttpClient>()
        val mockCall = mockk<okhttp3.Call>()
        val mockResponse = mockk<Response>()
        val requestSlot = slot<Request>()
        
        every { mockCall.execute() } returns mockResponse
        every { mockOkHttpClient.newCall(capture(requestSlot)) } returns mockCall

        val httpClient = HttpClient(mockOkHttpClient)
        httpClient.publishPoll("channelId", "Question", 5)

        val capturedRequest = requestSlot.captured
        val bodyBuffer = Buffer()
        capturedRequest.body?.writeTo(bodyBuffer)
        val bodyString = bodyBuffer.readUtf8()
        
        assert(bodyString.contains("\"allow_multiselect\":false"))
    }
}
