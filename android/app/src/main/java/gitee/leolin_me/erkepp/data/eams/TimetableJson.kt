package gitee.leolin_me.erkepp.data.eams

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

/**
 * 一周课表的落盘格式（纯 JSON，不依赖 Android）。
 *
 * 单独抽出来是为了两件事：
 * 1. 能在 JVM 单测里跑完整的编解码往返，不用起模拟器；
 * 2. 桌面小组件与后台提醒可以在**没有前台进程、没有网络**时同步读它。
 */
object TimetableJson {

    private val json = Json

    fun encode(week: TimetableWeek): String {
        val lessons = buildJsonArray {
            week.lessons.forEach { lesson ->
                add(
                    buildJsonObject {
                        put("courseCode", JsonPrimitive(lesson.courseCode))
                        put("courseName", JsonPrimitive(lesson.courseName))
                        put("date", JsonPrimitive(lesson.date.toString()))
                        put("startUnit", JsonPrimitive(lesson.startUnit))
                        put("endUnit", JsonPrimitive(lesson.endUnit))
                        put("startTime", JsonPrimitive(lesson.startTime))
                        put("endTime", JsonPrimitive(lesson.endTime))
                        put("lessonId", JsonPrimitive(lesson.lessonId))
                        put("lessonNo", JsonPrimitive(lesson.lessonNo))
                        put("weeks", JsonPrimitive(lesson.weeks))
                        put("weekstate", JsonPrimitive(lesson.weekstate))
                        put("studentCount", JsonPrimitive(lesson.studentCount))
                        put("rooms", buildJsonArray {
                            lesson.rooms.forEach { room ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(room.id))
                                    put("name", JsonPrimitive(room.name))
                                    put("building", JsonPrimitive(room.building))
                                    put("address", JsonPrimitive(room.address))
                                })
                            }
                        })
                        put("teachers", buildJsonArray {
                            lesson.teachers.forEach { teacher ->
                                add(buildJsonObject {
                                    put("id", JsonPrimitive(teacher.id))
                                    put("name", JsonPrimitive(teacher.name))
                                })
                            }
                        })
                    },
                )
            }
        }
        val root = buildJsonObject {
            put("fetchedAt", JsonPrimitive(week.fetchedAt))
            put("lessons", lessons)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** 解析失败一律返回 null——缓存坏了就当没有，绝不让它把页面弄崩。 */
    fun decode(text: String): TimetableWeek? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val fetchedAt = root["fetchedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        val array = root["lessons"] as? JsonArray ?: JsonArray(emptyList())
        val lessons = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val date = obj.text("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            EamsLesson(
                courseCode = obj.text("courseCode").orEmpty(),
                courseName = obj.text("courseName").orEmpty(),
                date = date,
                startUnit = obj["startUnit"]?.jsonPrimitive?.intOrNull ?: 0,
                endUnit = obj["endUnit"]?.jsonPrimitive?.intOrNull ?: 0,
                startTime = obj.text("startTime").orEmpty(),
                endTime = obj.text("endTime").orEmpty(),
                lessonId = obj["lessonId"]?.jsonPrimitive?.longOrNull ?: 0L,
                lessonNo = obj.text("lessonNo").orEmpty(),
                rooms = (obj["rooms"] as? JsonArray).orEmpty().mapNotNull { item ->
                    val room = item as? JsonObject ?: return@mapNotNull null
                    EamsRoom(
                        id = room["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                        name = room.text("name").orEmpty(),
                        building = room.text("building").orEmpty(),
                        address = room.text("address").orEmpty(),
                    )
                },
                teachers = (obj["teachers"] as? JsonArray).orEmpty().mapNotNull { item ->
                    val teacher = item as? JsonObject ?: return@mapNotNull null
                    EamsTeacher(
                        id = teacher["id"]?.jsonPrimitive?.longOrNull ?: 0L,
                        name = teacher.text("name").orEmpty(),
                    )
                },
                weeks = obj.text("weeks").orEmpty(),
                weekstate = obj.text("weekstate").orEmpty(),
                studentCount = obj["studentCount"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        return TimetableWeek(lessons = lessons, fetchedAt = fetchedAt)
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    /** 学期也一起缓存：离线时没有它就换算不出周次，课表缓存也就白存了。 */
    fun encodeSemester(semester: EamsSemester): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("id", JsonPrimitive(semester.id))
            put("year", JsonPrimitive(semester.year))
            put("name", JsonPrimitive(semester.name))
            put("season", JsonPrimitive(semester.season))
            put("startDate", JsonPrimitive(semester.startDate.toString()))
            put("endDate", JsonPrimitive(semester.endDate.toString()))
            put("weekStartDay", JsonPrimitive(semester.weekStartDay))
        },
    )

    fun decodeSemester(text: String): EamsSemester? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val id = root.text("id")?.takeIf { it.isNotBlank() } ?: return null
        val start = root.text("startDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val end = root.text("endDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return EamsSemester(
            id = id,
            year = root.text("year").orEmpty(),
            name = root.text("name").orEmpty(),
            season = root.text("season").orEmpty(),
            startDate = start,
            endDate = end,
            weekStartDay = root["weekStartDay"]?.jsonPrimitive?.intOrNull ?: 1,
        )
    }
}
