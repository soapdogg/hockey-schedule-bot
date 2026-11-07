package lambda

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Insights the bot has posted, one item per post, kept for a year for cooldowns. */
class InsightLog(
    private val ddb: DynamoDbClient,
    private val tableName: String = Fixtures.INSIGHT_LOG_TABLE_NAME,
) {
    fun record(teamId: String, insight: Insight, occasion: String, at: Instant) {
        val item = mapOf(
            Fixtures.TEAM_ATTRIBUTE_NAME to AttributeValue.fromS(teamId),
            POSTED_AT to AttributeValue.fromS(sortKey(at, insight.key)),
            KEY to AttributeValue.fromS(insight.key),
            TYPE to AttributeValue.fromS(insight.type),
            SUBJECTS to AttributeValue.fromL(insight.subjects.map { AttributeValue.fromS(it) }),
            OCCASION to AttributeValue.fromS(occasion),
            TEXT to AttributeValue.fromS(insight.text),
            TTL to AttributeValue.fromN(at.plus(RETENTION).epochSecond.toString()),
        )
        ddb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build())
    }

    /** Posts since [since], oldest first. */
    fun recent(teamId: String, since: Instant): List<PostedInsight> {
        val posts = mutableListOf<PostedInsight>()
        var startKey: Map<String, AttributeValue>? = null
        do {
            val request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("#team = :team AND #at >= :since")
                .expressionAttributeNames(mapOf("#team" to Fixtures.TEAM_ATTRIBUTE_NAME, "#at" to POSTED_AT))
                .expressionAttributeValues(
                    mapOf(":team" to AttributeValue.fromS(teamId), ":since" to AttributeValue.fromS(timestamp(since)))
                )
                // Strongly consistent, so a post made earlier in the same run counts.
                .consistentRead(true)
            startKey?.let { request.exclusiveStartKey(it) }
            val response = ddb.query(request.build())
            posts += response.items().mapNotNull { parse(it) }
            startKey = if (response.hasLastEvaluatedKey() && response.lastEvaluatedKey().isNotEmpty()) response.lastEvaluatedKey() else null
        } while (startKey != null)
        return posts
    }

    private fun parse(item: Map<String, AttributeValue>): PostedInsight? {
        val postedAt = item[POSTED_AT]?.s()?.substringBefore('#')?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return PostedInsight(
            key = item[KEY]?.s() ?: return null,
            type = item[TYPE]?.s() ?: return null,
            subjects = item[SUBJECTS]?.l()?.mapNotNull { it.s() } ?: emptyList(),
            postedAt = postedAt,
        )
    }

    companion object {
        private const val POSTED_AT = "posted-at"
        private const val KEY = "insight-key"
        private const val TYPE = "type"
        private const val SUBJECTS = "subjects"
        private const val OCCASION = "occasion"
        private const val TEXT = "text"
        private const val TTL = "ttl"
        private val RETENTION: Duration = Duration.ofDays(365)

        /** Whole seconds, so every timestamp has the same length and sorts as text. */
        private fun timestamp(at: Instant): String = at.truncatedTo(ChronoUnit.SECONDS).toString()

        /** The insight key is appended so two posts in the same second don't collide. */
        internal fun sortKey(at: Instant, insightKey: String): String = "${timestamp(at)}#$insightKey"
    }
}
