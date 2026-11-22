package lambda

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** The answers on a check-in post, in button order. */
enum class Position(val label: String, val short: String) {
    FORWARD("Forward", "F"),
    DEFENSE("Defense", "D"),
    FLEX("Flex", "Flex"),
    GOALIE("Goalie", "G"),
    OUT("Out", "Out");

    val playing: Boolean get() = this != OUT

    companion object {
        fun of(name: String?): Position? = entries.firstOrNull { it.name == name }
    }
}

data class CheckInResponse(
    val userId: String,
    val name: String,
    val position: Position,
    val at: Instant,
)

/** A game's check-in post, and what the bot has already done for it. */
data class CheckInGame(
    val key: String,
    val number: String,
    val opponent: String,
    val start: LocalDateTime,
    val arena: String,
    val homeAway: String,
    val guildId: String,
    val channelId: String,
    val messageId: String,
    /** Everyone expected to answer: Discord user ID to display name. */
    val roster: Map<String, String>,
    /** The last run that sent a reminder, so a run never reminds twice. */
    val remindedSlot: String? = null,
    /** The latest reminder, deleted when the next one goes out. */
    val reminderMessageId: String? = null,
    val gameDayPosted: Boolean = false,
    val directMessagesSent: Boolean = false,
    val closed: Boolean = false,
    /** Extra accounts of people on the roster: account ID to the roster ID it belongs to. */
    val aliases: Map<String, String> = emptyMap(),
) {
    val link: String get() = "https://discord.com/channels/$guildId/$channelId/$messageId"
    val startEpochSecond: Long get() = start.atZone(CheckIns.ZONE).toEpochSecond()

    /** The roster ID of whoever uses [accountId]. */
    fun personOf(accountId: String): String = aliases[accountId] ?: accountId

    /** Every account a person on the roster uses, so a mention reaches them on any of them. */
    fun accountsOf(personId: String): List<String> = listOf(personId) + aliases.filterValues { it == personId }.keys.sorted()
}

/** Everyone expected to answer, with extra accounts folded into the person who uses them. */
data class Roster(val people: Map<String, String>, val aliases: Map<String, String> = emptyMap()) {
    companion object {
        /** [linked] maps an extra account to the person's main one; links to people who left are ignored. */
        fun of(members: Map<String, String>, linked: Map<String, String>): Roster {
            val aliases = linked.filter { (account, person) -> account != person && account in members && person in members }
            return Roster(members - aliases.keys, aliases)
        }
    }
}

data class CheckInState(val game: CheckInGame, val responses: List<CheckInResponse>) {
    fun names(position: Position): List<String> =
        responses.filter { it.position == position }.map { it.name }.sortedBy { it.lowercase() }

    val skaters: Int get() = responses.count { it.position.playing && it.position != Position.GOALIE }
    val goalies: Int get() = responses.count { it.position == Position.GOALIE }

    /** Roster members who haven't answered, by user ID, sorted by name. */
    val noReply: List<Pair<String, String>>
        get() {
            val answered = responses.map { game.personOf(it.userId) }.toSet()
            return game.roster.filterKeys { it !in answered }.toList().sortedBy { it.second.lowercase() }
        }

    /** Mentions for everyone who hasn't answered, on all of their accounts. */
    val noReplyMentions: List<String>
        get() = noReply.map { (id, _) -> game.accountsOf(id).joinToString(" / ") { "<@$it>" } }

    val noReplyAccounts: List<String> get() = noReply.flatMap { game.accountsOf(it.first) }

    /** The state with [response] replacing any earlier answer from the same person. */
    fun with(response: CheckInResponse): CheckInState =
        copy(responses = responses.filterNot { it.userId == response.userId } + response)
}

object CheckIns {
    val ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

    /** Stable across schedule edits to the time or rink, so answers carry over. */
    fun key(teamId: String, start: LocalDateTime, opponent: String, prefix: String = ""): String =
        "$prefix$teamId:${start.toLocalDate()}:$opponent"

    private const val BUTTON_PREFIX = "ci|"

    fun buttonId(key: String, position: Position): String = "$BUTTON_PREFIX$key|${position.name}"

    /** The game key and answer from a button's custom ID, or null if it isn't a check-in button. */
    fun parseButtonId(customId: String): Pair<String, Position>? {
        if (!customId.startsWith(BUTTON_PREFIX)) return null
        val rest = customId.removePrefix(BUTTON_PREFIX)
        val key = rest.substringBeforeLast('|', "")
        val position = Position.of(rest.substringAfterLast('|')) ?: return null
        return if (key.isEmpty()) null else key to position
    }
}
