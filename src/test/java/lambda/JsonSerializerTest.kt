package lambda

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonSerializerTest {

    @Test
    fun `toJsonElement converts number to JsonPrimitive`() {
        val result = JsonSerializer.run { 42.toJsonElement() }
        assertTrue(result is JsonPrimitive)
        assertEquals(42, (result as JsonPrimitive).content.toInt())
    }

    @Test
    fun `toJsonElement converts boolean to JsonPrimitive`() {
        val result = JsonSerializer.run { true.toJsonElement() }
        assertTrue(result is JsonPrimitive)
        assertEquals("true", (result as JsonPrimitive).content)
    }

    @Test
    fun `toJsonElement converts string to JsonPrimitive`() {
        val result = JsonSerializer.run { "test".toJsonElement() }
        assertTrue(result is JsonPrimitive)
        assertEquals("test", (result as JsonPrimitive).content)
    }

    @Test
    fun `toJsonElement converts array to JsonArray`() {
        val result = JsonSerializer.run { arrayOf(1, 2, 3).toJsonElement() }
        assertTrue(result is JsonArray)
        assertEquals(3, (result as JsonArray).size)
    }

    @Test
    fun `toJsonElement converts list to JsonArray`() {
        val result = JsonSerializer.run { listOf("a", "b", "c").toJsonElement() }
        assertTrue(result is JsonArray)
        assertEquals(3, (result as JsonArray).size)
    }

    @Test
    fun `toJsonElement converts map to JsonObject`() {
        val result = JsonSerializer.run { mapOf("key" to "value").toJsonElement() }
        assertTrue(result is JsonObject)
        val jsonObject = result as JsonObject
        assertEquals("value", jsonObject["key"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toJsonElement converts null to JsonNull`() {
        val result = JsonSerializer.run { null.toJsonElement() }
        assertTrue(result is JsonNull)
    }

    @Test
    fun `toJsonArray converts array correctly`() {
        val result = JsonSerializer.run { arrayOf(1, "test", true).toJsonArray() }
        assertEquals(3, result.size)
    }

    @Test
    fun `toJsonArray converts iterable correctly`() {
        val result = JsonSerializer.run { listOf(1, 2, 3).toJsonArray() }
        assertEquals(3, result.size)
    }

    @Test
    fun `toJsonObject converts map correctly`() {
        val result = JsonSerializer.run {
            mapOf(
                "name" to "John",
                "age" to 30,
                "active" to true
            ).toJsonObject()
        }
        assertEquals(3, result.size)
        assertTrue(result.containsKey("name"))
        assertTrue(result.containsKey("age"))
        assertTrue(result.containsKey("active"))
    }

    @Test
    fun `serializeToString serializes map correctly`() {
        val map = mapOf(
            "content" to "Hello World",
            "count" to 42
        )
        val result = JsonSerializer.serializeToString(map)
        assertTrue(result.contains("\"content\""))
        assertTrue(result.contains("Hello World"))
        assertTrue(result.contains("\"count\""))
        assertTrue(result.contains("42"))
    }

    @Test
    fun `serializeToString handles nested structures`() {
        val map = mapOf(
            "user" to mapOf(
                "name" to "Alice",
                "age" to 25
            ),
            "tags" to listOf("tag1", "tag2")
        )
        val result = JsonSerializer.serializeToString(map)
        assertTrue(result.contains("\"user\""))
        assertTrue(result.contains("\"name\""))
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("\"tags\""))
        assertTrue(result.contains("tag1"))
    }

    @Test
    fun `serializeToString handles empty map`() {
        val map = mapOf<String, Any>()
        val result = JsonSerializer.serializeToString(map)
        assertEquals("{}", result)
    }
}
