package com.myorg;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
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

        // Every completed game, for analytics. Kept if the stack is deleted, since rebuilding it
        // means re-scraping years of league history.
        final var gameArchiveTable = Table.Builder.create(this, "GameArchive")
            .tableName("GameArchive")
            .partitionKey(Attribute.builder().name("team").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("game-id").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // Insights the bot has posted, for cooldowns. Entries expire after a year.
        final var insightLogTable = Table.Builder.create(this, "InsightLog")
            .tableName("InsightLog")
            .partitionKey(Attribute.builder().name("team").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("posted-at").type(AttributeType.STRING).build())
            .timeToLiveAttribute("ttl")
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build();

        // Who's in for each game: the check-in post, plus one item per answer.
        final var checkInsTable = Table.Builder.create(this, "CheckIns")
            .tableName("CheckIns")
            .partitionKey(Attribute.builder().name("game").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("member").type(AttributeType.STRING).build())
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
        gameArchiveTable.grantReadWriteData(hockeyScheduleBotFunction);
        insightLogTable.grantReadWriteData(hockeyScheduleBotFunction);
        checkInsTable.grantReadWriteData(hockeyScheduleBotFunction);

        // Discord's interactions endpoint for the check-in buttons. Discord gives it three
        // seconds to answer, so it gets more memory (and with it more CPU) than the scheduled
        // run, and SnapStart so it doesn't pay for JVM startup after sitting idle. This CDK
        // version only allows SnapStart on x86.
        final var interactionsFunction = Function.Builder.create(this, "DiscordInteractions")
            .functionName("DiscordInteractions")
            .runtime(Runtime.JAVA_21)
            .memorySize(1024)
            .timeout(Duration.seconds(10))
            .code(new AssetCode(MAVEN_ASSET_CODE_PATH))
            .handler("lambda.InteractionHandler::handleRequest")
            .architecture(Architecture.X86_64)
            .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
            .build();
        checkInsTable.grantReadWriteData(interactionsFunction);

        // SnapStart only applies to published versions, so the URL points at an alias.
        final var interactionsAlias = Alias.Builder.create(this, "DiscordInteractionsLive")
            .aliasName("live")
            .version(interactionsFunction.getCurrentVersion())
            .build();
        // Open to anyone; the handler refuses requests that Discord didn't sign.
        final var interactionsUrl = interactionsAlias.addFunctionUrl(FunctionUrlOptions.builder()
            .authType(FunctionUrlAuthType.NONE)
            .build());
        // Function URLs also need lambda:InvokeFunction. InvokedViaFunctionUrl limits it to calls
        // through the URL, so nobody can invoke the function directly. This CDK version has no
        // typed field for it yet.
        final var invokeViaUrl = CfnPermission.Builder.create(this, "DiscordInteractionsInvokeViaUrl")
            .action("lambda:InvokeFunction")
            .functionName(interactionsAlias.getFunctionArn())
            .principal("*")
            .build();
        invokeViaUrl.addPropertyOverride("InvokedViaFunctionUrl", true);
        CfnOutput.Builder.create(this, "InteractionsEndpointUrl")
            .description("Paste into the Discord developer portal as the Interactions Endpoint URL")
            .value(interactionsUrl.getUrl())
            .build();

        final var scheduleRole = Role.Builder.create(this, "ScheduleRole")
            .managedPolicies(List.of(ManagedPolicy.fromManagedPolicyArn(this, "scheduleManagedPolicy", "arn:aws:iam::aws:policy/service-role/AWSLambdaRole")))
            .assumedBy(ServicePrincipal.Builder.create("scheduler.amazonaws.com").build())
            .build();

        CfnSchedule.Builder.create(this, "HockeyBotSchedule")
            .scheduleExpression("cron(0 9,20 ? * * *)")
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
