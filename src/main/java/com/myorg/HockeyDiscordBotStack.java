package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.scheduler.CfnSchedule;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;

import java.util.List;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class HockeyDiscordBotStack extends Stack {
    public static final String MAVEN_ASSET_CODE_PATH = "target/hockey-discord-bot-0.1.jar";

    public static String RELAY_QUEUE_ARN;

    public HockeyDiscordBotStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public HockeyDiscordBotStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        final var hockeyScheduleBotTimeout = Duration.seconds(60);
        final var hockeyScheduleBotFunction = Function.Builder.create(this, "HockeyScheduleBot")
            .functionName("HockeyScheduleBot")
            .runtime(Runtime.JAVA_21)
            .memorySize(256)
            .timeout(hockeyScheduleBotTimeout)
            .code(new AssetCode(MAVEN_ASSET_CODE_PATH))
            .handler("lambda.ScheduleBotRequestHandler::handleRequest")
            .architecture(Architecture.ARM_64)
            .build();

        final var relayFunction =  Function.Builder.create(this, "Relay")
            .functionName("Relay")
            .runtime(Runtime.NODEJS_LATEST)
            .memorySize(256)
            .timeout(hockeyScheduleBotTimeout)
            .code(Code.fromAsset("lambda"))
            .handler("index.handler")
            .architecture(Architecture.ARM_64)
            .build();

        final var relayQueue = Queue.Builder.create(this, "RelayQueue")
            .visibilityTimeout(hockeyScheduleBotTimeout)
            .queueName("RelayQueue")
            .build();

        final var sqsEventSourceMappingPermissions = new PolicyStatement();
        sqsEventSourceMappingPermissions.addActions("sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes");
        sqsEventSourceMappingPermissions.addResources("*");
        relayFunction.addToRolePolicy(sqsEventSourceMappingPermissions);


        final var relayEsm = EventSourceMapping.Builder.create(this, "RelayEsm")
            .batchSize(1)
            .enabled(true)
            .eventSourceArn(relayQueue.getQueueArn())
            .target(relayFunction)
            .build();

        final var dedupeTable = Table.Builder.create(this, "DedupeTable")
            .tableName("DedupeTable")
            .partitionKey(Attribute.builder().name("team").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("game-id").type(AttributeType.STRING).build())
            .timeToLiveAttribute("ttl")
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build();

        final var ddbFullAccessPermissions = new PolicyStatement();
        ddbFullAccessPermissions.addActions("dynamodb:*", "sqs:*");
        ddbFullAccessPermissions.addResources("*");
        hockeyScheduleBotFunction.addToRolePolicy(ddbFullAccessPermissions);

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
}
