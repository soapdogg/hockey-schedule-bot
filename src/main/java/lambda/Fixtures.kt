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
    const val TEAM_ATTRIBUTE_NAME = "team"
    const val GAME_ID_ATTRIBUTE_NAME = "game-id"

    val GOATS_CHANNEL: String = System.getenv("GOATS_WEBHOOK_URL") ?: ""
    val GOATS_CHANNEL_ID: String = System.getenv("GOATS_CHANNEL_ID") ?: ""

    val BOT_TOKEN: String = System.getenv("DISCORDBOTTOKEN") ?: ""
}