package lambda

object Fixtures {
    const val KRAKEN_HOCKEY_LEAGUE_WEBSITE = "https://krakenhockeyleague.com"

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


    const val GOATS_CHANNEL = "https://discord.com/api/webhooks/1246515419158675587/hivn6lT_vPJgWkWh9i-TgOxtXlf-tu0KwH08L9yLq2ogKY-YkgZRrmhewF_tYxYJQbic"
    const val GOATS_CHANNEL_ID = "1246515337348907039"

    const val TEST_CHANNEL = "https://discord.com/api/webhooks/1353421694160601138/_Oci0XL07NesJL-NzeY5pWd6WM4TBYIsfQZC1mpMPBpES-YKXUsBGvkHtz_vga5iV9dN"
    const val TEST_CHANNEL_ID = "1353421250491580536"

    val BOT_TOKEN = System.getenv("DISCORDBOTTOKEN")
}