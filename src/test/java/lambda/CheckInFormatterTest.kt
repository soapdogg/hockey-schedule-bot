package lambda

import lambda.CheckInTestData.answer
import lambda.CheckInTestData.game
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckInFormatterTest {
    private val state = CheckInState(
        game(),
        listOf(answer("2", "Ben", Position.FORWARD), answer("1", "Ava", Position.FORWARD), answer("3", "Cy", Position.OUT)),
    )

    @Test
    fun `the post lists answers by position, then who hasn't replied`() {
        val content = CheckInFormatter.post(state)["content"] as String
        assertTrue(content.startsWith("🏒 **Game #8 vs Fossils** · Mon, Nov 16 @ 11:00 PM · EVT COMM · home"), content)
        assertTrue("**Forward (2):** Ava, Ben" in content, content)
        assertTrue("**Defense (0):** —" in content, content)
        assertTrue("**Goalie (0):** —" in content, content)
        assertTrue("**Out (1):** Cy" in content, content)
        assertTrue("**No reply (1):** Dee" in content, content)
        assertTrue("<t:${game().startEpochSecond}:R>" in content, content)
    }

    @Test
    fun `the post has one button per answer that names the game`() {
        @Suppress("UNCHECKED_CAST")
        val row = (CheckInFormatter.post(state)["components"] as List<Map<String, Any>>).single()
        @Suppress("UNCHECKED_CAST")
        val buttons = row["components"] as List<Map<String, Any>>
        assertEquals(listOf("Forward", "Defense", "Flex", "Goalie", "Out"), buttons.map { it["label"] })
        assertEquals(CheckInTestData.KEY to Position.GOALIE, CheckIns.parseButtonId(buttons[3]["custom_id"] as String))
        assertEquals(mapOf("parse" to emptyList<String>()), CheckInFormatter.post(state)["allowed_mentions"])
    }

    @Test
    fun `a closed post has no buttons and no reply list`() {
        val closed = CheckInFormatter.closedPost(state)
        assertEquals(emptyList<Any>(), closed["components"])
        val content = closed["content"] as String
        assertTrue("Check-ins are closed." in content, content)
        assertFalse("No reply" in content, content)
    }

    @Test
    fun `reminders mention only the people who haven't answered and get more urgent`() {
        val reminder = CheckInFormatter.reminder(state, LocalDate.of(2026, 11, 12))
        val content = reminder["content"] as String
        assertTrue(content.startsWith("⏰ **Game #8 vs Fossils** is on Monday at 11:00 PM."), content)
        assertTrue("Still waiting on <@4>:" in content, content)
        assertTrue("So far: 2 skaters and no goalie." in content, content)
        assertTrue("⚠️ No goalie yet and only 2 skaters." in content, content)
        assertEquals(mapOf("users" to listOf("4")), reminder["allowed_mentions"])
        assertEquals("902", (reminder["message_reference"] as Map<*, *>)["message_id"])

        assertTrue((CheckInFormatter.reminder(state, LocalDate.of(2026, 11, 15))["content"] as String).startsWith("⏰ **Game #8 vs Fossils** is tomorrow"))
        assertTrue((CheckInFormatter.reminder(state, LocalDate.of(2026, 11, 16))["content"] as String).startsWith("🚨 Last call:"))
    }

    @Test
    fun `the game-day post counts who's playing and ends with the insight`() {
        val post = CheckInFormatter.gameDay(state.with(answer("4", "Dee", Position.GOALIE)), "We're 5-1-0 at EVT COMM.")
        val content = post["content"] as String
        assertTrue(content.startsWith("🏒 **Game day!** **Game #8 vs Fossils** tonight at 11:00 PM · [EVT COMM](<"), content)
        assertTrue("**Playing (3):** 2 F · 1 G" in content, content)
        assertTrue("⚠️ Only 2 skaters." in content, content)
        assertTrue("Full lineup: https://discord.com/channels/900/901/902" in content, content)
        assertTrue(content.endsWith("\n\n📊 We're 5-1-0 at EVT COMM."), content)
        assertEquals(mapOf("users" to emptyList<String>()), post["allowed_mentions"])

        val missing = CheckInFormatter.gameDay(state, null)["content"] as String
        assertTrue("Still no answer from <@4>: check in here: https://discord.com/channels/900/901/902" in missing, missing)
        assertFalse("📊" in missing, missing)
    }

    @Test
    fun `no warning when there's a goalie and enough skaters`() {
        val full = CheckInState(
            game(roster = emptyMap()),
            (1..10).map { answer("$it", "P$it", Position.FORWARD) } + answer("11", "G", Position.GOALIE),
        )
        assertNull(CheckInFormatter.warning(full))
        assertEquals("10 skaters and 1 goalie", CheckInFormatter.status(full))
    }

    @Test
    fun `button IDs round-trip and anything else is ignored`() {
        val id = CheckIns.buttonId("test:10159:2026-11-16:Some|Team", Position.FLEX)
        assertEquals("test:10159:2026-11-16:Some|Team" to Position.FLEX, CheckIns.parseButtonId(id))
        assertNull(CheckIns.parseButtonId("other|x|FLEX"))
        assertNull(CheckIns.parseButtonId("ci|x|MAYBE"))
        assertNull(CheckIns.parseButtonId("ci||OUT"))
        assertEquals("10159:2026-11-16:Fossils", CheckIns.key("10159", CheckInTestData.START, "Fossils"))
    }

    @Test
    fun `a new answer replaces the person's earlier one`() {
        val changed = state.with(answer("3", "Cy", Position.DEFENSE))
        assertEquals(listOf("Cy"), changed.names(Position.DEFENSE))
        assertEquals(emptyList(), changed.names(Position.OUT))
    }
}
