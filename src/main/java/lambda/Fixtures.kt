package lambda

object Fixtures {
    const val KRAKEN_HOCKEY_LEAGUE_WEBSITE = "https://krakenhockeyleague.com"
    const val DISCORD_API_BASE_URL = "https://discord.com/api/v10"

    const val MAX_POLL_DURATION_HOURS = 168
    const val MIN_POLL_DURATION_HOURS = 1
    const val GAME_DATE_TIME_PATTERN = "E, MMM d uuuu @ h:mm a"

    val ARENAS_TO_LOCATION = mapOf(
        "KVIC" to "https://maps.app.goo.gl/587sGU8sbtiGcVsp6",
        "KCI VMFH" to "https://maps.app.goo.gl/GTwM6Tx1aicFdjQ48",
        "KCI Star" to "https://maps.app.goo.gl/GTwM6Tx1aicFdjQ48",
        "KCI Smrt" to "https://maps.app.goo.gl/GTwM6Tx1aicFdjQ48",
        "LIC" to "https://maps.app.goo.gl/bmzZUokkZv41YT3M8",
        "OVA" to "https://maps.app.goo.gl/VPftVaLwq9a8oeZD6",
        "EVT COMM" to "https://maps.app.goo.gl/CBQBocbYnGbMUJEp7",
        "ANGEL" to "https://maps.app.goo.gl/CBQBocbYnGbMUJEp7",
        "SHOW" to "https://maps.app.goo.gl/WKzp4AtNHahcKdqu8",
    )

    const val DEDUPE_TABLE_NAME = "DedupeTable"
    const val GAME_ARCHIVE_TABLE_NAME = "GameArchive"
    const val INSIGHT_LOG_TABLE_NAME = "InsightLog"
    const val CHECK_INS_TABLE_NAME = "CheckIns"
    const val TEAM_ATTRIBUTE_NAME = "team"
    const val GAME_ID_ATTRIBUTE_NAME = "game-id"

    const val RECAP_RANGE_SUFFIX = ":recap"
    const val RECAP_MAX_AGE_DAYS = 3L
    const val RECAP_TOP_SCORERS = 3

    const val HTTP_USER_AGENT = "hockey-schedule-bot/1.0 (+https://github.com/soapdogg/hockey-schedule-bot)"

    val GOATS_CHANNEL: String = System.getenv("GOATS_WEBHOOK_URL") ?: ""
    val GOATS_CHANNEL_ID: String = System.getenv("GOATS_CHANNEL_ID") ?: ""

    val BOT_TOKEN: String = System.getenv("DISCORDBOTTOKEN") ?: ""

    /**
     * Check-in posts with buttons, sent by the bot, replace the announcement and poll. Needs the
     * bot in the team's server and the interactions endpoint set up (see README).
     */
    const val CHECK_INS_ENABLED = false

    /**
     * The Discord application's public key, for checking that button presses really come from
     * Discord. Public by design: only Discord holds the matching private key.
     */
    const val DISCORD_PUBLIC_KEY = "bda8cc01bbba9584a6d98df5b9b7d1448e1d0068ad0db7e5397315be552d4880"

    val ALERT_WEBHOOK_URL: String = System.getenv("ALERT_WEBHOOK_URL") ?: GOATS_CHANNEL
}