package lambda

import okhttp3.OkHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ScheduleBotRequestHandler() {

    fun handleRequest() {
        println("=== Hockey Schedule Bot Lambda Starting ===")
        println("Bot token available: ${if (Fixtures.BOT_TOKEN.isNotEmpty()) "Yes" else "No"}")
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", Fixtures.HTTP_USER_AGENT)
                    .build()
                chain.proceed(req)
            }
            .build()
        val httpClient = HttpClient(okHttp)
        val gameExtractor = GameExtractor(httpClient)
        val gameSummaryExtractor = GameSummaryExtractor(httpClient)
        val teamStatsExtractor = TeamStatsExtractor(httpClient)
        val dynamoDb = DynamoDbClient.create()
        val database = Database(dynamoDb)
        val gameArchive = GameArchive(dynamoDb)
        val gameArchiver = GameArchiver(
            ScoresheetExtractor(httpClient),
            SeasonDiscoverer(httpClient),
            gameArchive,
        )
        val insights = InsightService(gameArchive, InsightLog(dynamoDb))
        val teams = listOf(
            Team("Reindeer5D", "10159", Fixtures.GOATS_CHANNEL, Fixtures.GOATS_CHANNEL_ID),
        )
        val clock = Clock.system(ZoneId.of("America/Los_Angeles"))
        val now = LocalDateTime.now(clock)
        println("Current time (PST): $now")
        println("Processing ${teams.size} team(s)")

        val failures = mutableListOf<String>()
        teams.forEach { team ->
            try {
                println("\n--- Processing team: ${team.name} ---")

                val games = gameExtractor.extractGames(team)
                println("Found ${games.size} game(s) for team ${team.name}")

                // Archive first, so a recap's insight can include the game just played. Archiving
                // feeds analytics only, so a failure here shouldn't fail the run.
                try {
                    val archived = gameArchiver.archiveNewGames(team, games)
                    if (archived > 0) println("Archived $archived new game(s) for ${team.name}")
                } catch (e: Exception) {
                    println("WARNING: Failed to archive games for ${team.name}: ${e.message}")
                    e.printStackTrace()
                }

                var preGameInsightPosted = false
                val seasonContext by lazy { buildSeasonContext(teamStatsExtractor, team, games) }
                games.forEach { game ->
                    try {
                        println("\nProcessing game #${game.number}: ${game.opponent} on ${game.date} @ ${game.time}")

                        val hasGameBeenPreviouslyPublished = database.hasItem(
                            Fixtures.DEDUPE_TABLE_NAME,
                            Fixtures.TEAM_ATTRIBUTE_NAME,
                            Fixtures.GAME_ID_ATTRIBUTE_NAME,
                            team.name,
                            game.hash
                        )
                        println("Game already published: $hasGameBeenPreviouslyPublished")

                        if (!hasGameBeenPreviouslyPublished) {
                            println("Publishing new game announcement...")
                            val arenaLink = Fixtures.ARENAS_TO_LOCATION.getValue(game.arena)
                            val dateString = game.date + " @ " + game.time
                            // One insight per run at most, and only for games coming up soon.
                            val start = runCatching { GameDateParser.parse(now, dateString) }.getOrNull()
                            val preGame = Occasion.PreGame(game.opponent, game.arena, start, game.ha)
                            val insight = if (!preGameInsightPosted && start != null && Duration.between(now, start) <= InsightService.PRE_GAME_HORIZON) {
                                insights.pick(team, preGame)
                            } else {
                                null
                            }
                            val content = """
Game #${game.number} of the season is on **$dateString**.
We will be playing at [${game.arena}](${arenaLink}) against the [${game.opponent}](${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}${game.opponentLink} ). We are the ${game.ha} team.
""".trimIndent() + (insight?.let { "\n" + RecapFormatter.insightLine(it.text) } ?: "")

                            httpClient.executeHttpPostRequest(team.webhookChannel, content)
                            if (insight != null) {
                                preGameInsightPosted = true
                                insights.recordPosted(team, insight, preGame)
                            }

                            val gameLocalDateTime = GameDateParser.parse(now, dateString)

                            val pollExpiration = (Duration.between(now, gameLocalDateTime).seconds / (60 * 60)) - 1

                            println("Current time: $now")
                            println("Game local date time: $gameLocalDateTime")
                            println("Calculated poll expiration: $pollExpiration hours")

                            if (pollExpiration < Fixtures.MIN_POLL_DURATION_HOURS) {
                                println("WARNING: Poll expiration is less than ${Fixtures.MIN_POLL_DURATION_HOURS} hour ($pollExpiration). Skipping poll creation.")
                            } else if (pollExpiration > Fixtures.MAX_POLL_DURATION_HOURS) {
                                println("WARNING: Poll expiration is greater than ${Fixtures.MAX_POLL_DURATION_HOURS} hours ($pollExpiration). Setting to ${Fixtures.MAX_POLL_DURATION_HOURS}.")
                                httpClient.publishPoll(
                                    team.apiChannelId,
                                    question = "Are you in for the game against the ${game.opponent} on $dateString?",
                                    Fixtures.MAX_POLL_DURATION_HOURS
                                )
                            } else {
                                httpClient.publishPoll(
                                    team.apiChannelId,
                                    question = "Are you in for the game against the ${game.opponent} on $dateString?",
                                    pollExpiration.toInt()
                                )
                            }

                            database.putItem(
                                Fixtures.DEDUPE_TABLE_NAME,
                                Fixtures.TEAM_ATTRIBUTE_NAME,
                                Fixtures.GAME_ID_ATTRIBUTE_NAME,
                                team.name,
                                game.hash
                            )
                        }

                        if (game.gameId != null && RecapWindow.isOpen(now, game)) {
                            val recapKey = game.hash + Fixtures.RECAP_RANGE_SUFFIX
                            val recapAlreadyPublished = database.hasItem(
                                Fixtures.DEDUPE_TABLE_NAME,
                                Fixtures.TEAM_ATTRIBUTE_NAME,
                                Fixtures.GAME_ID_ATTRIBUTE_NAME,
                                team.name,
                                recapKey,
                            )
                            if (!recapAlreadyPublished) {
                                println("Checking recap readiness for game ${game.gameId}...")
                                val recap = gameSummaryExtractor.fetchIfReady(game.gameId)
                                if (recap == null) {
                                    println("Recap not ready yet for game ${game.gameId}; will retry next run.")
                                } else {
                                    println("Recap ready: ${recap.goals.size} goal(s), ${recap.penalties.size} penalty/ies.")
                                    val occasion = Occasion.Recap(game.gameId)
                                    val insight = insights.pick(team, occasion)
                                    val messages = RecapFormatter.format(game, recap, seasonContext, insight?.text)
                                    messages.forEach { httpClient.executeHttpPostRequest(team.webhookChannel, it) }
                                    insight?.let { insights.recordPosted(team, it, occasion) }
                                    database.putItem(
                                        Fixtures.DEDUPE_TABLE_NAME,
                                        Fixtures.TEAM_ATTRIBUTE_NAME,
                                        Fixtures.GAME_ID_ATTRIBUTE_NAME,
                                        team.name,
                                        recapKey,
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("ERROR: Failed to process game #${game.number} for team ${team.name}: ${e.message}")
                        e.printStackTrace()
                    }
                }

                // A post of its own between games, only when there's something strong to say.
                if (InsightService.isMidweekRun(now)) {
                    try {
                        insights.pick(team, Occasion.Midweek)?.let { insight ->
                            println("Posting midweek insight ${insight.key}")
                            httpClient.executeHttpPostRequest(team.webhookChannel, RecapFormatter.insightLine(insight.text))
                            insights.recordPosted(team, insight, Occasion.Midweek)
                        }
                    } catch (e: Exception) {
                        println("WARNING: Failed to post midweek insight for ${team.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("ERROR: Failed to process team ${team.name}: ${e.message}")
                e.printStackTrace()
                failures += "${team.name}: ${e.javaClass.simpleName}: ${e.message?.take(200)}"
            }
        }

        if (failures.isNotEmpty()) {
            val alertUrl = Fixtures.ALERT_WEBHOOK_URL
            if (alertUrl.isNotEmpty()) {
                try {
                    val content = ":rotating_light: Hockey bot run failed for ${failures.size} team(s):\n" +
                        failures.joinToString("\n") { "- $it" }
                    httpClient.executeHttpPostRequest(alertUrl, content)
                } catch (e: Exception) {
                    println("ERROR: Failed to publish alert webhook: ${e.message}")
                }
            }
            throw RuntimeException("Hockey bot run failed for ${failures.size} team(s): $failures")
        }
    }

    private fun buildSeasonContext(
        teamStatsExtractor: TeamStatsExtractor,
        team: Team,
        games: List<Game>,
    ): SeasonContext {
        val stats = try {
            teamStatsExtractor.fetch(team.krakenWebsiteId)
        } catch (e: Exception) {
            println("WARNING: Could not fetch team stats for ${team.name}; recap will omit season leaders: ${e.message}")
            null
        }
        val context = SeasonContext.from(games, stats)
        if (stats != null && context.stats == null) {
            println("Team stats page does not include the latest game yet; recap will omit season leaders.")
        }
        return context
    }
}
