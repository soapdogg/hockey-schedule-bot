package lambda

import okhttp3.OkHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Backfills past seasons from the league site into the DynamoDB game archive (--table) and/or a
 * local JSON Lines file of exactly what DynamoDB would store (--jsonl).
 * Safe to re-run: games are overwritten, not duplicated.
 */
object HistoricalScraper {

    private data class Args(
        val teamId: String,
        val tableName: String?,
        val jsonlPath: String?,
        val seasonFilter: Set<String>,
        val sleepMillis: Long,
    )

    @JvmStatic
    fun main(rawArgs: Array<String>) {
        val args = parseArgs(rawArgs) ?: run {
            printUsage()
            System.exit(2)
            return
        }

        val httpClient = buildHttpClient()
        val gameExtractor = GameExtractor(httpClient)
        val scoresheetExtractor = ScoresheetExtractor(httpClient)
        val seasonDiscoverer = SeasonDiscoverer(httpClient)

        val allSeasons = try {
            seasonDiscoverer.discover(args.teamId)
        } catch (e: Exception) {
            System.err.println("Failed to discover seasons for team ${args.teamId}: ${e.message}")
            System.exit(1)
            return
        }
        if (allSeasons.isEmpty()) {
            System.err.println("No seasons found for team ${args.teamId}")
            System.exit(1)
            return
        }

        val seasons = if (args.seasonFilter.isEmpty()) allSeasons else
            allSeasons.filter { it.seasonId in args.seasonFilter }
        println("Discovered ${allSeasons.size} season(s); scraping ${seasons.size}.")

        val dynamo = args.tableName?.let { GameArchive(DynamoDbClient.create(), it) }
        val jsonl = args.jsonlPath?.let { File(it).bufferedWriter() }
        val team = Team(name = "historical", krakenWebsiteId = args.teamId, webhookChannel = "", apiChannelId = "")

        var totalArchived = 0
        var totalSkipped = 0
        var totalFailed = 0
        try {
            for (season in seasons) {
                println("==> Season ${season.seasonId}  (${season.label})")
                val schedule = try {
                    gameExtractor.extractGames(team, season = season.seasonId)
                } catch (e: Exception) {
                    println("  FAILED to fetch schedule: ${e.message}")
                    totalFailed += 1
                    continue
                }

                val completed = schedule.filter { it.gameId != null }
                println("  ${schedule.size} schedule rows, ${completed.size} completed")

                for (game in completed) {
                    val gameId = game.gameId ?: continue
                    Thread.sleep(args.sleepMillis)
                    val sheet = try {
                        scoresheetExtractor.fetch(gameId)
                    } catch (e: Exception) {
                        println("  [$gameId] FETCH ERROR: ${e.message}")
                        totalFailed += 1
                        continue
                    }
                    val archived = ArchivedGame.from(game, sheet, season.seasonId, season.label)
                    if (archived == null) {
                        println("  [$gameId] no official result or our side of the scoresheet is empty — skipping")
                        totalSkipped += 1
                        continue
                    }
                    try {
                        dynamo?.put(args.teamId, archived)
                        jsonl?.apply { write(GameArchive.toJson(archived)); newLine() }
                        println("  [$gameId] ${archived.date ?: archived.dateText} vs ${archived.opponent} — ${archived.result} ${archived.ourScore}-${archived.oppScore}")
                        totalArchived += 1
                    } catch (e: Exception) {
                        println("  [$gameId] WRITE ERROR: ${e.message}")
                        totalFailed += 1
                    }
                }
            }
        } finally {
            jsonl?.close()
        }

        println()
        println("Done. Archived=$totalArchived  Skipped=$totalSkipped  Failed=$totalFailed")
        args.tableName?.let { println("DynamoDB table: $it") }
        args.jsonlPath?.let { println("JSON Lines file: $it") }
    }

    private fun buildHttpClient(): HttpClient {
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
        return HttpClient(okHttp)
    }

    private fun parseArgs(args: Array<String>): Args? {
        var teamId: String? = null
        var tableName: String? = null
        var jsonlPath: String? = null
        var sleepMillis = 250L
        val seasonFilter = mutableSetOf<String>()

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "--team-id" -> { teamId = args.getOrNull(++i) ?: return null }
                "--table" -> { tableName = args.getOrNull(++i) ?: return null }
                "--jsonl" -> { jsonlPath = args.getOrNull(++i) ?: return null }
                "--season" -> { seasonFilter += args.getOrNull(++i) ?: return null }
                "--sleep-ms" -> { sleepMillis = args.getOrNull(++i)?.toLongOrNull() ?: return null }
                else -> {
                    System.err.println("Unknown argument: $arg")
                    return null
                }
            }
            i += 1
        }

        if (teamId.isNullOrBlank()) return null
        if (listOf(tableName, jsonlPath).all { it.isNullOrBlank() }) return null
        return Args(teamId, tableName, jsonlPath, seasonFilter, sleepMillis)
    }

    private fun printUsage() {
        System.err.println(
            """
            Usage:
              HistoricalScraper --team-id 10159 \
                                [--table ${Fixtures.GAME_ARCHIVE_TABLE_NAME}] \
                                [--jsonl games.jsonl] \
                                [--season <id>]... \
                                [--sleep-ms 250]

              Give --table to write to DynamoDB (uses your default AWS credentials and region),
              --jsonl to write the stored JSON to a local file (a dry run for --table), or both.
              Without --season flags, every discovered season is scraped.
            """.trimIndent()
        )
    }
}
