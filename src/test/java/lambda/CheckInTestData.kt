package lambda

import java.time.Instant
import java.time.LocalDateTime

object CheckInTestData {
    val START: LocalDateTime = LocalDateTime.of(2026, 11, 16, 23, 0)
    const val KEY = "10159:2026-11-16:Fossils"

    val ROSTER = linkedMapOf("1" to "Ava", "2" to "Ben", "3" to "Cy", "4" to "Dee")

    fun scheduleGame(date: String = "Mon, Nov 16", time: String = "11:00 PM") = Game(
        number = "8",
        date = date,
        time = time,
        arena = "EVT COMM",
        ha = "HOME",
        opponent = "Fossils",
        opponentLink = "/team/1/home",
        hash = "h",
    )

    fun game(
        roster: Map<String, String> = ROSTER,
        remindedSlot: String? = null,
        reminderMessageId: String? = null,
        gameDayPosted: Boolean = false,
        directMessagesSent: Boolean = false,
        closed: Boolean = false,
    ) = CheckInGame(
        key = KEY,
        number = "8",
        opponent = "Fossils",
        start = START,
        arena = "EVT COMM",
        homeAway = "HOME",
        guildId = "900",
        channelId = "901",
        messageId = "902",
        roster = roster,
        remindedSlot = remindedSlot,
        reminderMessageId = reminderMessageId,
        gameDayPosted = gameDayPosted,
        directMessagesSent = directMessagesSent,
        closed = closed,
    )

    fun answer(userId: String, name: String, position: Position) =
        CheckInResponse(userId, name, position, Instant.parse("2026-11-10T16:00:00Z"))
}
