package lambda

import okhttp3.OkHttpClient
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleBotRequestHandler() {

    fun handleRequest() {
        println("=== Hockey Schedule Bot Lambda Starting ===")
        println("Bot token available: ${if (Fixtures.BOT_TOKEN != null && Fixtures.BOT_TOKEN.isNotEmpty()) "Yes" else "No"}")
        val httpClient = HttpClient(OkHttpClient())
        val gameExtractor = GameExtractor(httpClient)
        val database = Database(DynamoDbClient.create())
        val teams = listOf(
            Team("Reindeer5D", "10159", Fixtures.GOATS_CHANNEL, Fixtures.GOATS_CHANNEL_ID),
        )
        val clock = Clock.system(ZoneId.of("America/Los_Angeles"))
        val now = LocalDateTime.now(clock)
        println("Current time (PST): $now")
        println("Processing ${teams.size} team(s)")

        teams.forEach { team ->
            println("\n--- Processing team: ${team.name} ---")

            val games = gameExtractor.extractGames(team)
            println("Found ${games.size} game(s) for team ${team.name}")
            games.forEach { game ->
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
                    val content = """
Game #${game.number} of the season is on **$dateString**.
We will be playing at [${game.arena}](${arenaLink}) against the [${game.opponent}](${Fixtures.KRAKEN_HOCKEY_LEAGUE_WEBSITE}${game.opponentLink} ). We are the ${game.ha} team.
""".trimIndent()

                    httpClient.executeHttpPostRequest(team.webhookChannel, content)


                    val year = if(now.month == Month.DECEMBER && game.date.contains("JAN")) now.year  + 1 else now.year

                    val gameDateStringWithYear = dateString.replace(" @", " $year @");

                    val formatter = DateTimeFormatter.ofPattern("E, MMM d uuuu @ h:mm a")
                        .withLocale(Locale.ENGLISH);

                    val gameLocalDateTime = LocalDateTime.parse(gameDateStringWithYear, formatter)

                    val pollExpiration = (Duration.between(now, gameLocalDateTime).seconds / (60 * 60)) -1

                    println("Game date/time: $gameDateStringWithYear")
                    println("Current time: $now")
                    println("Game local date time: $gameLocalDateTime")
                    println("Calculated poll expiration: $pollExpiration hours")

                    if (pollExpiration < 1) {
                        println("WARNING: Poll expiration is less than 1 hour ($pollExpiration). Skipping poll creation.")
                    } else if (pollExpiration > 168) {
                        println("WARNING: Poll expiration is greater than 168 hours ($pollExpiration). Setting to 168.")
                        httpClient.publishPoll(
                            team.apiChannelId,
                            question = "Are you in for the game against the ${game.opponent} on $dateString?",
                            168
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
            }
        }
    }
}