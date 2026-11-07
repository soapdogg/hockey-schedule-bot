package lambda

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Renders a recap without touching DynamoDB. Usage: `RecapDryRun [gameId] [teamId]`.
 * With a team ID, the real schedule entry and season stats are used, as in production.
 * Posts to GOATS_WEBHOOK_URL only if that variable is set.
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

        val (game, season) = if (teamId == null) {
            syntheticGame(gameId, recap) to null
        } else {
            val team = Team(name = "dry-run", krakenWebsiteId = teamId, webhookChannel = "", apiChannelId = "")
            val games = GameExtractor(httpClient).extractGames(team)
            val game = games.firstOrNull { it.gameId == gameId }
                ?: error("Game $gameId is not on team $teamId's current schedule.")
            game to SeasonContext.from(games, TeamStatsExtractor(httpClient).fetch(teamId))
        }

        val messages = RecapFormatter.format(game, recap, season)
        println("Generated ${messages.size} message(s).")
        messages.forEachIndexed { i, msg ->
            println("--- message ${i + 1} (${msg.length} chars) ---")
            println(msg)
        }
        if (webhookUrl.isNullOrEmpty()) {
            println("\nGOATS_WEBHOOK_URL not set — skipping POST (dry-run only).")
        } else {
            println("\nPosting to webhook...")
            messages.forEach { httpClient.executeHttpPostRequest(webhookUrl, it) }
            println("Posted.")
        }
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
