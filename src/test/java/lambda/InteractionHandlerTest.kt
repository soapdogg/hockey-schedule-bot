package lambda

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lambda.CheckInTestData.KEY
import lambda.CheckInTestData.answer
import lambda.CheckInTestData.game
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Clock
import java.time.ZonedDateTime
import java.util.Base64
import java.util.HexFormat
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InteractionHandlerTest {
    private val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val publicKeyHex = HexFormat.of().formatHex(keys.public.encoded.takeLast(32).toByteArray())
    private val store = mockk<CheckInStore>()
    private val now = ZonedDateTime.of(2026, 11, 12, 10, 0, 0, 0, CheckIns.ZONE)
    private val handler = InteractionHandler(DiscordSignature(publicKeyHex), store, Clock.fixed(now.toInstant(), CheckIns.ZONE))

    private fun sign(timestamp: String, body: String): String {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keys.private)
        signer.update((timestamp + body).toByteArray())
        return HexFormat.of().formatHex(signer.sign())
    }

    private fun event(body: String, signature: String = sign("1700000000", body), base64: Boolean = false) = JsonObject(
        mapOf(
            "headers" to JsonObject(
                mapOf(
                    "x-signature-ed25519" to JsonPrimitive(signature),
                    "x-signature-timestamp" to JsonPrimitive("1700000000"),
                )
            ),
            "body" to JsonPrimitive(if (base64) Base64.getEncoder().encodeToString(body.toByteArray()) else body),
            "isBase64Encoded" to JsonPrimitive(base64),
        )
    )

    private fun press(position: Position, nick: String? = "Benny") = """
        {"type":3,"data":{"custom_id":"${CheckIns.buttonId(KEY, position)}","component_type":2},
         "member":{${nick?.let { "\"nick\":\"$it\"," } ?: ""}"user":{"id":"2","username":"ben","global_name":"Ben"}}}
    """.trimIndent()

    private fun reply(response: JsonObject): JsonObject {
        assertEquals(200, response.getValue("statusCode").jsonPrimitive.int)
        return Json.parseToJsonElement(response.getValue("body").jsonPrimitive.content).jsonObject
    }

    @Test
    fun `answers Discord's ping`() {
        assertEquals(1, reply(handler.handle(event("""{"type":1}"""))).getValue("type").jsonPrimitive.int)
        assertEquals(1, reply(handler.handle(event("""{"type":1}""", base64 = true))).getValue("type").jsonPrimitive.int)
    }

    @Test
    fun `refuses requests Discord didn't sign`() {
        val forged = event("""{"type":1}""", signature = sign("1700000000", """{"type":2}"""))
        assertEquals(401, handler.handle(forged).getValue("statusCode").jsonPrimitive.int)
        assertEquals(401, handler.handle(event("""{"type":1}""", signature = "zz")).getValue("statusCode").jsonPrimitive.int)
        assertFalse(DiscordSignature(publicKeyHex).verify(null, "1", "{}"))
    }

    @Test
    fun `a button press saves the answer and redraws the post`() {
        every { store.load(KEY) } returns CheckInState(game(), listOf(answer("2", "Ben", Position.OUT), answer("1", "Ava", Position.FORWARD)))
        val saved = slot<CheckInResponse>()
        every { store.saveResponse(any(), capture(saved)) } just runs

        val body = reply(handler.handle(event(press(Position.DEFENSE))))
        assertEquals(7, body.getValue("type").jsonPrimitive.int)
        val content = body.getValue("data").jsonObject.getValue("content").jsonPrimitive.content
        assertTrue("**Defense (1):** Benny" in content, content)
        assertTrue("**Out" !in content, content)
        assertEquals(CheckInResponse("2", "Benny", Position.DEFENSE, now.toInstant()), saved.captured)
    }

    @Test
    fun `uses the display name when there's no server nickname`() {
        every { store.load(KEY) } returns CheckInState(game(), emptyList())
        val saved = slot<CheckInResponse>()
        every { store.saveResponse(any(), capture(saved)) } just runs
        handler.handle(event(press(Position.GOALIE, nick = null)))
        assertEquals("Ben", saved.captured.name)
    }

    @Test
    fun `presses after the start or on unknown games only answer the presser`() {
        every { store.load(KEY) } returns CheckInState(game(closed = true), emptyList())
        val closed = reply(handler.handle(event(press(Position.FORWARD))))
        assertEquals(4, closed.getValue("type").jsonPrimitive.int)
        assertEquals(64, closed.getValue("data").jsonObject.getValue("flags").jsonPrimitive.int)

        every { store.load(KEY) } returns null
        assertEquals(4, reply(handler.handle(event(press(Position.FORWARD)))).getValue("type").jsonPrimitive.int)
        verify(exactly = 0) { store.saveResponse(any(), any()) }
    }
}
