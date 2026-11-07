package lambda

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shows what the insight engine finds, without logging anything:
 *
 *   InsightDryRun --team-id 10159 [--jsonl games.jsonl] [--post]
 *
 * Reads games from the GameArchive table (set AWS_REGION), or from a file written by
 * HistoricalScraper --jsonl. Prints every comparison with its statistics, then the candidates
 * and the pick for a recap of the latest game, the next game's announcement, and a midweek post.
 * With --post N, picks N insights of different kinds, worded as they'd be between games, and
 * posts them to GOATS_WEBHOOK_URL as one test message.
 */
object InsightDryRun {
    private val JSON = Json { ignoreUnknownKeys = true }

    @JvmStatic
    fun main(args: Array<String>) {
        val teamId = args.valueOf("--team-id") ?: error("--team-id is required")
        val jsonl = args.valueOf("--jsonl")
        val postCount = args.valueOf("--post")?.toIntOrNull()

        val games = if (jsonl != null) {
            File(jsonl).readLines().filter { it.isNotBlank() }.map { JSON.decodeFromString<ArchivedGame>(it) }
                .map { it.copy(us = PlayerAliases.apply(it.us)) }
        } else {
            GameArchive(DynamoDbClient.create()).games(teamId)
        }
        println("Loaded ${games.size} games.")

        val engine = InsightEngine()
        val facts = GameFacts.of(games)
        println("\n=== Comparisons (significant = survives the false-discovery-rate cut) ===")
        println(String.format(Locale.ENGLISH, "%-38s %10s %10s %7s %7s  %s", "split", "in", "out", "diff", "p", ""))
        for (r in engine.evaluateSplits(facts).sortedBy { it.pValue }) {
            println(
                String.format(
                    Locale.ENGLISH, "%-38s %10s %10s %+7.2f %7.4f  %s",
                    r.split.key.take(38), r.inRecord, r.outRecord, r.difference, r.pValue, if (r.significant) "significant" else "",
                )
            )
        }

        val httpClient = HttpClient(
            OkHttpClient.Builder()
                .callTimeout(60, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", Fixtures.HTTP_USER_AGENT).build())
                }
                .build()
        )
        val now = LocalDateTime.now(Clock.system(ZoneId.of("America/Los_Angeles")))
        val schedule = GameExtractor(httpClient).extractGames(
            Team(name = "dry-run", krakenWebsiteId = teamId, webhookChannel = "", apiChannelId = "")
        )
        val next = schedule.firstOrNull { it.result.isNullOrBlank() }

        val occasions = buildList {
            facts.lastOrNull()?.let { add("Recap of ${it.game.date} vs ${it.game.opponent}" to Occasion.Recap(it.game.gameId)) }
            if (next != null) {
                val start = runCatching { GameDateParser.parse(now, next.date + " @ " + next.time) }.getOrNull()
                add("Announcement of ${next.date} ${next.time} vs ${next.opponent} at ${next.arena}" to
                    Occasion.PreGame(next.opponent, next.arena, start, next.ha))
            }
            add("Midweek" to Occasion.Midweek)
        }

        val picks = mutableListOf<Insight>()
        for ((label, occasion) in occasions) {
            val candidates = engine.candidates(games, occasion).sortedByDescending { it.score }
            println("\n=== $label: ${candidates.size} candidate(s) ===")
            candidates.forEach { println(String.format(Locale.ENGLISH, "%5.2f  [%s] %s", it.score, it.type, it.text)) }
            // Treat earlier picks as just posted, so the picks differ like real posts would.
            val recent = picks.map { PostedInsight(it.key, it.type, it.subjects, Instant.now()) }
            val minScore = if (occasion == Occasion.Midweek) InsightEngine.MIDWEEK_MIN_SCORE else 0.0
            val pick = engine.choose(candidates, recent, Instant.now(), minScore)
            println("--> pick: ${pick?.text ?: "(nothing strong enough)"}")
            pick?.let { picks += it }
        }

        if (postCount != null) {
            val webhookUrl = System.getenv("GOATS_WEBHOOK_URL")
            if (webhookUrl.isNullOrEmpty()) error("GOATS_WEBHOOK_URL is not set")
            val general = engine.candidates(games, Occasion.Midweek)
            val samples = mutableListOf<Insight>()
            repeat(postCount) {
                val recent = samples.map { PostedInsight(it.key, it.type, it.subjects, Instant.now()) }
                engine.choose(general, recent, Instant.now())?.let { samples += it }
            }
            val content = "🧪 **Testing stat insights.** Game announcements and recaps will each come with one of these, " +
                "picked from whatever stands out at the time:\n\n" + samples.joinToString("\n") { RecapFormatter.insightLine(it.text) }
            println("\n=== Posting ===\n$content")
            httpClient.executeHttpPostRequest(webhookUrl, content)
            println("\nPosted ${samples.size} insight(s).")
        }
    }

    private fun Array<String>.valueOf(flag: String): String? = indexOf(flag).takeIf { it >= 0 }?.let { getOrNull(it + 1) }
}
