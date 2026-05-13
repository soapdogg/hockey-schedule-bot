package lambda

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object GameDateParser {
    private val formatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern(Fixtures.GAME_DATE_TIME_PATTERN)
        .withLocale(Locale.ENGLISH)

    fun parse(now: LocalDateTime, dateTimeWithoutYear: String): LocalDateTime {
        for (y in listOf(now.year, now.year + 1, now.year - 1)) {
            val candidate = dateTimeWithoutYear.replace(" @", " $y @")
            try {
                return LocalDateTime.parse(candidate, formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        throw IllegalStateException(
            "Could not parse game date '$dateTimeWithoutYear' in year window around ${now.year}"
        )
    }
}
