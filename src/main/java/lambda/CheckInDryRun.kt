package lambda

import okhttp3.OkHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs the check-in steps for one game against a test channel:
 *
 *   CheckInDryRun --channel <channel id> [--team-id 10159] [--game 8] [--now 2026-11-16T08:00]
 *
 * Needs DISCORDBOTTOKEN and AWS_REGION. Uses the next game on the schedule unless --game says
 * otherwise. Pretends it's --now, so running it again with later times steps through the week:
 * the post, reminders, the game-day post and closing. Test posts are kept apart from real ones
 * in the CheckIns table, and the insight on the game-day post isn't logged.
 */
object CheckInDryRun {
    @JvmStatic
    fun main(args: Array<String>) {
        val channelId = args.valueOf("--channel") ?: error("--channel is required")
        val teamId = args.valueOf("--team-id") ?: "10159"
        val now = args.valueOf("--now")?.let { LocalDateTime.parse(it) } ?: LocalDateTime.now(CheckIns.ZONE)
        val token = System.getenv("DISCORDBOTTOKEN")?.takeIf { it.isNotBlank() } ?: error("DISCORDBOTTOKEN is not set")

        val httpClient = HttpClient(
            OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", Fixtures.HTTP_USER_AGENT).build())
                }
                .build()
        )
        val team = Team(name = "dry-run", krakenWebsiteId = teamId, webhookChannel = "", apiChannelId = channelId)
        val schedule = GameExtractor(httpClient).extractGames(team)
        val game = args.valueOf("--game")?.let { number -> schedule.firstOrNull { it.number == number } ?: error("No game #$number") }
            ?: schedule.firstOrNull { it.result.isNullOrBlank() }
            ?: error("No upcoming game on the schedule")
        println("Game #${game.number} vs ${game.opponent} on ${game.date} @ ${game.time}, pretending it's $now")

        val dynamoDb = DynamoDbClient.create()
        val insights = InsightService(GameArchive(dynamoDb), InsightLog(dynamoDb))
        CheckInService(
            DiscordApi(token),
            CheckInStore(dynamoDb),
            channelId,
            pickInsight = { insights.pick(team, it) },
            keyPrefix = "test:",
        ).run(team, listOf(game), now)
        println("Done.")
    }

    private fun Array<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }
}
