package lambda

import okhttp3.OkHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.util.concurrent.TimeUnit

/**
 * Renders a recap without writing anything. Usage: `RecapDryRun [gameId] [teamId]`.
 * With a team ID, the real schedule entry and season stats are used, as in production, and an
 * insight is picked from the GameArchive table (set AWS_REGION). The insight isn't logged, so
 * it doesn't put anything on cooldown. Posts to GOATS_WEBHOOK_URL only if that variable is set,
 * with a line on top saying it's a test.
 */
object RecapDryRun {
    @JvmStatic
    fun main(args: Array<String>) {
        val webhookUrl = System.getenv("GOATS_WEBHOOK_URL")
        val gameId = args.firstOrNull() ?: "55483"
        val teamId = args.getOrNull(1)

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", Fixtures.HTTP_USER_AGENT)
                        .build()
                )
            }
            .build()

        val httpClient = HttpClient(okHttp)
        val recap = GameSummaryExtractor(httpClient).fetchIfReady(gameId)
            ?: error("Game $gameId is not ready — SCORING SUMMARY or scoring table missing.")

        val team = teamId?.let { Team(name = "dry-run", krakenWebsiteId = it, webhookChannel = "", apiChannelId = "") }
        val (game, season) = if (team == null) {
            syntheticGame(gameId, recap) to null
        } else {
            val games = GameExtractor(httpClient).extractGames(team)
            val game = games.firstOrNull { it.gameId == gameId }
                ?: error("Game $gameId is not on team $teamId's current schedule.")
            game to SeasonContext.from(games, TeamStatsExtractor(httpClient).fetch(team.krakenWebsiteId))
        }
        val insight = team?.let { pickInsight(it, gameId) }

        val messages = RecapFormatter.format(game, recap, season, insight?.text)
        println("Generated ${messages.size} message(s).")
        messages.forEachIndexed { i, msg ->
            println("--- message ${i + 1} (${msg.length} chars) ---")
            println(msg)
        }
        if (webhookUrl.isNullOrEmpty()) {
            println("\nGOATS_WEBHOOK_URL not set — skipping POST (dry-run only).")
        } else {
            println("\nPosting to webhook...")
            val labelled = listOf(TEST_LABEL + messages.first()) + messages.drop(1)
            labelled.forEach { httpClient.executeHttpPostRequest(webhookUrl, it) }
            println("Posted.")
        }
    }

    private const val TEST_LABEL = "🧪 **Test recap** of an earlier game, not a new result.\n\n"

    private fun pickInsight(team: Team, gameId: String): Insight? = try {
        val dynamoDb = DynamoDbClient.create()
        InsightService(GameArchive(dynamoDb), InsightLog(dynamoDb)).pick(team, Occasion.Recap(gameId))
    } catch (e: Exception) {
        println("No insight: ${e.message}")
        null
    }

    private fun syntheticGame(gameId: String, recap: Recap) = Game(
        number = "?",
        date = "(dry-run)",
        time = "",
        arena = "",
        ha = "",
        opponent = recap.teams.getOrNull(1)?.name ?: "Opponent",
        opponentLink = "",
        hash = "dry-run-$gameId",
        gameId = gameId,
        result = null,
        score = "${recap.teams[0].total} - ${recap.teams[1].total}",
    )
}
