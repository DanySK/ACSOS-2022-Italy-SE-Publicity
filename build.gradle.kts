import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.github.doyaaaaaken:kotlin-csv-jvm:1.2.0")
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
    OTHER,
}

data class Subscription(
    val time: LocalDateTime,
    val name: String,
    val email: String,
    val role: Role,
    val attendsIvana: Boolean,
    val attendsAlessandro: Boolean,
    val attendsLukas: Boolean,
    val kart: Kart,
    val dinner: Boolean,
) {
    val attendsAfternoon = attendsAlessandro && attendsLukas
    val technicalParticipation: Int = attendsIvana.toInt() + attendsAlessandro.toInt() + attendsLukas.toInt()

    companion object {
        fun Boolean.compareTo(other: Boolean) = if (this == other) 0 else if (this) -1 else 1
        fun Boolean.toInt() = if (this) 1 else 0
        fun fromEntry(entry: List<String>): Subscription? {
            require(entry.size >= 11)
            if (entry[3].contains("online", ignoreCase = true)) return null
            val time = LocalDateTime.parse(entry[0], DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"))
            val email = entry[1]
            val name = entry[2]
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
                attendsIvana = follows.contains("Ivana"),
                attendsAlessandro = follows.contains("Papadop"),
                attendsLukas = follows.contains("Lukas"),
                kart = kart,
                dinner = entry[8].contains("I will come")
            )
        }
    }

}

object CoffeeBreakPriority : Comparator<Subscription> {
    override fun compare(o1: Subscription, o2: Subscription) = listOf(
            o1.attendsAfternoon.compareTo(o2.attendsAfternoon),
            o1.technicalParticipation.compareTo(o2.technicalParticipation),
            o1.role.compareTo(o2.role),
            o1.time.compareTo(o2.time),
        )
        .firstOrNull { it != 0 } ?: 0
}

object BusPriority : Comparator<Subscription> {
    override fun compare(a: Subscription, b: Subscription) = when {
        a.kart == Kart.NOPE && b.kart == Kart.NOPE -> a.time.compareTo(b.time)
        a.technicalParticipation == 0 || b.technicalParticipation == 0 -> a.technicalParticipation.compareTo(b.technicalParticipation)
        a.role != b.role -> a.role.compareTo(b.role)
        a.kart != b.kart -> a.kart.compareTo(b.kart)
        else -> a.time.compareTo(b.time)
    }
}

object KartPriority : Comparator<Subscription> {
    override fun compare(a: Subscription, b: Subscription) = when {
        a.kart in listOf(Kart.PUBLIC, Kart.NOPE) ->
            if (b.kart in listOf(Kart.DRIVER, Kart.DRIVER_WITH_RESERVE)) -1 else a.time.compareTo(b.time)
        b.kart in listOf(Kart.PUBLIC, Kart.NOPE) ->
            if (a.kart in listOf(Kart.DRIVER, Kart.DRIVER_WITH_RESERVE)) 1 else a.time.compareTo(b.time)
        a.technicalParticipation == 0 || b.technicalParticipation == 0 -> listOf(
                a.technicalParticipation.compareTo(b.technicalParticipation),
                a.time.compareTo(b.time),
            ).firstOrNull { it != 0 } ?: 0
        else -> listOf(a.role.compareTo(b.role), a.kart.compareTo(b.kart), a.time.compareTo(b.time)).firstOrNull { it != 0 } ?: 0
    }
}

object DinnerPriority : Comparator<Subscription> {
    override fun compare(a: Subscription, b: Subscription) = listOf(
        a.dinner.compareTo(b.dinner),
        a.role.compareTo(b.role),
        BusPriority.compare(a, b),
        a.time.compareTo(b.time),
    ).firstOrNull { it != 0 } ?: 0

}

tasks.register("generateBadges") {
    doLast {
        val csvs = projectDir.walkTopDown().filter { it.extension == "csv" }.toList()
        check(csvs.size == 1) { "There is not exactly one CSV file with data: $csvs" }
        val csv = csvs.first()
        val rows: List<List<String>> = csvReader().readAll(csv)
        val header = rows.first()
        val contents = rows.drop(1)
        val inPresence = contents.map(Subscription::fromEntry).filterNotNull().reversed().distinctBy { it.name }
        val breakPriority = inPresence.sortedWith(CoffeeBreakPriority)
        breakPriority.map { "${it.name} ${it.role} ${it.attendsAlessandro} ${it.attendsLukas}" }.forEach(::println)
    }
}
