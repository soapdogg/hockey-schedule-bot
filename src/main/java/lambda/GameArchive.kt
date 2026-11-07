package lambda

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest

/**
 * Completed games in DynamoDB: one item per game, keyed by league team ID and game ID, with
 * the whole game stored as JSON. Analytics read every game for a team and work in memory,
 * which is cheap at a few hundred games.
 */
class GameArchive(
    private val ddb: DynamoDbClient,
    private val tableName: String = Fixtures.GAME_ARCHIVE_TABLE_NAME,
) {
    fun put(teamId: String, game: ArchivedGame) {
        val item = mutableMapOf(
            Fixtures.TEAM_ATTRIBUTE_NAME to AttributeValue.fromS(teamId),
            Fixtures.GAME_ID_ATTRIBUTE_NAME to AttributeValue.fromS(game.gameId),
            SEASON_ID to AttributeValue.fromS(game.seasonId),
            SCHEMA to AttributeValue.fromN(SCHEMA_VERSION.toString()),
            DATA to AttributeValue.fromS(toJson(game)),
        )
        game.date?.let { item[DATE] = AttributeValue.fromS(it) }
        ddb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    fun gameIds(teamId: String): Set<String> =
        query(teamId, projection = Fixtures.GAME_ID_ATTRIBUTE_NAME)
            .mapNotNull { it[Fixtures.GAME_ID_ATTRIBUTE_NAME]?.s() }
            .toSet()

    /** Every archived game for the team, with our players' spellings merged by [PlayerAliases]. */
    fun games(teamId: String): List<ArchivedGame> =
        query(teamId, projection = null)
            .mapNotNull { it[DATA]?.s() }
            .map { JSON.decodeFromString<ArchivedGame>(it) }
            .map { it.copy(us = PlayerAliases.apply(it.us)) }

    private fun query(teamId: String, projection: String?): List<Map<String, AttributeValue>> {
        val items = mutableListOf<Map<String, AttributeValue>>()
        var startKey: Map<String, AttributeValue>? = null
        do {
            val names = mutableMapOf("#team" to Fixtures.TEAM_ATTRIBUTE_NAME)
            val request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#team = :team")
                .expressionAttributeValues(mapOf(":team" to AttributeValue.fromS(teamId)))
            if (projection != null) {
                names["#projected"] = projection
                request.projectionExpression("#projected")
            }
            request.expressionAttributeNames(names)
            startKey?.let { request.exclusiveStartKey(it) }

            val response = ddb.query(request.build())
            items += response.items()
            startKey = if (response.hasLastEvaluatedKey() && response.lastEvaluatedKey().isNotEmpty()) {
                response.lastEvaluatedKey()
            } else {
                null
            }
        } while (startKey != null)
        return items
    }

    companion object {
        const val SCHEMA_VERSION = 1
        private const val SEASON_ID = "season-id"
        private const val SCHEMA = "schema"
        private const val DATA = "data"
        private const val DATE = "date"
        private val JSON = Json { ignoreUnknownKeys = true }

        /** The JSON stored for each game; also what HistoricalScraper writes with --jsonl. */
        fun toJson(game: ArchivedGame): String = JSON.encodeToString(game)
    }
}
