package lambda

object PlayerNames {
    private val WHITESPACE = Regex("\\s+")
    private val WORD_START = Regex("(^|[\\s'-])(\\p{L})")

    /**
     * The league site shows some names in all caps ("DZUCKA LIN"); render those as "Dzucka Lin".
     * Scoresheets also mark some players with "**" (meaning unexplained), which is dropped.
     */
    fun display(raw: String): String {
        val name = raw.replace("*", "").replace(WHITESPACE, " ").trim()
        if (name != name.uppercase()) return name
        return WORD_START.replace(name.lowercase()) { it.groupValues[1] + it.groupValues[2].uppercase() }
    }
}
