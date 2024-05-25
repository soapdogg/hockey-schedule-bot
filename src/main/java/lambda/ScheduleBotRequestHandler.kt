package lambda

import com.vdurmont.emoji.EmojiParser
import org.http4k.client.Java8HttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.jsoup.Jsoup
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class ScheduleBotRequestHandler {

    fun handleRequest() {
        println("Hello from scheduler")

        val base_url = "https://krakenhockeyleague.com"

        val teams = listOf(Pair("10159", "Reindeer5D"), Pair("9943", "Seals6A"))
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
            val scheduleUrl = "$base_url/team/${team.first}/schedule"
            val schedule = executeHttpGetRequest(scheduleUrl)
            val doc = Jsoup.parse(schedule)
            val table = doc.select("table").last()

            table?.select("tbody")?.forEach {
                val game = it.select("tr").last()
                val gameData = game?.select("td")
                val gameNumber = gameData?.get(0)?.text()
                val gameDate = gameData?.get(1)?.text()
                val gameTime = gameData?.get(2)?.text()
                val location = gameData?.get(3)?.text()
                val arenaLink = arenas[location]
                val ha = gameData?.get(4)?.text()
                val opponent = gameData?.get(5)?.text()
                val opponentLink = gameData?.get(5)?.select("a")?.attr("href")

                val teamName = team.second

                val hash = hashAll(gameNumber, gameDate, gameTime, location, ha, opponent)

                val dedupeResult = ddbClient.getItem (GetItemRequest.builder()
                    .key(mapOf(
                        "team" to AttributeValue.fromS(teamName),
                        "game-id" to AttributeValue.fromS(hash.toString())
                    ))
                    .tableName("DedupeTable")
                    .build()
                )

                if (!dedupeResult.hasItem()) {


                    val str = """
Game #$gameNumber of the season is on **$gameDate @ $gameTime**.
We will be playing at [$location]($arenaLink) against the [$opponent]($base_url$opponentLink ). We are the $ha team.
Reply with :thumbsup: if you are IN, reply with :thumbsdown:if you are OUT.""".trimIndent()

                    val result = EmojiParser.parseToAliases(str)

                    val sqsClient = SqsClient.create()
                    sqsClient.sendMessage(
                        SendMessageRequest.builder()
                            .messageBody(result)
                            .queueUrl("https://sqs.us-west-2.amazonaws.com/245053569556/RelayQueue")
                            .build()
                    )

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

    private fun executeHttpGetRequest(url: String): String {
        val request = Request(Method.GET, url)

        println("Request: ${request.uri}")

        val client = Java8HttpClient()
        val response = client(request)

        println("Response: ${response.status}")

        return response.bodyString()
    }

    private fun hashAll(vararg vals: Any?): Long {
        var res = 0L
        for (v in vals) {
            res += v.hashCode().toLong()
            res *= 31L
        }
        return res
    }
}