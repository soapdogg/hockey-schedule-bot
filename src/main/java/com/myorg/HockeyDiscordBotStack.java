package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.AssetCode;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class HockeyDiscordBotStack extends Stack {
    public static final String MAVEN_ASSET_CODE_PATH = "target/hockey-discord-bot-0.1.jar";

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
        // example resource
        // final Queue queue = Queue.Builder.create(this, "HockeyDiscordBotQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();
    }
}
