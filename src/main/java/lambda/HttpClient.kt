package lambda

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class HttpClient(
    private val client: OkHttpClient
) {
    fun executeHttpGetRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP GET failed with status ${response.code} for $url")
                }
                response.body?.string() ?: throw IllegalStateException("Response body was null for GET $url")
            }
        } catch (e: Exception) {
            println("ERROR: HTTP GET request failed for $url: ${e.message}")
            throw e
        }
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

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    println("WARNING: Webhook POST failed with status ${response.code}: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            println("ERROR: Webhook POST request failed for $url: ${e.message}")
            throw e
        }
    }

    fun publishPoll(
        channelId: String,
        question: String,
        pollExpiration: Int
    ) {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val options = listOf("Forward", "Defense", "Flex", "Goalie", "Out")

        val pollData = mapOf(
            "poll" to mapOf(
                "question" to mapOf("text" to question),
                "answers" to options.map { mapOf("poll_media" to mapOf("text" to it)) },
                "duration" to pollExpiration,
                "allow_multiselect" to false
            )
        )

        val messageJson = JsonSerializer.serializeToString(pollData)

        println("Publishing poll to channel: $channelId")
        println("Poll expiration: $pollExpiration hours")
        println("Request body: $messageJson")

        val requestBody = messageJson.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${Fixtures.DISCORD_API_BASE_URL}/channels/$channelId/messages")
            .addHeader("Authorization", "Bot ${Fixtures.BOT_TOKEN}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                println("Poll publish response code: ${response.code}")
                val responseBody = response.body?.string()
                println("Poll publish response body: $responseBody")

                if (!response.isSuccessful) {
                    println("ERROR: Failed to publish poll. Status: ${response.code}, Body: $responseBody")
                } else {
                    println("Successfully published poll")
                }
            }
        } catch (e: Exception) {
            println("ERROR: Poll publish request failed for channel $channelId: ${e.message}")
            throw e
        }
    }
}
