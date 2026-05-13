package lambda

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object RecapDryRun {
    @JvmStatic
    fun main(args: Array<String>) {
        val webhookUrl = System.getenv("GOATS_WEBHOOK_URL")
        val gameId = args.firstOrNull() ?: "55483"

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

        val syntheticGame = Game(
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

        val messages = RecapFormatter.format(syntheticGame, recap)
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
}
