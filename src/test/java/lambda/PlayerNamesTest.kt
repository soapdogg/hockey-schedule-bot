package lambda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlayerNamesTest {

    @Test
    fun `title-cases all-caps names and leaves mixed case alone`() {
        assertEquals("Dzucka Lin", PlayerNames.display("DZUCKA LIN"))
        assertEquals("Mary-Jane O'Neil", PlayerNames.display("MARY-JANE O'NEIL"))
        assertEquals("Eric McDonald", PlayerNames.display("Eric McDonald"))
        assertEquals("Chris Flynn", PlayerNames.display("  Chris   Flynn "))
    }
}
