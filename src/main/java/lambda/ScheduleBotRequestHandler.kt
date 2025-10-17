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
        val httpClient = HttpClient(OkHttpClient())
        val gameExtractor = GameExtractor(httpClient)
        val database = Database(DynamoDbClient.create())
        val teams = listOf(
            Team("Reindeer5D", "10159", Fixtures.GOATS_CHANNEL, Fixtures.GOATS_CHANNEL_ID),
        )
        val clock = Clock.system(ZoneId.of("America/Los_Angeles"))
        val now = LocalDateTime.now(clock)

        teams.forEach { team ->

            val games = gameExtractor.extractGames(team)
            games.forEach { game ->

                val hasGameBeenPreviouslyPublished = database.hasItem(
                    Fixtures.DEDUPE_TABLE_NAME,
                    Fixtures.TEAM_ATTRIBUTE_NAME,
                    Fixtures.GAME_ID_ATTRIBUTE_NAME,
                    team.name,
                    game.hash
                )

                if (!hasGameBeenPreviouslyPublished) {
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

                    httpClient.publishPoll(
                        team.apiChannelId,
                        question = "Are you in for the game against the ${game.opponent} on $dateString?",
                        pollExpiration.toInt()
                    )

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