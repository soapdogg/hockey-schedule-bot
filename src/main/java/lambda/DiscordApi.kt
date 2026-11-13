package lambda

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DiscordException(val status: Int, message: String) : RuntimeException(message)

/** The few Discord REST calls the bot makes as itself (rather than through a webhook). */
class DiscordApi(
    private val token: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = Fixtures.DISCORD_API_BASE_URL,
) {
    /** Posts a message and returns its ID. */
    fun createMessage(channelId: String, body: Map<String, Any>): String =
        call("POST", "/channels/$channelId/messages", body).jsonObject.getValue("id").jsonPrimitive.content

    fun editMessage(channelId: String, messageId: String, body: Map<String, Any>) {
        call("PATCH", "/channels/$channelId/messages/$messageId", body)
    }

    /** Deletes one of the bot's messages. Already gone is fine. */
    fun deleteMessage(channelId: String, messageId: String) {
        try {
            call("DELETE", "/channels/$channelId/messages/$messageId", null)
        } catch (e: DiscordException) {
            if (e.status != 404) throw e
        }
    }

    fun guildOf(channelId: String): String =
        call("GET", "/channels/$channelId", null).jsonObject.getValue("guild_id").jsonPrimitive.content

    /**
     * Everyone in the server except bots: user ID to the name people see (server nickname,
     * else display name, else username). Needs the Server Members intent turned on.
     */
    fun members(guildId: String): Map<String, String> {
        val members = linkedMapOf<String, String>()
        var after = "0"
        while (true) {
            val page = call("GET", "/guilds/$guildId/members?limit=1000&after=$after", null).jsonArray
            for (member in page) {
                val user = member.jsonObject["user"]?.jsonObject ?: continue
                if (user["bot"]?.jsonPrimitive?.booleanOrNull == true) continue
                val id = user.getValue("id").jsonPrimitive.content
                members[id] = member.jsonObject.string("nick") ?: user.string("global_name") ?: user.string("username") ?: id
            }
            if (page.size < 1000) return members
            after = page.last().jsonObject.getValue("user").jsonObject.getValue("id").jsonPrimitive.content
        }
    }

    fun sendDirectMessage(userId: String, body: Map<String, Any>) {
        val channel = call("POST", "/users/@me/channels", mapOf("recipient_id" to userId))
        createMessage(channel.jsonObject.getValue("id").jsonPrimitive.content, body)
    }

    private fun call(method: String, path: String, body: Map<String, Any>?, retried: Boolean = false): JsonElement {
        val requestBody = body?.let { JsonSerializer.serializeToString(it).toRequestBody(JSON) }
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bot $token")
            .header("User-Agent", USER_AGENT)
            .method(method, requestBody)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 429 && !retried) {
                val wait = runCatching { Json.parseToJsonElement(text).jsonObject["retry_after"]?.jsonPrimitive?.doubleOrNull }
                    .getOrNull() ?: 1.0
                Thread.sleep((wait.coerceAtMost(5.0) * 1000).toLong())
                return call(method, path, body, retried = true)
            }
            if (!response.isSuccessful) {
                throw DiscordException(response.code, "Discord $method ${path.substringBefore('?')} failed with ${response.code}: ${text.take(300)}")
            }
            return if (text.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(text)
        }
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** Discord asks bots to identify themselves in this format. */
        private const val USER_AGENT = "DiscordBot (https://github.com/soapdogg/hockey-schedule-bot, 1.0)"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
