package lambda

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class HttpClient(
    private val client: OkHttpClient
) {
    fun executeHttpGetRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        val response = client.newCall(request).execute()

        return response.body?.string()!!
    }

    fun executeHttpPostRequest(url: String, content: String) {
        val mediaType = "application/json".toMediaTypeOrNull()

        val params = mapOf("content" to content)
        val body = JsonSerializer.serializeToString(params)

        val requestBody = RequestBody.create(mediaType, body)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("accept", "application/json")
            .addHeader("content-type", "application/json")
            .build()

        client.newCall(request).execute()
    }

    fun publishPoll(
        channelId: String,
        question: String,
        pollExpiration: Int
    ) {
        val baseUrl = "https://discord.com/api/v10"
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val options = listOf("Forward", "Defense", "Flex", "Goalie", "Out")
        // Create poll using Discord's poll feature (introduced in 2024)
        val pollJson = JSONObject().apply {
            put("question", JSONObject().put("text", question))
            put("answers", JSONArray().apply {
                options.forEachIndexed { index, option ->
                    put(JSONObject().apply {
                        put("poll_media", JSONObject().put("text", option))
                    })
                }
            })
            put("duration", pollExpiration) // Duration in hours (1-168)
            put("allow_multiselect", false)
        }

        val messageJson = JSONObject().apply {
            put("poll", pollJson)
        }

        val requestBody = messageJson.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/channels/$channelId/messages")
            .addHeader("Authorization", "Bot ${Fixtures.BOT_TOKEN}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute()
    }
}