import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.time.LocalDateTime

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.github.doyaaaaaken:kotlin-csv-jvm:1.2.0")
    }
}

enum class Kart {
    DRIVER,
    DRIVER_WITH_RESERVE,
    PUBLIC,
}

enum class Role {
    RESEARCHER,
    MASTER_STUDENT,
    STUDENT,
    OTHER,
}

data class Subscription(
    val time: LocalDateTime,
    val name: String,
    val role:
    val attendsIvana: Boolean,
    val attendsAlessandro: Boolean,
    val attendsLukas: Boolean,
    val kart: Kart,
    val dinner: Boolean,
)

object DinnerPriority : Comparator<Subscription> {
    override fun compare(o1: Subscription, o2: Subscription) = when {
        TODO("Not yet implemented")
    }

}

tasks.register("generateBadges") {
    doLast {
        val csvs = projectDir.walkTopDown().filter { it.extension == "csv" }.toList()
        check(csvs.size == 1) { "There is not exactly one CSV file with data: $csvs" }
        val csv = csvs.first()
        val rows: List<List<String>> = csvReader().readAll(csv)
        val header = rows.first()
        val contents = rows.drop(1)
        println(rows)
    }
}
