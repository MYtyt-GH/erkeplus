package gitee.leolin_me.erkepp.data.eams

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

/**
 * `business_data` 解出来的 JSON 怎么读成模型。
 *
 * 字段名全部来自 2026-11-15 的真实响应（`docs/api/eams-jwglyd.md`），
 * 没有猜的字段；缺字段时给默认值而不是抛异常，避免一条坏记录毁掉整周课表。
 */
object EamsParsers {

    fun verifyCode(root: JsonElement): EamsVerifyCode {
        val obj = root.jsonObject
        return EamsVerifyCode(
            enable = obj["enable"]?.jsonPrimitive?.booleanOrNull ?: false,
            key = obj["key"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    fun login(root: JsonElement): EamsSession {
        val obj = root.jsonObject
        val info = obj["user_info"]?.let { runCatching { it.jsonObject }.getOrNull() }
        return EamsSession(
            token = obj["token"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            secret = obj["secret"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            userCode = info?.get("user_code")?.jsonPrimitive?.contentOrNull.orEmpty(),
            userName = info?.get("user_name")?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    fun semesters(root: JsonElement): EamsSemesterList {
        val obj = root.jsonObject
        val list = (obj["semesters"] as? JsonArray).orEmpty().mapNotNull { parseSemester(it) }
        return EamsSemesterList(
            currentId = obj["cur_semester_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            semesters = list,
        )
    }

    fun lessons(root: JsonElement): List<EamsLesson> {
        val array = root as? JsonArray ?: return emptyList()
        return array.mapNotNull { parseLesson(it) }
    }

    private fun parseSemester(element: JsonElement): EamsSemester? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val id = obj.text("id") ?: return null
        val start = obj.date("start_date") ?: return null
        val end = obj.date("end_date") ?: return null
        return EamsSemester(
            id = id,
            year = obj.text("year").orEmpty(),
            name = obj.text("name").orEmpty(),
            season = obj.text("season").orEmpty(),
            startDate = start,
            endDate = end,
            weekStartDay = obj["week_start_day"]?.jsonPrimitive?.intOrNull ?: 1,
        )
    }

    private fun parseLesson(element: JsonElement): EamsLesson? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        val date = obj.date("date") ?: return null
        return EamsLesson(
            courseCode = obj.text("course_code").orEmpty(),
            courseName = obj.text("course_name").orEmpty(),
            date = date,
            startUnit = obj["start_unit"]?.jsonPrimitive?.intOrNull ?: 0,
            endUnit = obj["end_unit"]?.jsonPrimitive?.intOrNull ?: 0,
            startTime = obj.text("start_time").orEmpty(),
            endTime = obj.text("end_time").orEmpty(),
            lessonId = obj["lesson_id"]?.jsonPrimitive?.longOrNull ?: 0L,
            lessonNo = obj.text("lesson_no").orEmpty(),
            rooms = (obj["rooms"] as? JsonArray).orEmpty().mapNotNull { parseRoom(it) },
            teachers = (obj["teachers"] as? JsonArray).orEmpty().mapNotNull { parseTeacher(it) },
            weeks = obj.text("weeks").orEmpty(),
            weekstate = obj.text("weekstate").orEmpty(),
            studentCount = obj["teachclass_std_count"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    private fun parseRoom(element: JsonElement): EamsRoom? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        return EamsRoom(
            id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            name = obj.text("name").orEmpty(),
            building = obj.text("building").orEmpty(),
            address = obj.text("address").orEmpty(),
        )
    }

    private fun parseTeacher(element: JsonElement): EamsTeacher? {
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return null
        return EamsTeacher(
            id = obj["id"]?.jsonPrimitive?.longOrNull ?: 0L,
            name = obj.text("name").orEmpty(),
        )
    }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.date(key: String): LocalDate? =
        text(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
