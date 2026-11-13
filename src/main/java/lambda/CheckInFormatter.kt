package lambda

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Discord message bodies for check-ins, as maps ready to serialize. */
object CheckInFormatter {
    /** Fewer skaters than this gets a warning in reminders and the game-day post. */
    const val MIN_SKATERS = 10

    private val WHEN = DateTimeFormatter.ofPattern("EEE, MMM d '@' h:mm a", Locale.ENGLISH)
    private val DAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val NO_MENTIONS = mapOf("parse" to emptyList<String>())

    fun post(state: CheckInState): Map<String, Any> = mapOf(
        "content" to postText(state, open = true),
        "components" to buttons(state.game.key),
        "allowed_mentions" to NO_MENTIONS,
    )

    fun closedPost(state: CheckInState): Map<String, Any> = mapOf(
        "content" to postText(state, open = false),
        "components" to emptyList<Any>(),
        "allowed_mentions" to NO_MENTIONS,
    )

    fun reminder(state: CheckInState, today: LocalDate): Map<String, Any> {
        val game = state.game
        val lead = when (ChronoUnit.DAYS.between(today, game.start.toLocalDate())) {
            0L -> "🚨 Last call: ${title(game)} is tonight at ${game.start.format(TIME)}."
            1L -> "⏰ ${title(game)} is tomorrow at ${game.start.format(TIME)}."
            else -> "⏰ ${title(game)} is on ${game.start.format(DAY)} at ${game.start.format(TIME)}."
        }
        val waiting = state.noReply.map { it.first }
        val content = listOfNotNull(
            "$lead Still waiting on ${waiting.joinToString(" ") { "<@$it>" }}: tap your position on the check-in post.",
            "So far: ${status(state)}.",
            warning(state),
        ).joinToString("\n")
        return mapOf(
            "content" to content,
            "allowed_mentions" to mapOf("users" to waiting),
            "message_reference" to mapOf("message_id" to game.messageId, "fail_if_not_exists" to false),
        )
    }

    fun gameDay(state: CheckInState, insight: String?): Map<String, Any> {
        val game = state.game
        val arena = Fixtures.ARENAS_TO_LOCATION[game.arena]?.let { "[${game.arena}](<$it>)" } ?: game.arena
        val playing = state.responses.filter { it.position.playing }
        val breakdown = Position.entries.filter { it.playing }
            .mapNotNull { p -> state.responses.count { it.position == p }.takeIf { it > 0 }?.let { "$it ${p.short}" } }
        val waiting = state.noReply.map { it.first }
        val content = listOfNotNull(
            "🏒 **Game day!** ${title(game)} tonight at ${game.start.format(TIME)} · $arena · ${game.homeAway.lowercase()}",
            "**Playing (${playing.size}):** ${breakdown.joinToString(" · ").ifEmpty { "nobody yet" }}",
            warning(state),
            if (waiting.isEmpty()) {
                "Full lineup: ${game.link}"
            } else {
                "Still no answer from ${waiting.joinToString(" ") { "<@$it>" }}: check in here: ${game.link}"
            },
            insight?.let { "\n" + RecapFormatter.insightLine(it) },
        ).joinToString("\n")
        return mapOf("content" to content, "allowed_mentions" to mapOf("users" to waiting))
    }

    fun directMessage(game: CheckInGame): Map<String, Any> = mapOf(
        "content" to "Hi! You haven't checked in for tonight's game: ${title(game)} at ${game.start.format(TIME)}, " +
            "${game.arena}. Tap your position here: ${game.link}",
    )

    fun status(state: CheckInState): String {
        val skaters = "${state.skaters} skater${if (state.skaters == 1) "" else "s"}"
        val goalies = when (state.goalies) {
            0 -> "no goalie"
            1 -> "1 goalie"
            else -> "${state.goalies} goalies"
        }
        return "$skaters and $goalies"
    }

    fun warning(state: CheckInState): String? {
        val problems = buildList {
            if (state.goalies == 0) add("no goalie yet")
            if (state.skaters < MIN_SKATERS) add("only ${state.skaters} skater${if (state.skaters == 1) "" else "s"}")
        }
        if (problems.isEmpty()) return null
        return "⚠️ " + problems.joinToString(" and ").replaceFirstChar { it.uppercase() } + "."
    }

    private fun title(game: CheckInGame) = "**Game #${game.number} vs ${game.opponent}**"

    private fun postText(state: CheckInState, open: Boolean): String = buildString {
        val game = state.game
        appendLine("🏒 ${title(game)} · ${game.start.format(WHEN)} · ${game.arena} · ${game.homeAway.lowercase()}")
        if (open) {
            appendLine("Tap your position to check in. You can change it until the game starts <t:${game.startEpochSecond}:R>.")
        } else {
            appendLine("Check-ins are closed.")
        }
        appendLine()
        for (position in Position.entries) {
            val names = state.names(position)
            if (position.playing || names.isNotEmpty()) {
                appendLine("**${position.label} (${names.size}):** ${names.joinToString(", ").ifEmpty { "—" }}")
            }
        }
        val noReply = state.noReply
        if (open && noReply.isNotEmpty()) {
            appendLine("**No reply (${noReply.size}):** ${noReply.joinToString(", ") { it.second }}")
        }
    }.trimEnd()

    private fun buttons(key: String): List<Any> = listOf(
        mapOf(
            "type" to 1,
            "components" to Position.entries.map { position ->
                mapOf(
                    "type" to 2,
                    // Primary (blurple) for playing, secondary (grey) for out.
                    "style" to if (position.playing) 1 else 2,
                    "label" to position.label,
                    "custom_id" to CheckIns.buttonId(key, position),
                )
            },
        )
    )
}
