package lambda

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import lambda.CheckInTestData.KEY
import lambda.CheckInTestData.ROSTER
import lambda.CheckInTestData.answer
import lambda.CheckInTestData.game
import lambda.CheckInTestData.scheduleGame
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInServiceTest {
    private val team = Team("Reindeer", "10159", "", "901")
    private val discord = mockk<DiscordApi>(relaxed = true)
    private val store = mockk<CheckInStore>(relaxed = true)
    private val saved = mutableListOf<CheckInGame>()
    private val posted = mutableListOf<Map<String, Any>>()
    private val recorded = mutableListOf<Insight>()
    private val insight = Insight("venue-EVT", "venue", emptyList(), "We're 5-1-0 at EVT COMM.", 3.0)

    private val service = CheckInService(
        discord, store, "901",
        pickInsight = { insight },
        insightPosted = { i, _ -> recorded += i },
    )

    init {
        every { discord.guildOf("901") } returns "900"
        every { discord.members("900") } returns ROSTER
        every { discord.createMessage("901", capture(posted)) } returnsMany listOf("m1", "m2", "m3")
        every { store.saveGame(capture(saved)) } just runs
    }

    private fun run(now: LocalDateTime, state: CheckInState?) {
        every { store.load(KEY) } returns state
        service.run(team, listOf(scheduleGame()), now)
    }

    @Test
    fun `opens a post a week out, and not before`() {
        run(LocalDateTime.of(2026, 11, 9, 19, 0), null)
        verify(exactly = 0) { discord.createMessage(any(), any()) }

        run(LocalDateTime.of(2026, 11, 10, 8, 0), null)
        val content = posted.single()["content"] as String
        assertTrue("**No reply (4):** Ava, Ben, Cy, Dee" in content, content)
        val game = saved.single()
        assertEquals("m1", game.messageId)
        assertEquals("900", game.guildId)
        assertEquals(ROSTER, game.roster)
        assertEquals("2026-11-10-am", game.remindedSlot)
    }

    @Test
    fun `still opens when the server members can't be listed`() {
        every { discord.members("900") } throws DiscordException(403, "Missing Access")
        run(LocalDateTime.of(2026, 11, 10, 8, 0), null)
        assertEquals(emptyMap(), saved.single().roster)
    }

    @Test
    fun `reminds people who haven't answered on mornings until three days out`() {
        val state = CheckInState(game(reminderMessageId = "old"), listOf(answer("1", "Ava", Position.FORWARD)))

        run(LocalDateTime.of(2026, 11, 12, 19, 0), state)
        verify(exactly = 0) { discord.createMessage(any(), any()) }
        verify { discord.editMessage("901", "902", any()) }

        run(LocalDateTime.of(2026, 11, 12, 8, 0), state)
        verify { discord.deleteMessage("901", "old") }
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("2", "3", "4"), (posted.single()["allowed_mentions"] as Map<String, List<String>>)["users"])
        assertEquals("2026-11-12-am", saved.last().remindedSlot)
        assertEquals("m1", saved.last().reminderMessageId)
    }

    @Test
    fun `reminds mornings and evenings in the last two days, once per run`() {
        val state = CheckInState(game(), emptyList())
        run(LocalDateTime.of(2026, 11, 15, 19, 0), state)
        assertEquals(1, posted.size)

        run(LocalDateTime.of(2026, 11, 15, 19, 0), state.copy(game = saved.last()))
        assertEquals(1, posted.size, "the same run doesn't remind twice")
    }

    @Test
    fun `no reminder once everyone has answered`() {
        val everyone = ROSTER.map { (id, name) -> answer(id, name, Position.DEFENSE) }
        run(LocalDateTime.of(2026, 11, 15, 8, 0), CheckInState(game(), everyone))
        assertTrue(posted.isEmpty())
    }

    @Test
    fun `game day gets the lineup post with an insight, and a direct message for anyone missing`() {
        val state = CheckInState(game(reminderMessageId = "old"), listOf(answer("1", "Ava", Position.GOALIE)))
        run(LocalDateTime.of(2026, 11, 16, 8, 0), state)

        val content = posted.single()["content"] as String
        assertTrue(content.startsWith("🏒 **Game day!**"), content)
        assertTrue(content.endsWith("📊 We're 5-1-0 at EVT COMM."), content)
        assertEquals(listOf(insight), recorded)
        verify { discord.deleteMessage("901", "old") }
        verify(exactly = 3) { discord.sendDirectMessage(any(), any()) }
        verify(exactly = 0) { discord.sendDirectMessage("1", any()) }
        val after = saved.last()
        assertTrue(after.gameDayPosted)
        assertTrue(after.directMessagesSent)
        assertNull(after.reminderMessageId)

        // The evening run is a last call, without more direct messages.
        posted.clear()
        run(LocalDateTime.of(2026, 11, 16, 19, 0), state.copy(game = after))
        assertTrue((posted.single()["content"] as String).startsWith("🚨 Last call:"))
        verify(exactly = 3) { discord.sendDirectMessage(any(), any()) }
    }

    @Test
    fun `a failed direct message doesn't stop the run`() {
        every { discord.sendDirectMessage("2", any()) } throws DiscordException(403, "Cannot send messages to this user")
        run(LocalDateTime.of(2026, 11, 16, 8, 0), CheckInState(game(), emptyList()))
        verify(exactly = 4) { discord.sendDirectMessage(any(), any()) }
        assertTrue(saved.last().directMessagesSent)
    }

    @Test
    fun `closes the post once the game has started`() {
        val state = CheckInState(game(reminderMessageId = "old"), listOf(answer("1", "Ava", Position.FORWARD)))
        val body = slot<Map<String, Any>>()
        every { discord.editMessage("901", "902", capture(body)) } just runs
        run(LocalDateTime.of(2026, 11, 17, 8, 0), state)

        assertEquals(emptyList<Any>(), body.captured["components"])
        verify { discord.deleteMessage("901", "old") }
        assertTrue(saved.single().closed)
        assertTrue(posted.isEmpty())

        saved.clear()
        run(LocalDateTime.of(2026, 11, 17, 19, 0), state.copy(game = state.game.copy(closed = true)))
        assertTrue(saved.isEmpty(), "already closed")
    }

    @Test
    fun `puts the post back if someone deleted it`() {
        every { discord.editMessage("901", "902", any()) } throws DiscordException(404, "Unknown Message")
        run(LocalDateTime.of(2026, 11, 12, 19, 0), CheckInState(game(), emptyList()))
        assertEquals("m1", saved.single().messageId)
    }

    @Test
    fun `follows a time change without losing answers`() {
        val state = CheckInState(game(), listOf(answer("1", "Ava", Position.FORWARD)))
        every { store.load(KEY) } returns state
        service.run(team, listOf(scheduleGame(time = "9:05 PM")), LocalDateTime.of(2026, 11, 12, 19, 0))
        assertEquals(LocalDateTime.of(2026, 11, 16, 21, 5), saved.single().start)
    }

    @Test
    fun `a failure on one game is reported after the others are done`() {
        every { store.load(KEY) } throws IllegalStateException("boom")
        val error = runCatching { service.run(team, listOf(scheduleGame()), LocalDateTime.of(2026, 11, 12, 8, 0)) }.exceptionOrNull()
        assertTrue(error?.message?.contains("boom") == true)
        assertFalse(saved.any())
    }
}
