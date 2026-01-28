package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.scheduler.CfnSchedule;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.List;
import java.util.Map;

public class HockeyDiscordBotStack extends Stack {
    public static final String MAVEN_ASSET_CODE_PATH = "target/hockey-discord-bot-0.1.jar";


    public HockeyDiscordBotStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public HockeyDiscordBotStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        final var hockeyScheduleBotTimeout = Duration.seconds(60);

        // Get configuration from environment variables
        final var discordBotToken = getRequiredEnv("DISCORDBOTTOKEN");
        final var goatsWebhookUrl = getRequiredEnv("GOATS_WEBHOOK_URL");
        final var goatsChannelId = getRequiredEnv("GOATS_CHANNEL_ID");

        final var dedupeTable = Table.Builder.create(this, "DedupeTable")
            .tableName("DedupeTable")
            .partitionKey(Attribute.builder().name("team").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("game-id").type(AttributeType.STRING).build())
            .timeToLiveAttribute("ttl")
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build();

        final var hockeyScheduleBotFunction = Function.Builder.create(this, "HockeyScheduleBot")
            .functionName("HockeyScheduleBot")
            .runtime(Runtime.JAVA_21)
            .memorySize(256)
            .timeout(hockeyScheduleBotTimeout)
            .code(new AssetCode(MAVEN_ASSET_CODE_PATH))
            .handler("lambda.ScheduleBotRequestHandler::handleRequest")
            .architecture(Architecture.ARM_64)
            .environment(Map.of(
                "DISCORDBOTTOKEN", discordBotToken,
                "GOATS_WEBHOOK_URL", goatsWebhookUrl,
                "GOATS_CHANNEL_ID", goatsChannelId
            ))
            .build();

        // Grant scoped read/write permissions to the DynamoDB table
        dedupeTable.grantReadWriteData(hockeyScheduleBotFunction);

        final var scheduleRole = Role.Builder.create(this, "ScheduleRole")
            .managedPolicies(List.of(ManagedPolicy.fromManagedPolicyArn(this, "scheduleManagedPolicy", "arn:aws:iam::aws:policy/service-role/AWSLambdaRole")))
            .assumedBy(ServicePrincipal.Builder.create("scheduler.amazonaws.com").build())
            .build();

        CfnSchedule.Builder.create(this, "HockeyBotSchedule")
            .scheduleExpression("cron(0 20 ? * * *)")
            .scheduleExpressionTimezone("America/Vancouver")
            .flexibleTimeWindow(
                CfnSchedule.FlexibleTimeWindowProperty.builder().mode("OFF").build()
            ).target(
                CfnSchedule.TargetProperty.builder()
                    .arn(hockeyScheduleBotFunction.getFunctionArn())
                    .roleArn(scheduleRole.getRoleArn())
                    .build()
            ).build();
    }

    private String getRequiredEnv(String name) {
        final var value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new RuntimeException(name + " environment variable must be set");
        }
        return value;
    }
}
