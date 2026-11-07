package lambda

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecapWindowTest {

    // Nov 2, 2026 is a Monday.
    private val game = Game(
        number = "6",
        date = "Mon, Nov 2",
        time = "9:00 PM",
        arena = "KVIC",
        ha = "HOME",
        opponent = "Barrel Aged Brewzers",
        opponentLink = "",
        hash = "hash",
        gameId = "57309",
        result = "L",
    )

    @Test
    fun `open the morning after the game`() {
        assertTrue(RecapWindow.isOpen(LocalDateTime.of(2026, 11, 3, 9, 0), game))
    }

    @Test
    fun `open until the game is three days old`() {
        assertTrue(RecapWindow.isOpen(LocalDateTime.of(2026, 11, 5, 20, 0), game))
    }

    @Test
    fun `closed for older games so a first deploy does not recap the whole season`() {
        assertFalse(RecapWindow.isOpen(LocalDateTime.of(2026, 11, 7, 9, 0), game))
    }

    @Test
    fun `closed before the game starts`() {
        assertFalse(RecapWindow.isOpen(LocalDateTime.of(2026, 11, 2, 20, 0), game))
    }
}
