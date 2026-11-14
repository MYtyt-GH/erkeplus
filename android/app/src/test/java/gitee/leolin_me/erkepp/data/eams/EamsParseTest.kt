package gitee.leolin_me.erkepp.data.eams

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 教务响应的解析、学期周次换算、课表缓存的编解码。
 *
 * 样本结构照抄 2026-11-15 的真实响应，但姓名/课程一律换成假数据——
 * 仓库里不写真实姓名（这条以前在 docs 里违过一次）。
 */
class EamsParseTest {

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `验证码开关解析`() {
        val code = EamsParsers.verifyCode(json("""{"enable":false}"""))
        assertEquals(false, code.enable)
        assertEquals("", code.key)

        val enabled = EamsParsers.verifyCode(json("""{"enable":true,"key":"k-1"}"""))
        assertTrue(enabled.enable)
        assertEquals("k-1", enabled.key)
    }

    @Test
    fun `登录响应解析出 token 与 secret`() {
        val session = EamsParsers.login(
            json(
                """
                {"token":"tok-123","secret":"sec-456","access_method":"pwd",
                 "cur_identity":"1","roleId":"3",
                 "user_info":{"user_code":"2026000001","user_name":"某同学","gender":1}}
                """.trimIndent(),
            ),
        )
        assertEquals("tok-123", session.token)
        assertEquals("sec-456", session.secret)
        assertEquals("2026000001", session.userCode)
        assertEquals("某同学", session.userName)
    }

    @Test
    fun `学期列表解析并挑出当前学期`() {
        val list = EamsParsers.semesters(
            json(
                """
                {"cur_semester_id":"340","semesters":[
                  {"code":"2026202701","id":"340","year":"2026-2027","name":"1","season":"秋",
                   "start_date":"2026-08-31","end_date":"2027-01-17","week_start_day":1},
                  {"code":"2025202602","id":"320","year":"2025-2026","name":"2","season":"春",
                   "start_date":"2026-03-02","end_date":"2026-07-12","week_start_day":1}]}
                """.trimIndent(),
            ),
        )
        assertEquals(2, list.semesters.size)
        val current = list.current()
        assertNotNull(current)
        assertEquals("340", current!!.id)
        assertEquals("2026-2027 第 1 学期", current.label)
        assertEquals(LocalDate.of(2026, 8, 31), current.startDate)
    }

    @Test
    fun `课表按次解析出房间与教师`() {
        val lessons = EamsParsers.lessons(
            json(
                """
                [{"course_code":"1302001111","course_name":"某门课(一)","date":"2026-11-12",
                  "start_unit":7,"end_unit":8,"start_time":"16:00","end_time":"17:40",
                  "lesson_id":401117,"lesson_no":"1302001111.03",
                  "rooms":[{"id":358,"code":"045103","name":"某楼103","building":"某楼",
                            "address":"校本部 某楼 某楼103"}],
                  "teachers":[{"id":2749,"code":"2017027","name":"某老师"}],
                  "weeks":"5,6,7","weekstate":"5-7","teachclass_std_count":83},
                 {"course_code":"1302001111","course_name":"某门课(一)","date":"2026-11-10",
                  "start_unit":3,"end_unit":4,"start_time":"10:00","end_time":"11:40",
                  "lesson_id":401117,"lesson_no":"1302001111.03",
                  "rooms":[],"teachers":[],"weeks":"5","weekstate":"5","teachclass_std_count":83}]
                """.trimIndent(),
            ),
        )
        assertEquals(2, lessons.size)
        val first = lessons.first()
        assertEquals("某门课(一)", first.courseName)
        assertEquals(LocalDate.of(2026, 11, 12), first.date)
        assertEquals(7, first.startUnit)
        assertEquals("某楼 某楼103", first.rooms.first().display)
        assertEquals("某老师", first.teacherNames)
        assertEquals(83, first.studentCount)
        // 没有教室的记录也不能炸
        assertEquals("", lessons[1].roomNames)
    }

    @Test
    fun `坏记录跳过而不是整体失败`() {
        val lessons = EamsParsers.lessons(
            json(
                """
                [{"course_name":"日期缺失"},{"course_name":"好的","date":"2026-11-12","start_unit":1}]
                """.trimIndent(),
            ),
        )
        assertEquals(1, lessons.size)
        assertEquals("好的", lessons.first().courseName)
    }

    @Test
    fun `开学那天是第 1 周 11 月 9 日是第 11 周`() {
        val semester = semester()
        assertEquals(1, SemesterCalendar.weekOf(semester, LocalDate.of(2026, 8, 31)))
        assertEquals(11, SemesterCalendar.weekOf(semester, LocalDate.of(2026, 11, 9)))
        assertEquals(11, SemesterCalendar.weekOf(semester, LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 9), SemesterCalendar.weekStart(semester, 11))
    }

    @Test
    fun `学期前后越界的日期夹到合法周次`() {
        val semester = semester()
        assertEquals(1, SemesterCalendar.weekOf(semester, LocalDate.of(2026, 8, 1)))
        assertEquals(20, SemesterCalendar.weekCount(semester))
        assertEquals(20, SemesterCalendar.weekOf(semester, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun `当天的课按节次排序`() {
        val week = lessonsOfWeek()
        val today = week.on(LocalDate.of(2026, 11, 12))
        assertEquals(2, today.size)
        assertEquals(3, today[0].startUnit)
        assertEquals(7, today[1].startUnit)
        assertTrue(week.byDayIndex(LocalDate.of(2026, 11, 9))[3]!!.isNotEmpty())
    }

    @Test
    fun `缓存编解码往返不丢字段`() {
        val week = TimetableWeek(lessons = lessonsOfWeek(), fetchedAt = 1794678794993L)
        val decoded = TimetableJson.decode(TimetableJson.encode(week))
        assertNotNull(decoded)
        assertEquals(3, decoded!!.lessons.size)
        assertEquals(1794678794993L, decoded.fetchedAt)
        assertEquals(week.lessons[0], decoded.lessons[0])
        assertEquals(week.lessons[0].rooms, decoded.lessons[0].rooms)
        assertEquals(week.lessons[0].teachers, decoded.lessons[0].teachers)
    }

    @Test
    fun `缓存损坏时返回 null 而不是抛异常`() {
        assertNull(TimetableJson.decode("不是 JSON"))
        assertNull(TimetableJson.decode("") )
    }

    private fun semester() = EamsSemester(
        id = "340",
        year = "2026-2027",
        name = "1",
        season = "秋",
        startDate = LocalDate.of(2026, 8, 31),
        endDate = LocalDate.of(2027, 1, 17),
        weekStartDay = 1,
    )

    private fun lessonsOfWeek(): List<EamsLesson> = listOf(
        lesson(date = LocalDate.of(2026, 11, 12), startUnit = 7, endUnit = 8),
        lesson(date = LocalDate.of(2026, 11, 12), startUnit = 3, endUnit = 4),
        lesson(date = LocalDate.of(2026, 11, 10), startUnit = 1, endUnit = 2),
    )

    private fun lesson(date: LocalDate, startUnit: Int, endUnit: Int) = EamsLesson(
        courseCode = "1302001111",
        courseName = "某门课(一)",
        date = date,
        startUnit = startUnit,
        endUnit = endUnit,
        startTime = "08:00",
        endTime = "09:40",
        lessonId = 401117L,
        lessonNo = "1302001111.03",
        rooms = listOf(EamsRoom(358L, "某楼103", "某楼", "校本部 某楼 某楼103")),
        teachers = listOf(EamsTeacher(2749L, "某老师")),
        weeks = "5,6,7",
        weekstate = "5-7",
        studentCount = 83,
    )
}
