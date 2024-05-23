package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class HockeyDiscordBotStack extends Stack {
    public HockeyDiscordBotStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public HockeyDiscordBotStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "HockeyDiscordBotQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();
    }
}
