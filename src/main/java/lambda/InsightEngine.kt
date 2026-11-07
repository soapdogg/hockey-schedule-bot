package lambda

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/** One thing worth saying about the team's history. */
data class Insight(
    /** Identifies the fact, so the same one isn't repeated soon. */
    val key: String,
    /** The kind of insight, so posts vary. */
    val type: String,
    /** Players the insight is about. */
    val subjects: List<String>,
    val text: String,
    val score: Double,
)

/** An insight that was posted, for cooldowns. */
data class PostedInsight(
    val key: String,
    val type: String,
    val subjects: List<String>,
    val postedAt: Instant,
)

/** What an insight is posted with. */
sealed interface Occasion {
    /** The recap of a game that was just played. */
    data class Recap(val gameId: String) : Occasion

    /** The announcement of an upcoming game. */
    data class PreGame(
        val opponent: String,
        val arena: String?,
        val start: LocalDateTime?,
        val homeAway: String?,
    ) : Occasion

    /** A post of its own between games, only for strong insights. */
    data object Midweek : Occasion
}

/**
 * A comparison of the team's record in games where something was true against games where it
 * wasn't. Only splits that survive a significance test across all splits get posted.
 */
class Split(
    val key: String,
    val type: String,
    val inPhrase: String,
    val outPhrase: String,
    /** Whether a game is in the group; null leaves the game out (missing data). */
    val applies: (GameFacts) -> Boolean?,
    /** Games the comparison is made over. */
    val universe: (GameFacts) -> Boolean = { true },
    val subject: String? = null,
    /** Only post when the group does better, so individuals are only mentioned favourably. */
    val positiveOnly: Boolean = false,
    val minIn: Int = InsightEngine.MIN_GROUP,
    val minOut: Int = InsightEngine.MIN_GROUP,
    /** Recap opener when the game just played is in the group. */
    val tonightLead: String? = null,
    /** Announcement opener when the upcoming game is in the group. */
    val upcomingLead: ((UpcomingGame) -> String?)? = null,
    /** Replaces [inPhrase] after an opener, such as "there" after "This one's at KVIC." */
    val inPhraseAfterLead: String? = null,
)

data class SplitResult(
    val split: Split,
    val inRecord: Record,
    val outRecord: Record,
    val pValue: Double,
    val significant: Boolean,
) {
    /** Difference in points share; positive when the group does better. */
    val difference: Double get() = inRecord.pointsShare - outRecord.pointsShare
}

/** What is known about an upcoming game before it's played. */
data class UpcomingGame(
    val arena: String?,
    val dayOfWeek: DayOfWeek?,
    val startTime: LocalTime?,
    val homeAway: String?,
    val daysSincePrevious: Long?,
)

class InsightEngine(private val random: Random = Random.Default) {

    fun candidates(games: List<ArchivedGame>, occasion: Occasion): List<Insight> {
        val facts = GameFacts.of(games)
        if (facts.isEmpty()) return emptyList()
        val results = evaluateSplits(facts)
        return when (occasion) {
            is Occasion.Recap -> {
                val tonight = facts.firstOrNull { it.game.gameId == occasion.gameId }
                splitInsights(results, tonight = tonight, upcoming = null) +
                    duoInsights(facts, tonight) +
                    (tonight?.let { milestonesReached(facts, it) } ?: emptyList())
            }
            is Occasion.PreGame -> {
                val upcoming = upcomingGame(facts, occasion)
                splitInsights(results, tonight = null, upcoming = upcoming) +
                    headToHead(facts, occasion.opponent) +
                    milestonesInReach(facts) +
                    duoInsights(facts, tonight = null)
            }
            Occasion.Midweek ->
                splitInsights(results, tonight = null, upcoming = null) +
                    milestonesInReach(facts) +
                    duoInsights(facts, tonight = null)
        }
    }

    /**
     * Picks one insight: drops anything on cooldown, then picks at random among the top few,
     * weighted by score, so posts vary without getting weaker.
     */
    fun choose(candidates: List<Insight>, recent: List<PostedInsight>, now: Instant, minScore: Double = 0.0): Insight? {
        val eligible = candidates.filter { c ->
            c.score >= minScore &&
                recent.none { it.key == c.key && it.postedAt.isAfter(now.minus(KEY_COOLDOWN)) } &&
                recent.none { it.type == c.type && it.postedAt.isAfter(now.minus(TYPE_COOLDOWN)) } &&
                recent.none { r -> r.subjects.any { it in c.subjects } && r.postedAt.isAfter(now.minus(SUBJECT_COOLDOWN)) }
        }
        // One per type, so the random pick is between different kinds of insight.
        val top = eligible.groupBy { it.type }.values.map { group -> group.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(TOP_CHOICES)
        if (top.isEmpty()) return null
        var roll = random.nextDouble() * top.sumOf { it.score }
        for (insight in top) {
            roll -= insight.score
            if (roll < 0) return insight
        }
        return top.last()
    }

    fun evaluateSplits(facts: List<GameFacts>): List<SplitResult> {
        val evaluated = splits(facts).mapNotNull { split ->
            val inGroup = mutableListOf<GameFacts>()
            val outGroup = mutableListOf<GameFacts>()
            for (f in facts) {
                if (!split.universe(f)) continue
                when (split.applies(f)) {
                    true -> inGroup += f
                    false -> outGroup += f
                    null -> {}
                }
            }
            if (inGroup.size < split.minIn || outGroup.size < split.minOut) return@mapNotNull null
            val p = InsightStats.permutationPValue(
                inGroup.map { it.outcome.points },
                outGroup.map { it.outcome.points },
                seed = split.key.hashCode(),
            )
            Triple(split, inGroup, outGroup) to p
        }
        val significant = InsightStats.benjaminiHochberg(evaluated.map { it.second }, FALSE_DISCOVERY_RATE)
        return evaluated.mapIndexed { i, (groups, p) ->
            val (split, inGroup, outGroup) = groups
            SplitResult(
                split = split,
                inRecord = Record.of(inGroup.map { it.outcome }),
                outRecord = Record.of(outGroup.map { it.outcome }),
                pValue = p,
                significant = significant[i],
            )
        }
    }

    internal fun splits(facts: List<GameFacts>): List<Split> {
        val list = mutableListOf<Split>()

        for (n in listOf(20, 25)) {
            list += Split(
                "shots-against-under-$n", "shots-against",
                "when we hold opponents under $n shots", "when we don't",
                applies = { f -> f.shotsAgainst?.let { it < n } },
                tonightLead = "We held them under $n shots in this one.",
            )
        }
        for (n in listOf(30, 35)) {
            list += Split(
                "shots-against-$n-plus", "shots-against",
                "when opponents get $n or more shots", "when they don't",
                applies = { f -> f.shotsAgainst?.let { it >= n } },
                tonightLead = "They got $n or more shots on us in this one.",
            )
        }
        for (n in listOf(30, 35)) {
            list += Split(
                "shots-for-$n-plus", "shots-for",
                "when we get $n or more shots", "when we don't",
                applies = { f -> f.shotsFor?.let { it >= n } },
                tonightLead = "We got $n or more shots in this one.",
            )
        }
        list += Split(
            "outshot", "shots-for",
            "when we outshoot the other team", "when we don't",
            applies = { f -> if (f.shotsFor == null || f.shotsAgainst == null) null else f.shotsFor > f.shotsAgainst },
            tonightLead = "We outshot them in this one.",
        )
        list += Split(
            "power-play-goal", "power-play",
            "when we score on the power play", "when we don't",
            applies = { f -> f.powerPlayGoalsFor > 0 },
            tonightLead = "We scored on the power play in this one.",
        )
        list += Split(
            "power-play-goal-against", "power-play",
            "when we give up a power-play goal", "when we don't",
            applies = { f -> f.powerPlayGoalsAgainst?.let { it > 0 } },
            tonightLead = "We gave up a power-play goal in this one.",
        )
        list += Split(
            "short-handed-goal", "power-play",
            "when we score short-handed", "when we don't",
            applies = { f -> f.shortHandedGoalsFor > 0 },
            tonightLead = "We scored short-handed in this one.",
        )
        list += Split(
            "scored-first", "first-goal",
            "when we score first", "when they score first",
            applies = { f -> f.scoredFirst },
            tonightLead = "We scored first in this one.",
        )
        list += Split(
            "few-penalties", "penalties",
            "when we take at most one penalty", "when we take more",
            applies = { f -> f.penaltiesTaken <= 1 },
            tonightLead = "We took at most one penalty in this one.",
        )
        list += Split(
            "many-penalties", "penalties",
            "when we take 4 or more penalties", "when we take fewer",
            applies = { f -> f.penaltiesTaken >= 4 },
            tonightLead = "We took 4 or more penalties in this one.",
        )
        list += Split(
            "more-penalties", "penalties",
            "when we take more penalties than the other team", "when we don't",
            applies = { f -> f.penaltiesDrawn?.let { f.penaltiesTaken > it } },
            tonightLead = "We took more penalties than they did in this one.",
        )
        list += Split(
            "big-bench", "bench",
            "with 12 or more skaters", "with fewer",
            applies = { f -> f.skaters >= 12 },
            tonightLead = "We had 12 or more skaters in this one.",
        )
        list += Split(
            "short-bench", "bench",
            "with 9 or fewer skaters", "with more",
            applies = { f -> f.skaters <= 9 },
            tonightLead = "We had 9 or fewer skaters in this one.",
        )

        val arenas = facts.mapNotNull { it.game.arena }.groupingBy { it }.eachCount()
        for ((arena, _) in arenas) {
            list += Split(
                "venue-$arena", "venue",
                "at $arena", "everywhere else",
                applies = { f -> f.game.arena?.let { it == arena } },
                tonightLead = "This one was at $arena.",
                upcomingLead = { u -> if (u.arena == arena) "This one's at $arena." else null },
                inPhraseAfterLead = "there",
            )
        }
        if (arenas.keys.count { it.startsWith(KCI_PREFIX) } > 1) {
            list += Split(
                "venue-KCI", "venue",
                "at KCI (any rink)", "everywhere else",
                applies = { f -> f.game.arena?.startsWith(KCI_PREFIX) },
                tonightLead = "This one was at KCI.",
                upcomingLead = { u -> if (u.arena?.startsWith(KCI_PREFIX) == true) "This one's at KCI." else null },
                inPhraseAfterLead = "at KCI",
            )
        }
        for (day in DayOfWeek.entries) {
            val name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            list += Split(
                "day-$name", "day",
                "on ${name}s", "on other days",
                applies = { f -> f.date?.let { it.dayOfWeek == day } },
                upcomingLead = { u -> if (u.dayOfWeek == day) "This one's on a $name." else null },
            )
        }
        list += Split(
            "late-start", "start-time",
            "in games starting at 10 PM or later", "in earlier games",
            applies = { f -> f.startTime?.let { it >= LATE } },
            upcomingLead = { u -> u.startTime?.takeIf { it >= LATE }?.let { "This one starts at ${it.format(CLOCK)}." } },
        )
        list += Split(
            "early-start", "start-time",
            "in games starting before 8 PM", "in later games",
            applies = { f -> f.startTime?.let { it < EARLY } },
            upcomingLead = { u -> u.startTime?.takeIf { it < EARLY }?.let { "This one starts at ${it.format(CLOCK)}." } },
        )
        list += Split(
            "home", "home-away",
            "as the home team", "as the away team",
            applies = { f -> f.game.homeAway?.uppercase()?.let { it == "HOME" } },
            upcomingLead = { u -> if (u.homeAway?.uppercase() == "HOME") "We're the home team for this one." else null },
        )
        list += Split(
            "long-break", "rest",
            "in our first game after two or more weeks off", "otherwise",
            applies = { f -> f.daysSincePrevious?.let { it >= LONG_BREAK_DAYS } },
            tonightLead = "That was our first game in two weeks or more.",
            upcomingLead = { u -> if ((u.daysSincePrevious ?: 0) >= LONG_BREAK_DAYS) "It's our first game in two weeks or more." else null },
        )

        // With or without a current player, compared within the seasons they've played in, so
        // the comparison isn't just between better and worse eras of the team.
        for (player in currentSkaters(facts)) {
            val seasons = facts.filter { f -> player in skaterNames(f) }.map { it.game.seasonId }.toSet()
            list += Split(
                "with-$player", "with-player",
                "with $player in the lineup", "without",
                applies = { f -> player in skaterNames(f) },
                universe = { f -> f.game.seasonId in seasons },
                subject = player,
                positiveOnly = true,
                minOut = MIN_MISSED_GAMES,
            )
        }
        return list
    }

    private fun splitInsights(results: List<SplitResult>, tonight: GameFacts?, upcoming: UpcomingGame?): List<Insight> =
        results.mapNotNull { r ->
            val split = r.split
            if (!r.significant || abs(r.difference) < MIN_DIFFERENCE) return@mapNotNull null
            if (split.positiveOnly && r.difference <= 0) return@mapNotNull null

            val tonightApplies = tonight != null && split.universe(tonight) && split.applies(tonight) == true
            val lead = when {
                tonightApplies -> split.tonightLead
                upcoming != null -> split.upcomingLead?.invoke(upcoming)
                else -> null
            }
            val inPhrase = if (lead != null) split.inPhraseAfterLead ?: split.inPhrase else split.inPhrase
            val now = if (tonightApplies) "now " else ""
            val sentence = "We're $now${r.inRecord} $inPhrase, and ${r.outRecord} ${split.outPhrase}."
            Insight(
                key = split.key,
                type = split.type,
                subjects = listOfNotNull(split.subject),
                text = listOfNotNull(lead, sentence).joinToString(" "),
                score = abs(r.difference) * 10 * (if (lead != null) RELEVANCE_BONUS else 1.0),
            )
        }

    /** Assist connections: who sets up whom most often. */
    private fun duoInsights(facts: List<GameFacts>, tonight: GameFacts?): List<Insight> {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        for (f in facts) {
            for (goal in f.game.us.goals) {
                for (assist in listOfNotNull(goal.assist1, goal.assist2).distinct()) {
                    if (assist != goal.scorer) counts.merge(assist to goal.scorer, 1, Int::plus)
                }
            }
        }
        fun rank(count: Int) = 1 + counts.values.count { it > count }
        fun describe(pair: Pair<String, String>, count: Int, includingTonight: Boolean = false): String {
            val (assister, scorer) = pair
            val standing = when (rank(count)) {
                1 -> if (counts.values.count { it == count } == 1) ", more than any other pair on the team" else ", tied for the most of any pair on the team"
                2 -> ", the second-most of any pair on the team"
                3 -> ", the third-most of any pair on the team"
                else -> ""
            }
            val tonightPart = if (includingTonight) ", including one in this game" else ""
            val now = if (includingTonight) "now " else ""
            return "$assister has ${now}assisted on $count of ${possessive(scorer)} goals$tonightPart$standing."
        }

        if (tonight != null) {
            val tonightPairs = tonight.game.us.goals.flatMap { goal ->
                listOfNotNull(goal.assist1, goal.assist2).distinct().filter { it != goal.scorer }.map { it to goal.scorer }
            }.toSet()
            return tonightPairs.mapNotNull { pair ->
                val count = counts[pair] ?: return@mapNotNull null
                if (count < MIN_DUO_RECAP) return@mapNotNull null
                val r = rank(count)
                Insight(
                    key = "duo-${pair.first}-${pair.second}-$count",
                    type = "duo",
                    subjects = listOf(pair.first, pair.second),
                    text = describe(pair, count, includingTonight = true),
                    score = when { r == 1 -> 3.0; r <= 3 -> 2.5; else -> 2.0 },
                )
            }
        }

        val current = currentSkaters(facts).toSet()
        return counts.entries
            .filter { (pair, count) -> pair.first in current && pair.second in current && count >= MIN_DUO_GENERAL }
            .sortedByDescending { it.value }
            .take(3)
            .map { (pair, count) ->
                Insight(
                    key = "duo-${pair.first}-${pair.second}-$count",
                    type = "duo",
                    subjects = listOf(pair.first, pair.second),
                    text = describe(pair, count),
                    score = if (rank(count) == 1) 1.5 else 1.0,
                )
            }
    }

    /** Career totals for the team, from the archive. */
    private class Totals {
        val goals = mutableMapOf<String, Int>()
        val assists = mutableMapOf<String, Int>()
        val points = mutableMapOf<String, Int>()
        val games = mutableMapOf<String, Int>()
        val saves = mutableMapOf<String, Int>()

        fun add(f: GameFacts) {
            val us = f.game.us
            for (name in us.roster.map { it.name }.toSet()) games.merge(name, 1, Int::plus)
            for (goal in us.goals) {
                goals.merge(goal.scorer, 1, Int::plus)
                points.merge(goal.scorer, 1, Int::plus)
                for (a in listOfNotNull(goal.assist1, goal.assist2).distinct()) {
                    assists.merge(a, 1, Int::plus)
                    points.merge(a, 1, Int::plus)
                }
            }
            for (g in us.goalies) saves.merge(g.name, g.saves, Int::plus)
        }

        fun snapshot(): Map<String, Map<String, Int>> = mapOf(
            "goals" to goals.toMap(), "assists" to assists.toMap(), "points" to points.toMap(),
            "games" to games.toMap(), "saves" to saves.toMap(),
        )
    }

    private fun milestonesReached(facts: List<GameFacts>, tonight: GameFacts): List<Insight> {
        val totals = Totals()
        val before = facts.takeWhile { it !== tonight }.onEach { totals.add(it) }.let { totals.snapshot() }
        totals.add(tonight)
        val after = totals.snapshot()
        val insights = mutableListOf<Insight>()
        for ((stat, thresholds) in MILESTONES) {
            for ((player, total) in after.getValue(stat)) {
                val previous = before.getValue(stat)[player] ?: 0
                val reached = thresholds(total).lastOrNull { it in (previous + 1)..total } ?: continue
                val text = when (stat) {
                    "games" -> "$player played game $reached for the team in this one."
                    else -> "$player reached ${formatNumber(reached)} $stat for the team in this one."
                }
                insights += Insight("milestone-$player-$stat-$reached", "milestone", listOf(player), text, MILESTONE_REACHED_SCORE * milestoneWeight(stat, reached))
            }
        }
        return insights
    }

    private fun milestonesInReach(facts: List<GameFacts>): List<Insight> {
        val totals = Totals()
        facts.forEach { totals.add(it) }
        val current = currentPlayers(facts)
        val insights = mutableListOf<Insight>()
        for ((stat, thresholds) in MILESTONES) {
            for ((player, total) in totals.snapshot().getValue(stat)) {
                if (player !in current) continue
                val next = thresholds(total + MILESTONE_REACH.getValue(stat)).firstOrNull { it > total } ?: continue
                val away = next - total
                if (away > MILESTONE_REACH.getValue(stat)) continue
                val text = when (stat) {
                    "games" -> "${possessive(player)} next game will be number $next for the team."
                    else -> "$player is $away ${if (away == 1) stat.removeSuffix("s") else stat} away from ${formatNumber(next)} for the team."
                }
                insights += Insight("milestone-$player-$stat-$next-soon", "milestone", listOf(player), text, MILESTONE_SOON_SCORE * milestoneWeight(stat, next))
            }
        }
        return insights
    }

    private fun headToHead(facts: List<GameFacts>, opponent: String): List<Insight> {
        val meetings = facts.filter { it.game.opponent.equals(opponent, ignoreCase = true) }
        if (meetings.size < MIN_MEETINGS) return emptyList()
        val record = Record.of(meetings.map { it.outcome })
        val last = meetings.last()
        val lastText = last.date?.let { date ->
            val result = when {
                last.game.result.uppercase().startsWith("SO") && last.outcome == Outcome.LOSS -> "shootout loss"
                last.outcome == Outcome.WIN -> "win"
                last.outcome == Outcome.TIE -> "tie"
                else -> "loss"
            }
            " Last time: a ${last.game.ourScore}-${last.game.oppScore} $result on ${date.format(DATE)}."
        } ?: ""
        val lopsided = meetings.size >= 5 && (record.pointsShare >= 0.75 || record.pointsShare <= 0.25)
        return listOf(
            Insight(
                key = "h2h-$opponent-${meetings.size}",
                type = "head-to-head",
                subjects = emptyList(),
                text = "We're $record all-time against $opponent.$lastText",
                score = if (lopsided) 3.0 else 2.5,
            )
        )
    }

    private fun upcomingGame(facts: List<GameFacts>, occasion: Occasion.PreGame): UpcomingGame {
        val startDate = occasion.start?.toLocalDate()
        val previous = startDate?.let { d -> facts.mapNotNull { it.date }.filter { it < d }.maxOrNull() }
        return UpcomingGame(
            arena = occasion.arena,
            dayOfWeek = occasion.start?.dayOfWeek,
            startTime = occasion.start?.toLocalTime(),
            homeAway = occasion.homeAway,
            daysSincePrevious = if (startDate != null && previous != null) ChronoUnit.DAYS.between(previous, startDate) else null,
        )
    }

    private fun currentSeason(facts: List<GameFacts>): String? = facts.lastOrNull()?.game?.seasonId

    private fun currentSkaters(facts: List<GameFacts>): List<String> {
        val season = currentSeason(facts) ?: return emptyList()
        return facts.filter { it.game.seasonId == season }.flatMap { skaterNames(it) }.distinct().sorted()
    }

    private fun currentPlayers(facts: List<GameFacts>): Set<String> {
        val season = currentSeason(facts) ?: return emptySet()
        return facts.filter { it.game.seasonId == season }.flatMap { f -> f.game.us.roster.map { it.name } }.toSet()
    }

    private fun skaterNames(f: GameFacts): Set<String> = f.game.us.roster.filter { !it.isGoalie }.map { it.name }.toSet()

    companion object {
        const val MIN_GROUP = 10
        const val MIN_MISSED_GAMES = 8
        const val FALSE_DISCOVERY_RATE = 0.10
        const val MIN_DIFFERENCE = 0.10
        const val RELEVANCE_BONUS = 1.5
        const val MIN_DUO_RECAP = 5
        const val MIN_DUO_GENERAL = 8
        const val MIN_MEETINGS = 3
        const val MILESTONE_REACHED_SCORE = 4.0
        const val MILESTONE_SOON_SCORE = 3.0
        /** Midweek posts only go out for insights at least this strong. */
        const val MIDWEEK_MIN_SCORE = 3.5
        private const val TOP_CHOICES = 3
        private const val LONG_BREAK_DAYS = 14L
        private const val KCI_PREFIX = "KCI "
        private val LATE: LocalTime = LocalTime.of(22, 0)
        private val EARLY: LocalTime = LocalTime.of(20, 0)
        private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        private val DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)

        val KEY_COOLDOWN: Duration = Duration.ofDays(60)
        val TYPE_COOLDOWN: Duration = Duration.ofDays(14)
        val SUBJECT_COOLDOWN: Duration = Duration.ofDays(21)

        /** Milestone numbers for each stat, up to (at least) a given total. */
        private val MILESTONES: Map<String, (Int) -> List<Int>> = mapOf(
            "goals" to { max -> listOf(10, 25) + (50..maxOf(50, max + 25) step 25) },
            "assists" to { max -> listOf(10, 25) + (50..maxOf(50, max + 25) step 25) },
            "points" to { max -> listOf(25) + (50..maxOf(50, max + 50) step 50) },
            "games" to { max -> listOf(25) + (50..maxOf(50, max + 50) step 50) },
            "saves" to { max -> (500..maxOf(500, max + 500) step 500).toList() },
        )

        /** How close counts as "within reach" before a game. */
        private val MILESTONE_REACH = mapOf("goals" to 2, "assists" to 2, "points" to 2, "games" to 1, "saves" to 30)

        /** Small milestones (10 goals, 25 games) count for less than round, bigger ones. */
        private fun milestoneWeight(stat: String, value: Int): Double {
            val big = if (stat == "saves") 1000 else 100
            val medium = if (stat == "saves") 500 else 50
            return when {
                value >= big -> 1.15
                value >= medium -> 1.0
                else -> 0.7
            }
        }

        private fun possessive(name: String) = if (name.endsWith("s")) "$name'" else "$name's"

        private fun formatNumber(n: Int) = String.format(Locale.ENGLISH, "%,d", n)
    }
}
