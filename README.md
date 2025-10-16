# Hockey Schedule Bot

[![CI](https://github.com/soapdogg/hockey-schedule-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/soapdogg/hockey-schedule-bot/actions/workflows/ci.yml)
![Coverage](.github/badges/jacoco.svg)
![Branches](.github/badges/branches.svg)
[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org/)
[![Maven](https://img.shields.io/badge/Maven-3.x-red.svg)](https://maven.apache.org/)
[![AWS CDK](https://img.shields.io/badge/AWS_CDK-2.142.1-orange.svg)](https://aws.amazon.com/cdk/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## Overview

Hockey Schedule Bot is an automated Discord bot built with AWS CDK, Kotlin, and Java that monitors hockey team schedules and publishes game information to Discord channels. The bot scrapes game schedules from the Kraken Hockey League website and automatically posts upcoming game details including date, time, opponent, and location to designated Discord channels.

### Key Features

- **Automated Schedule Monitoring**: Runs on a scheduled basis (daily at 8 PM PST) via AWS EventBridge
- **Discord Integration**: Posts game announcements with formatted messages and interactive polls
- **Game Deduplication**: Uses DynamoDB to track previously published games and avoid duplicates
- **Interactive Polls**: Creates Discord polls asking team members about their availability for games
- **Arena Location Links**: Provides Google Maps links for all arena locations
- **AWS Lambda Deployment**: Serverless architecture using AWS Lambda for scalability and cost-efficiency

### Architecture

The application is deployed as an AWS Lambda function orchestrated by AWS CDK:

- **Lambda Function**: Kotlin-based handler that processes game schedules
- **DynamoDB Table**: Stores published game IDs to prevent duplicate announcements
- **EventBridge Schedule**: Triggers the Lambda function daily at 8 PM Pacific Time
- **Discord Webhooks**: Posts messages to Discord channels
- **Discord Bot API**: Creates interactive polls for game attendance

### Technologies

- **Languages**: Kotlin 2.0.0, Java 17
- **Build Tool**: Maven
- **Cloud Platform**: AWS (Lambda, DynamoDB, EventBridge)
- **Infrastructure as Code**: AWS CDK 2.142.1
- **Web Scraping**: Jsoup
- **HTTP Client**: OkHttp
- **Testing**: JUnit 5

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.x
- AWS CLI configured (for deployment)
- AWS CDK CLI installed
- Discord Bot Token (set as `DISCORDBOTTOKEN` environment variable)

### Building the Project

```bash
mvn clean package
```

### Running Tests

```bash
mvn test
```

### Deploying to AWS

1. Synthesize the CloudFormation template:
   ```bash
   cdk synth
   ```

2. Deploy to AWS:
   ```bash
   cdk deploy
   ```

### Configuration

The bot is configured to monitor specific teams in the `ScheduleBotRequestHandler.kt` file. To add or modify teams, update the `teams` list with the appropriate team details:

```kotlin
val teams = listOf(
    Team("TeamName", "teamId", "webhookUrl", "channelId"),
)
```

## Project Structure

```
src/
├── main/
│   └── java/
│       ├── com/myorg/          # CDK application and stack definitions
│       └── lambda/             # Lambda function and bot logic (Kotlin)
└── test/
    └── java/                   # Unit tests
```

## Useful Commands

- `mvn package` - Compile and run tests
- `mvn clean verify` - Clean build with coverage report
- `cdk ls` - List all stacks in the app
- `cdk synth` - Emit the synthesized CloudFormation template
- `cdk deploy` - Deploy this stack to your AWS account/region
- `cdk diff` - Compare deployed stack with current state
- `cdk docs` - Open CDK documentation

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
