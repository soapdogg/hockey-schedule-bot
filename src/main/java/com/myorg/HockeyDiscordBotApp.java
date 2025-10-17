package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;

public class HockeyDiscordBotApp {
    public static void main(final String[] args) {
        App app = new App();

        // Build stack props with optional environment configuration
        StackProps.Builder stackPropsBuilder = StackProps.builder();
        
        // Use environment variables for account and region when available (e.g., in CI/CD).
        // Falls back to environment-agnostic mode if not set.
        String account = System.getenv("CDK_DEFAULT_ACCOUNT");
        String region = System.getenv("CDK_DEFAULT_REGION");
        
        if (account != null && !account.trim().isEmpty() && 
            region != null && !region.trim().isEmpty()) {
            stackPropsBuilder.env(Environment.builder()
                    .account(account)
                    .region(region)
                    .build());
        }
        // If env vars not set or empty, stack will be environment-agnostic
        
        new HockeyDiscordBotStack(app, "HockeyDiscordBotStack", stackPropsBuilder.build());

        app.synth();
    }
}

