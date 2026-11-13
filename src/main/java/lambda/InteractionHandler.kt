package lambda

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.LocalDateTime
import java.util.Base64
import java.util.HexFormat

/**
 * Discord's interactions endpoint, behind a Lambda function URL: Discord sends every button
 * press here and shows whatever this returns. It has to answer within three seconds, so it
 * only touches DynamoDB and answers with the redrawn post.
 */
class InteractionHandler(
    private val signature: DiscordSignature = DiscordSignature(Fixtures.DISCORD_PUBLIC_KEY),
    private val store: CheckInStore = CheckInStore(DynamoDbClient.create()),
    private val clock: Clock = Clock.system(CheckIns.ZONE),
) : RequestStreamHandler {

    override fun handleRequest(input: InputStream, output: OutputStream, context: Context?) {
        val event = Json.parseToJsonElement(input.readBytes().decodeToString()).jsonObject
        output.write(handle(event).toString().toByteArray())
    }

    /** Takes a function URL event and returns the function URL response. */
    fun handle(event: JsonObject): JsonObject {
        val headers = event["headers"]?.jsonObject
        val rawBody = event["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val body = if (event["isBase64Encoded"]?.jsonPrimitive?.booleanOrNull == true) {
            Base64.getDecoder().decode(rawBody).decodeToString()
        } else {
            rawBody
        }
        // Discord checks that bad signatures are refused before it accepts the endpoint.
        if (!signature.verify(headers.header("x-signature-ed25519"), headers.header("x-signature-timestamp"), body)) {
            return response(401, """{"error":"invalid request signature"}""")
        }
        val interaction = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return response(400, """{"error":"bad request"}""")
        val reply = when (interaction["type"]?.jsonPrimitive?.intOrNull) {
            PING -> mapOf("type" to PONG)
            MESSAGE_COMPONENT -> button(interaction)
            else -> return response(400, """{"error":"unsupported interaction"}""")
        }
        return response(200, JsonSerializer.serializeToString(reply))
    }

    private fun button(interaction: JsonObject): Map<String, Any> {
        val customId = interaction["data"]?.jsonObject?.string("custom_id")
        val (key, position) = customId?.let { CheckIns.parseButtonId(it) } ?: return ephemeral("I don't know that button.")
        val member = interaction["member"]?.jsonObject
        val user = (member?.get("user") ?: interaction["user"])?.jsonObject ?: return ephemeral("I couldn't tell who you are.")
        val userId = user.string("id") ?: return ephemeral("I couldn't tell who you are.")
        val name = member?.string("nick") ?: user.string("global_name") ?: user.string("username") ?: userId

        val state = store.load(key) ?: return ephemeral("This check-in isn't open anymore.")
        if (state.game.closed || !state.game.start.isAfter(LocalDateTime.now(clock))) {
            return ephemeral("Check-ins for this game closed when it started.")
        }
        val answer = CheckInResponse(userId, name, position, clock.instant())
        store.saveResponse(state.game, answer)
        println("$name checked in as ${position.label} for $key")
        return mapOf("type" to UPDATE_MESSAGE, "data" to CheckInFormatter.post(state.with(answer)))
    }

    private fun ephemeral(text: String): Map<String, Any> =
        mapOf("type" to CHANNEL_MESSAGE, "data" to mapOf("content" to text, "flags" to EPHEMERAL))

    private fun response(status: Int, body: String) = JsonObject(
        mapOf(
            "statusCode" to JsonPrimitive(status),
            "headers" to JsonObject(mapOf("content-type" to JsonPrimitive("application/json"))),
            "body" to JsonPrimitive(body),
        )
    )

    private fun JsonObject?.header(name: String): String? =
        this?.entries?.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.jsonPrimitive?.contentOrNull

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private const val PING = 1
        private const val MESSAGE_COMPONENT = 3
        private const val PONG = 1
        private const val CHANNEL_MESSAGE = 4
        private const val UPDATE_MESSAGE = 7
        private const val EPHEMERAL = 64
    }
}

/** Checks Discord's Ed25519 signature over the timestamp and body of a request. */
class DiscordSignature(publicKeyHex: String) {
    private val key: PublicKey = KeyFactory.getInstance("Ed25519")
        .generatePublic(X509EncodedKeySpec(X509_PREFIX + HexFormat.of().parseHex(publicKeyHex)))

    fun verify(signatureHex: String?, timestamp: String?, body: String): Boolean {
        if (signatureHex == null || timestamp == null) return false
        return runCatching {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(key)
            verifier.update((timestamp + body).toByteArray())
            verifier.verify(HexFormat.of().parseHex(signatureHex))
        }.getOrDefault(false)
    }

    companion object {
        /** The DER header that wraps a raw 32-byte Ed25519 public key. */
        private val X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100")
    }
}
