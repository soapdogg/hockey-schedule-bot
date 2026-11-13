package lambda

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The scheduled side of check-ins, run twice a day. A game gets a check-in post a week out.
 * People who haven't answered get reminded, more often as the game gets closer: mornings until
 * three days out, then mornings and evenings. On game day the morning post lists who's playing
 * and the bot sends a direct message to anyone still missing. Once the game starts the post is
 * closed. The buttons themselves are handled by [InteractionHandler].
 */
class CheckInService(
    private val discord: DiscordApi,
    private val store: CheckInStore,
    private val channelId: String,
    private val pickInsight: (Occasion.PreGame) -> Insight? = { null },
    private val insightPosted: (Insight, Occasion.PreGame) -> Unit = { _, _ -> },
    /** Keeps test posts apart from real ones. */
    private val keyPrefix: String = "",
) {
    fun run(team: Team, games: List<Game>, now: LocalDateTime) {
        val guildId by lazy { discord.guildOf(channelId) }
        val roster by lazy { roster(guildId) }
        val failures = mutableListOf<String>()
        for (game in games) {
            val start = runCatching { GameDateParser.parse(now, "${game.date} @ ${game.time}") }.getOrNull() ?: continue
            if (start.isBefore(now.minus(CLOSE_WITHIN)) || Duration.between(now, start) > OPEN_BEFORE) continue
            try {
                val key = CheckIns.key(team.krakenWebsiteId, start, game.opponent, keyPrefix)
                val saved = store.load(key)
                when {
                    !start.isAfter(now) -> if (saved != null && !saved.game.closed) close(saved)
                    saved == null -> open(key, game, start, guildId, roster ?: emptyMap(), now)
                    else -> update(saved, game, start, roster, now)
                }
            } catch (e: Exception) {
                println("ERROR: Check-in for game #${game.number} vs ${game.opponent} failed: ${e.message}")
                e.printStackTrace()
                failures += "game #${game.number}: ${e.message?.take(200)}"
            }
        }
        if (failures.isNotEmpty()) throw IllegalStateException("Check-ins failed for ${failures.joinToString("; ")}")
    }

    private fun open(key: String, game: Game, start: LocalDateTime, guildId: String, roster: Map<String, String>, now: LocalDateTime) {
        val draft = CheckInGame(
            key = key,
            number = game.number,
            opponent = game.opponent,
            start = start,
            arena = game.arena,
            homeAway = game.ha,
            guildId = guildId,
            channelId = channelId,
            messageId = "",
            roster = roster,
            // The post itself is this run's reminder.
            remindedSlot = slot(now),
        )
        val messageId = discord.createMessage(channelId, CheckInFormatter.post(CheckInState(draft, emptyList())))
        store.saveGame(draft.copy(messageId = messageId))
        println("Opened check-in for game #${game.number} vs ${game.opponent}")
    }

    private fun update(saved: CheckInState, game: Game, start: LocalDateTime, roster: Map<String, String>?, now: LocalDateTime) {
        // Pick up schedule changes and new server members, and redraw the post in case two
        // answers raced and one of them redrew it with stale data.
        var current = saved.game.copy(
            number = game.number,
            start = start,
            arena = game.arena,
            homeAway = game.ha,
            roster = roster ?: saved.game.roster,
        )
        var state = saved.copy(game = current)
        try {
            discord.editMessage(current.channelId, current.messageId, CheckInFormatter.post(state))
        } catch (e: DiscordException) {
            if (e.status != 404) throw e
            // Someone deleted the post, so put it back.
            current = current.copy(messageId = discord.createMessage(current.channelId, CheckInFormatter.post(state)))
            state = state.copy(game = current)
        }

        val slot = slot(now)
        val daysOut = ChronoUnit.DAYS.between(now.toLocalDate(), start.toLocalDate())
        if (daysOut == 0L && !current.gameDayPosted) {
            current.reminderMessageId?.let { discord.deleteMessage(current.channelId, it) }
            val occasion = Occasion.PreGame(game.opponent, game.arena, start, game.ha)
            val insight = pickInsight(occasion)
            discord.createMessage(current.channelId, CheckInFormatter.gameDay(state, insight?.text))
            insight?.let { insightPosted(it, occasion) }
            current = current.copy(gameDayPosted = true, remindedSlot = slot, reminderMessageId = null)
            if (!current.directMessagesSent) {
                for ((userId, name) in state.noReply) {
                    try {
                        discord.sendDirectMessage(userId, CheckInFormatter.directMessage(current))
                    } catch (e: Exception) {
                        // People can turn off direct messages from server members.
                        println("WARNING: Could not send a direct message to $name: ${e.message}")
                    }
                }
                current = current.copy(directMessagesSent = true)
            }
        } else if (reminderDue(daysOut, now) && current.remindedSlot != slot && state.noReply.isNotEmpty()) {
            current.reminderMessageId?.let { discord.deleteMessage(current.channelId, it) }
            val reminderId = discord.createMessage(current.channelId, CheckInFormatter.reminder(state, now.toLocalDate()))
            current = current.copy(remindedSlot = slot, reminderMessageId = reminderId)
        }
        if (current != saved.game) store.saveGame(current)
    }

    private fun close(saved: CheckInState) {
        val game = saved.game
        discord.editMessage(game.channelId, game.messageId, CheckInFormatter.closedPost(saved))
        game.reminderMessageId?.let { discord.deleteMessage(game.channelId, it) }
        store.saveGame(game.copy(closed = true, reminderMessageId = null))
        println("Closed check-in for game #${game.number} vs ${game.opponent}")
    }

    private fun roster(guildId: String): Map<String, String>? = try {
        discord.members(guildId)
    } catch (e: DiscordException) {
        // Most likely the Server Members intent is off. Without a roster there's no
        // "No reply" list and no reminders, but check-ins still work.
        println("WARNING: Could not list server members: ${e.message}")
        null
    }

    companion object {
        val OPEN_BEFORE: Duration = Duration.ofDays(7)

        /** How long after the start a still-open post gets closed. */
        val CLOSE_WITHIN: Duration = Duration.ofDays(3)

        /** Morning reminders until three days out, then mornings and evenings. */
        fun reminderDue(daysOut: Long, now: LocalDateTime): Boolean = when (daysOut) {
            in 3L..6L -> now.hour < 12
            in 0L..2L -> true
            else -> false
        }

        /** Each run's slot: the date, and morning or evening. */
        fun slot(now: LocalDateTime): String = "${now.toLocalDate()}-${if (now.hour < 12) "am" else "pm"}"
    }
}
