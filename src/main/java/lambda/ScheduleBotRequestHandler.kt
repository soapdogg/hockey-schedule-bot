package lambda

import com.vdurmont.emoji.EmojiParser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

class ScheduleBotRequestHandler {

    val GOATS_CHANNEL = "https://discord.com/api/webhooks/1246515419158675587/hivn6lT_vPJgWkWh9i-TgOxtXlf-tu0KwH08L9yLq2ogKY-YkgZRrmhewF_tYxYJQbic"

    fun handleRequest() {
        val baseUrl = "https://krakenhockeyleague.com"

        val teams = listOf(
            Pair("10159", "Reindeer5D")
        )
        val arenas = mapOf(
            "KVIC" to "https://maps.app.goo.gl/587sGU8sbtiGcVsp6",
            "KCI VMFH" to "https://maps.app.goo.gl/GTwM6Tx1aicFdjQ48",
            "KCI Star" to "https://maps.app.goo.gl/GTwM6Tx1aicFdjQ48",
            "KCI Smrt" to "https://maps.app.goo.gl/GTwM6Tx1aicFdjQ48",
            "LIC" to "https://maps.app.goo.gl/bmzZUokkZv41YT3M8",
            "OVA" to "https://maps.app.goo.gl/VPftVaLwq9a8oeZD6",
            "EVT COMM" to "https://maps.app.goo.gl/CBQBocbYnGbMUJEp7",
            "ANGEL" to "https://maps.app.goo.gl/CBQBocbYnGbMUJEp7",
            "SHOW" to "https://maps.app.goo.gl/WKzp4AtNHahcKdqu8",
        )

        val ddbClient = DynamoDbClient.create()

        teams.forEach { team ->
            val scheduleUrl = "$baseUrl/team/${team.first}/schedule/?season=2129"
            val schedule = executeHttpGetRequest(scheduleUrl)
            val doc = Jsoup.parse(schedule)
            doc.select("table")?.forEach{ month->
                month.select("tbody")?.forEach { m ->

                    m.select("tr")?.forEach { game ->
                        val gameData = game?.select("td")
                        if (gameData != null && gameData.size >= 6) {
                            val gameNumber = gameData[0]?.text()
                            val gameDate = gameData[1]?.text()
                            val gameTime = gameData[2]?.text()
                            val location = gameData[3]?.text()
                            val arenaLink = arenas[location]
                            val ha = gameData[4]?.text()
                            val opponent = gameData[5]?.text()
                            val opponentLink = gameData[5]?.select("a")?.attr("href")

                            val teamName = team.second

                            val hash = hashAll(gameNumber, gameDate, gameTime, location, ha, opponent)

                            val dedupeResult = ddbClient.getItem(
                                GetItemRequest.builder()
                                    .key(
                                        mapOf(
                                            "team" to AttributeValue.fromS(teamName),
                                            "game-id" to AttributeValue.fromS(hash.toString())
                                        )
                                    )
                                    .tableName("DedupeTable")
                                    .build()
                            )

                            if (!dedupeResult.hasItem()) {
                                val str = """
Game #$gameNumber of the season is on **$gameDate @ $gameTime**.
We will be playing at [$location]($arenaLink) against the [$opponent]($baseUrl$opponentLink ). We are the $ha team.
Reply with :thumbsup: if you are IN, reply with :thumbsdown:if you are OUT.""".trimIndent()

                                val result = EmojiParser.parseToAliases(str)


                                val client = OkHttpClient()

                                val mediaType = "application/json".toMediaTypeOrNull()

                                val params = mapOf("content" to result)
                                val body = serializeToString(params)

                                val requestBody = RequestBody.create(mediaType, body)

                                val request = Request.Builder()
                                    .url(GOATS_CHANNEL)
                                    .post(requestBody)
                                    .addHeader("accept", "application/json")
                                    .addHeader("content-type", "application/json")
                                    .build()

                                client.newCall(request).execute()


                                ddbClient.putItem(
                                    PutItemRequest.builder().item(
                                        mapOf(
                                            "team" to AttributeValue.fromS(teamName),
                                            "game-id" to AttributeValue.fromS(hash.toString())
                                        )
                                    ).tableName("DedupeTable")
                                        .build()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun executeHttpGetRequest(url: String): String {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        println("Request: ${request.url}")

        val response = client.newCall(request).execute()
        println("Response: ${response.code}")

        return response.body?.string()!!
    }

    private fun hashAll(vararg vals: Any?): Long {
        var res = 0L
        for (v in vals) {
            res += v.hashCode().toLong()
            res *= 31L
        }
        return res
    }

    fun Any?.toJsonElement(): JsonElement = when (this) {
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Array<*> -> this.toJsonArray()
        is List<*> -> this.toJsonArray()
        is Map<*, *> -> this.toJsonObject()
        is JsonElement -> this
        else -> JsonNull
    }

    fun Array<*>.toJsonArray() = JsonArray(map { it.toJsonElement() })
    fun Iterable<*>.toJsonArray() = JsonArray(map { it.toJsonElement() })
    fun Map<*, *>.toJsonObject() = JsonObject(mapKeys { it.key.toString() }.mapValues { it.value.toJsonElement() })

    fun serializeToString(map: Map<String, Any>): String = Json.encodeToString(map.toJsonElement())
}