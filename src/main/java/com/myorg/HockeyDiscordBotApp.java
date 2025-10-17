package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;

public class HockeyDiscordBotApp {
    public static void main(final String[] args) {
        App app = new App();

        new HockeyDiscordBotStack(app, "HockeyDiscordBotStack", StackProps.builder()
                // Use environment variables for account and region when available (e.g., in CI/CD).
                // Falls back to environment-agnostic mode if not set.
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                // For more information, see https://docs.aws.amazon.com/cdk/latest/guide/environments.html
                .build());

        app.synth();
    }
}

