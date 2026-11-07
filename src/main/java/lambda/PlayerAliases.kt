package lambda

/**
 * Different spellings the league has used for the same player, mapped to one name. Archived
 * games keep the league's spelling; the archive merges them when games are read. Each player's
 * name is the spelling the league uses now, or else the most common one.
 */
object PlayerAliases {
    private val CANONICAL: Map<String, String> = mapOf(
        "Andrew Girovyi" to "Andrew Gaiovyi",
        "Andy Muehlhausen" to "Andrew Muehlhausen",
        "Anur Talwar" to "Arun Talwar",
        "Dave Wilson" to "Dade Wilson",
        "Diviyan Matheentran" to "Diviyan Matheendran",
        "Drew Carson" to "Drew Larson",
        "Dzuka Lin" to "Dzucka Lin",
        "Dzuka Lynn" to "Dzucka Lin",
        "Eddy Parker" to "Eddie Parker",
        "Erik Steffin" to "Erik Steen",
        "Jazz Dallley" to "Jazz Palley",
        "Mike Scanner" to "Mike Scanlon",
        "Phantasia Jendro" to "Phantasia Jerdro",
        "Ray Haro" to "Raymond Haro",
        "Yossi" to "Yazz Henderson",
        "Yossi Henderson" to "Yazz Henderson",
    )

    fun canonical(name: String): String = CANONICAL[name] ?: name

    fun apply(sheet: Scoresheet): Scoresheet = sheet.copy(
        roster = sheet.roster.map { it.copy(name = canonical(it.name)) },
        goals = sheet.goals.map {
            it.copy(
                scorer = canonical(it.scorer),
                assist1 = it.assist1?.let(::canonical),
                assist2 = it.assist2?.let(::canonical),
            )
        },
        penalties = sheet.penalties.map { it.copy(player = canonical(it.player)) },
        goalies = sheet.goalies.map { it.copy(name = canonical(it.name)) },
    )
}
