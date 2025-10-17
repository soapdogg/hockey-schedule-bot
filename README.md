# Hockey Schedule Bot

[![CI](https://github.com/soapdogg/hockey-schedule-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/soapdogg/hockey-schedule-bot/actions/workflows/ci.yml)
![Coverage](.github/badges/jacoco.svg)
![Branches](.github/badges/branches.svg)
[![Java Version](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.0.0-purple.svg)](https://kotlinlang.org/)
[![Maven](https://img.shields.io/badge/Maven-3.x-red.svg)](https://maven.apache.org/)
[![AWS CDK](https://img.shields.io/badge/AWS_CDK-2.142.1-orange.svg)](https://aws.amazon.com/cdk/)

## Overview

Hockey Schedule Bot is an automated Discord bot built with AWS CDK, Kotlin, and Java that monitors hockey team schedules and publishes game information to Discord channels. The bot scrapes game schedules from the Kraken Hockey League website and automatically posts upcoming game details including date, time, opponent, and location to designated Discord channels.

> **Note:** Coverage badges are automatically generated and updated by the CI workflow after each push to the main branch.

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

#### Manual Deployment

1. Synthesize the CloudFormation template:
   ```bash
   cdk synth
   ```

2. Deploy to AWS:
   ```bash
   cdk deploy
   ```

#### Automatic Deployment (CI/CD)

The project includes a GitHub Actions workflow that automatically deploys to AWS when changes are merged to the `main` branch. The workflow:
1. Builds the project with Maven
2. Synthesizes the CDK stack
3. Deploys to your AWS account

**Setup Instructions:**

To enable automatic deployment, you need to configure AWS authentication using OpenID Connect (OIDC), which is more secure than using long-lived credentials:

1. **Create an IAM OIDC Identity Provider in AWS:**
   - Go to IAM Console → Identity Providers → Add Provider
   - Provider Type: OpenID Connect
   - Provider URL: `https://token.actions.githubusercontent.com`
   - Audience: `sts.amazonaws.com`

2. **Create an IAM Role for GitHub Actions:**
   ```bash
   # Create a trust policy file (trust-policy.json):
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Principal": {
           "Federated": "arn:aws:iam::<YOUR_AWS_ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
         },
         "Action": "sts:AssumeRoleWithWebIdentity",
         "Condition": {
           "StringEquals": {
             "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
           },
           "StringLike": {
             "token.actions.githubusercontent.com:sub": "repo:soapdogg/hockey-schedule-bot:ref:refs/heads/main"
           }
         }
       }
     ]
   }
   
   # Create the role:
   aws iam create-role --role-name GitHubActionsDeployRole --assume-role-policy-document file://trust-policy.json
   
   # Attach necessary policies (adjust permissions as needed):
   aws iam attach-role-policy --role-name GitHubActionsDeployRole --policy-arn arn:aws:iam::aws:policy/AdministratorAccess
   ```

3. **Add GitHub Secrets:**
   Go to your repository Settings → Secrets and variables → Actions → New repository secret:
   
   - `AWS_ROLE_ARN`: The ARN of the IAM role created above (e.g., `arn:aws:iam::123456789012:role/GitHubActionsDeployRole`)
   - `AWS_REGION`: Your AWS region (e.g., `us-west-2`)

4. **Optional: Restrict Permissions**
   For production use, consider replacing `AdministratorAccess` with a more restrictive policy that only grants permissions needed for CDK deployment:
   - CloudFormation full access
   - Lambda create/update/delete
   - DynamoDB create/update/delete
   - IAM role/policy management
   - EventBridge Scheduler access
   - S3 access for CDK assets

**Security Notes:**
- The OIDC approach does not require storing long-lived AWS credentials in GitHub
- The trust policy restricts access to only the `main` branch of this repository
- Tokens are short-lived and automatically rotated by GitHub

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
