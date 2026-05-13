package lambda

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals

class GameDateParserTest {

    @Test
    fun `parses a date in the current year when day-of-week matches`() {
        // Oct 16, 2024 was a Wednesday
        val now = LocalDateTime.of(2024, 10, 1, 12, 0)
        val parsed = GameDateParser.parse(now, "Wed, Oct 16 @ 7:00 PM")
        assertEquals(LocalDateTime.of(2024, 10, 16, 19, 0), parsed)
    }

    @Test
    fun `picks next year when current year's day-of-week does not match`() {
        // Running in December 2024; game is "Sat, Jan 4". Jan 4, 2025 is a Saturday;
        // Jan 4, 2024 was a Thursday — so only 2025 parses.
        val now = LocalDateTime.of(2024, 12, 20, 8, 0)
        val parsed = GameDateParser.parse(now, "Sat, Jan 4 @ 8:30 PM")
        assertEquals(LocalDateTime.of(2025, 1, 4, 20, 30), parsed)
    }

    @Test
    fun `picks next year when running in November and game is in following January`() {
        // Nov 2025: Jan 3, 2026 is a Saturday; Jan 3, 2025 was a Friday.
        val now = LocalDateTime.of(2025, 11, 15, 9, 0)
        val parsed = GameDateParser.parse(now, "Sat, Jan 3 @ 6:00 PM")
        assertEquals(LocalDateTime.of(2026, 1, 3, 18, 0), parsed)
    }

    @Test
    fun `picks previous year when running in January and game is from late prior year`() {
        // Running early Jan 2025; "Sun, Dec 29" matches Dec 29, 2024.
        val now = LocalDateTime.of(2025, 1, 5, 9, 0)
        val parsed = GameDateParser.parse(now, "Sun, Dec 29 @ 5:00 PM")
        assertEquals(LocalDateTime.of(2024, 12, 29, 17, 0), parsed)
    }

    @Test
    fun `throws when no year in window produces a matching day-of-week`() {
        val now = LocalDateTime.of(2024, 10, 1, 12, 0)
        // Oct 16 is never a Sunday in 2023, 2024, or 2025.
        assertThrows<IllegalStateException> {
            GameDateParser.parse(now, "Sun, Oct 16 @ 7:00 PM")
        }
    }
}
