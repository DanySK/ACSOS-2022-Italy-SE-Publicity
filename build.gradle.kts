import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.lordcodes.turtle.shellRun
import org.gradle.configurationcache.extensions.capitalized
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.github.doyaaaaaken:kotlin-csv-jvm:1.2.0")
        classpath("com.lordcodes.turtle:turtle:0.6.0")
    }
}

enum class Kart {
    DRIVER_WITH_RESERVE,
    PUBLIC,
    DRIVER,
    NOPE,
}

enum class Role {
    RESEARCHER,
    UNKNOWN,
    MASTER_STUDENT,
    STUDENT,
    OTHER;

    override fun toString() = when(this) {
        RESEARCHER -> "Researcher / PhD Student"
        UNKNOWN -> ""
        MASTER_STUDENT -> "DTM or LM student"
        STUDENT -> "Student"
        OTHER -> "Practitioner"
    }
}

data class Subscription(
    val time: LocalDateTime,
    val name: String,
    val email: String,
    val role: Role,
    val ivana: Boolean,
    val alessandro: Boolean,
    val lukas: Boolean,
    val kart: Kart,
    val dinner: Boolean,
) {
    val attendsAfternoon = alessandro && lukas
    val technicalParticipation: Int = ivana.toInt() + alessandro.toInt() + lukas.toInt()

    companion object {
        fun Boolean.toInt() = if (this) 1 else 0
        fun fromEntry(entry: List<String>): Subscription? {
            require(entry.size >= 11)
            if (entry[3].contains("online", ignoreCase = true)) return null
            val time = LocalDateTime.parse(entry[0], DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"))
            val email = entry[1].trim()
            val name = entry[2].trim().split(Regex("\\s+")).map { it.capitalized() }.joinToString(" ")
            val follows = entry[4] + entry[6]
            val role: Role = when {
                entry[5].isBlank() -> Role.UNKNOWN
                entry[5].contains("Researcher") -> Role.RESEARCHER
                entry[5].contains("LM-DTM") -> Role.MASTER_STUDENT
                entry[5].contains("enrolled") -> Role.STUDENT
                else -> Role.OTHER
            }
            val kart: Kart = when {
                follows.contains("Misanino") -> Kart.DRIVER_WITH_RESERVE
                entry[7].contains("DRIVER") && entry[8].contains("PUBLIC") -> Kart.DRIVER_WITH_RESERVE
                entry[7].contains("DRIVER") -> Kart.DRIVER
                entry[7].contains("PUBLIC") -> Kart.PUBLIC
                else -> Kart.NOPE
            }
            return Subscription(
                time = time,
                name = name,
                email = email,
                role = role,
                ivana = follows.contains("Ivana"),
                alessandro = follows.contains("Papadop"),
                lukas = follows.contains("Lukas"),
                kart = kart,
                dinner = follows.contains("Scottadito") || entry[9].contains("I will come")
            )
        }
    }
}

object CoffeeBreakPriority : Comparator<Subscription> {
    override fun compare(o1: Subscription, o2: Subscription) = listOf(
            o2.attendsAfternoon.compareTo(o1.attendsAfternoon),
            o2.technicalParticipation.compareTo(o1.technicalParticipation),
            o1.role.compareTo(o2.role),
            o1.time.compareTo(o2.time),
        )
        .firstOrNull { it != 0 } ?: 0
}

object BusPriority : Comparator<Subscription> {
    override fun compare(a: Subscription, b: Subscription) = when {
        a.kart == Kart.NOPE && b.kart == Kart.NOPE -> a.time.compareTo(b.time)
        a.kart == Kart.NOPE -> 1
        b.kart == Kart.NOPE -> -1
        a.technicalParticipation == 0 || b.technicalParticipation == 0 -> b.technicalParticipation.compareTo(a.technicalParticipation)
        a.role != b.role -> a.role.compareTo(b.role)
        a.kart != b.kart -> a.kart.compareTo(b.kart)
        else -> a.time.compareTo(b.time)
    }
}

object KartPriority : Comparator<Subscription> {
    override fun compare(a: Subscription, b: Subscription) = when {
        a.kart in listOf(Kart.PUBLIC, Kart.NOPE) ->
            if (b.kart in listOf(Kart.DRIVER, Kart.DRIVER_WITH_RESERVE)) 1 else a.time.compareTo(b.time)
        b.kart in listOf(Kart.PUBLIC, Kart.NOPE) ->
            if (a.kart in listOf(Kart.DRIVER, Kart.DRIVER_WITH_RESERVE)) -1 else a.time.compareTo(b.time)
        a.technicalParticipation == 0 || b.technicalParticipation == 0 -> listOf(
                b.technicalParticipation.compareTo(a.technicalParticipation),
                a.time.compareTo(b.time),
            ).firstOrNull { it != 0 } ?: 0
        else -> listOf(a.role.compareTo(b.role), a.kart.compareTo(b.kart), a.time.compareTo(b.time)).firstOrNull { it != 0 } ?: 0
    }
}

object DinnerPriority : Comparator<Subscription> {
    override fun compare(a: Subscription, b: Subscription) = listOf(
        b.dinner.compareTo(a.dinner),
        a.role.compareTo(b.role),
        BusPriority.compare(a, b),
        a.time.compareTo(b.time),
    ).firstOrNull { it != 0 } ?: 0

}

val generateBadges by tasks.registering {
    fun <X> List<X>.indexPadded(element: X): String = indexOf(element).let {
        if (it < 0) "---" else (it + 1).toString().padStart(3, ' ')
    }
    val csvs = projectDir.walkTopDown().filter { it.extension == "csv" }.toList()
    val destinationDir = File(buildDir, "badges")
    inputs.files(csvs)
    outputs.dir(destinationDir)
    doLast {
        check(csvs.size == 1) { "There is not exactly one CSV file with data: $csvs" }
        val csv = csvs.first()
        val rows: List<List<String>> = csvReader() { charset = "UTF-8" }.readAll(csv)
        val contents = rows.drop(1)
        val inPresence = contents.map(Subscription::fromEntry).filterNotNull().reversed().distinctBy { it.name }
        val breakPriority = inPresence.sortedWith(CoffeeBreakPriority)
        val participatesEvent = inPresence.filter { it.kart != Kart.NOPE }
        val busPriority = participatesEvent.sortedWith(BusPriority)
        val kartPriority = participatesEvent
            .filter { it.kart in listOf(Kart.DRIVER, Kart.DRIVER_WITH_RESERVE) }
            .sortedWith(KartPriority)
        val dinnerPriority = inPresence.filter(Subscription::dinner).sortedWith(DinnerPriority)
        val badgeBase = file("badge-base.svg").readText()
        destinationDir.mkdirs()
        inPresence.forEach { participant ->
            val destination = File(destinationDir, "${participant.name.replace(Regex("\\s"), "")}.svg")
            val svg = badgeBase
                .replace("Name Surname", participant.name)
                .replace("ROLE", participant.role.toString())
                .replace(Regex("(Coffee\\s+break\\s+#\\s+)000"), "$1${breakPriority.indexPadded(participant)}")
                .replace(Regex("(Bus\\s+seat\\s+#\\s+)000"), "$1${busPriority.indexPadded(participant)}")
                .replace(Regex("(Kart driver\\s+#\\s+)000"), "$1${kartPriority.indexPadded(participant)}")
                .replace(Regex("(Dinner\\s+#\\s+)000"), "$1${dinnerPriority.indexPadded(participant)}")
            destination.writeText(svg)
        }
        File(buildDir, "emails.txt").writeText(inPresence.map { it.email }.joinToString("\n"))
        fun List<Subscription>.markdownList() = mapIndexed { i, it -> "| ${i + 1}. ${it.name} <${it.email}>" }.joinToString("\n")
        File(buildDir, "participants.md").writeText(
            """
            |# Coffee Break
            ${breakPriority.markdownList()}
            |
            |# Bus
            ${busPriority.markdownList()}
            |
            |# Kart drivers
            ${kartPriority.markdownList()}
            |
            |# Dinner
            ${dinnerPriority.markdownList()}
            |
            |# All entries
            ${inPresence.map { "|${it.name}: " }}
            """.trimMargin()
        )
    }
}

val standardizeBadges by tasks.registering {
    dependsOn(generateBadges.get())
    val badges = File(buildDir, "badges")
    val outputs = File(buildDir, "standardSvgBadges")
    inputs.dir(badges)
    this.outputs.dir(outputs)
    doLast {
        outputs.mkdirs()
        badges.walkTopDown().filter { it.extension == "svg" }.forEach { badge ->
            val destination = File(outputs, badge.name)
            shellRun {
                command(
                    "inkscape",
                    listOf("--export-type=svg", "--export-filename=${destination.path}", "--export-plain-svg", badge.path)
                )
            }
        }
    }
}

tasks.register("printablePage") {
    dependsOn(standardizeBadges.get())
    val badges = File(buildDir, "standardSvgBadges")
    inputs.dir(badges)
    val destination = File(buildDir, "badges.html")
    doLast {
        val html = destination.bufferedWriter()
        html.write("<html><head><meta charset=\"utf-8\"></head><body>")
        badges.walkTopDown().filter { it.extension == "svg" }.forEach { badge ->
            html.write(badge.readText())
        }
        html.write("</body></html>")
        html.close()
    }
}

tasks.register("clean") {
    doLast{
        buildDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
