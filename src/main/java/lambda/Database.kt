package lambda

import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

class Database(
    private val ddbClient: DynamoDbClient,
) {
    fun hasItem(
        tableName: String,
        hashKeyName: String,
        rangeKeyName: String,
        hashKeyValue: String,
        rangeKeyValue: String,
    ): Boolean {
        val request = GetItemRequest.builder()
            .key(
                mapOf(
                    hashKeyName to AttributeValue.fromS(hashKeyValue),
                    rangeKeyName to AttributeValue.fromS(rangeKeyValue)
                )
            )
            .tableName(tableName)
            .build()
        val response = ddbClient.getItem(request)
        return response.hasItem()
    }

    fun putItem(
        tableName: String,
        hashKeyName: String,
        rangeKeyName: String,
        hashKeyValue: String,
        rangeKeyValue: String
    ) {
        val request = PutItemRequest.builder()
            .item(
            mapOf(
                    hashKeyName to AttributeValue.fromS(hashKeyValue),
                    rangeKeyName to AttributeValue.fromS(rangeKeyValue)
                )
            )
            .tableName(tableName)
            .build()
        ddbClient.putItem(request)
    }

}