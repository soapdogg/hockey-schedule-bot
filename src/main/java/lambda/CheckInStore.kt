package lambda

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

/**
 * One partition per game: an item for the post itself, and one per person who answered.
 * Everything expires a couple of months after the game.
 */
class CheckInStore(
    private val ddb: DynamoDbClient,
    private val tableName: String = Fixtures.CHECK_INS_TABLE_NAME,
) {
    fun load(key: String): CheckInState? {
        val items = mutableListOf<Map<String, AttributeValue>>()
        var startKey: Map<String, AttributeValue>? = null
        do {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#game = :game")
                .expressionAttributeNames(mapOf("#game" to GAME))
                .expressionAttributeValues(mapOf(":game" to AttributeValue.fromS(key)))
                // Strongly consistent, so an answer given a moment ago shows up.
                .consistentRead(true)
            startKey?.let { request.exclusiveStartKey(it) }
            val response = ddb.query(request.build())
            items += response.items()
            startKey = if (response.hasLastEvaluatedKey() && response.lastEvaluatedKey().isNotEmpty()) response.lastEvaluatedKey() else null
        } while (startKey != null)

        val game = items.firstOrNull { it[MEMBER]?.s() == GAME_ITEM }?.let { parseGame(key, it) } ?: return null
        val responses = items.filter { it[MEMBER]?.s() != GAME_ITEM }.mapNotNull { parseResponse(it) }
        return CheckInState(game, responses)
    }

    fun saveGame(game: CheckInGame) {
        val item = buildMap {
            put(GAME, AttributeValue.fromS(game.key))
            put(MEMBER, AttributeValue.fromS(GAME_ITEM))
            put("number", AttributeValue.fromS(game.number))
            put("opponent", AttributeValue.fromS(game.opponent))
            put("start", AttributeValue.fromS(game.start.toString()))
            put("arena", AttributeValue.fromS(game.arena))
            put("home-away", AttributeValue.fromS(game.homeAway))
            put("guild", AttributeValue.fromS(game.guildId))
            put("channel", AttributeValue.fromS(game.channelId))
            put("message", AttributeValue.fromS(game.messageId))
            put("roster", AttributeValue.fromM(game.roster.mapValues { AttributeValue.fromS(it.value) }))
            game.remindedSlot?.let { put("reminded-slot", AttributeValue.fromS(it)) }
            game.reminderMessageId?.let { put("reminder-message", AttributeValue.fromS(it)) }
            put("game-day-posted", AttributeValue.fromBool(game.gameDayPosted))
            put("direct-messages-sent", AttributeValue.fromBool(game.directMessagesSent))
            put("closed", AttributeValue.fromBool(game.closed))
            put(TTL, ttl(game.start))
        }
        ddb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    fun saveResponse(game: CheckInGame, response: CheckInResponse) {
        val item = mapOf(
            GAME to AttributeValue.fromS(game.key),
            MEMBER to AttributeValue.fromS(response.userId),
            "name" to AttributeValue.fromS(response.name),
            "position" to AttributeValue.fromS(response.position.name),
            "at" to AttributeValue.fromS(response.at.toString()),
            TTL to ttl(game.start),
        )
        ddb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    private fun parseGame(key: String, item: Map<String, AttributeValue>): CheckInGame? = runCatching {
        CheckInGame(
            key = key,
            number = item.getValue("number").s(),
            opponent = item.getValue("opponent").s(),
            start = LocalDateTime.parse(item.getValue("start").s()),
            arena = item.getValue("arena").s(),
            homeAway = item.getValue("home-away").s(),
            guildId = item.getValue("guild").s(),
            channelId = item.getValue("channel").s(),
            messageId = item.getValue("message").s(),
            roster = item["roster"]?.m()?.mapValues { it.value.s() } ?: emptyMap(),
            remindedSlot = item["reminded-slot"]?.s(),
            reminderMessageId = item["reminder-message"]?.s(),
            gameDayPosted = item["game-day-posted"]?.bool() ?: false,
            directMessagesSent = item["direct-messages-sent"]?.bool() ?: false,
            closed = item["closed"]?.bool() ?: false,
        )
    }.getOrNull()

    private fun parseResponse(item: Map<String, AttributeValue>): CheckInResponse? {
        return CheckInResponse(
            userId = item[MEMBER]?.s() ?: return null,
            name = item["name"]?.s() ?: return null,
            position = Position.of(item["position"]?.s()) ?: return null,
            at = item["at"]?.s()?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null,
        )
    }

    private fun ttl(start: LocalDateTime) =
        AttributeValue.fromN(start.atZone(CheckIns.ZONE).toInstant().plus(RETENTION).epochSecond.toString())

    companion object {
        const val GAME = "game"
        const val MEMBER = "member"
        const val TTL = "ttl"

        /** Sorts before any Discord user ID, which are all digits. */
        const val GAME_ITEM = "#game"
        val RETENTION: Duration = Duration.ofDays(60)
    }
}
