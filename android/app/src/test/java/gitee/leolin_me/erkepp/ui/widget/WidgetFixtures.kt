package gitee.leolin_me.erkepp.ui.widget

import gitee.leolin_me.erkepp.data.eams.EamsLesson
import gitee.leolin_me.erkepp.data.eams.EamsRoom
import gitee.leolin_me.erkepp.data.eams.EamsSemester
import java.time.LocalDate

/**
 * 小组件测试共用的样本。
 *
 * 日期是算过的：2026-08-31 是第 1 周周一，所以 2026-11-13（周五）是第 11 周，
 * 2026-11-14 是周六、2026-11-16 是下周一。
 */
object WidgetFixtures {

    val semester = EamsSemester(
        id = "340",
        year = "2026-2027",
        name = "1",
        season = "秋",
        startDate = LocalDate.of(2026, 8, 31),
        endDate = LocalDate.of(2027, 1, 17),
        weekStartDay = 1,
    )

    val friday: LocalDate = LocalDate.of(2026, 11, 13)
    val saturday: LocalDate = LocalDate.of(2026, 11, 14)

    fun lesson(
        name: String,
        date: LocalDate = friday,
        start: String = "08:00",
        end: String = "09:40",
        unit: Int = 1,
        room: String = "明理北103",
    ) = EamsLesson(
        courseCode = "C$unit-$name",
        courseName = name,
        date = date,
        startUnit = unit,
        endUnit = unit + 1,
        startTime = start,
        endTime = end,
        lessonId = unit.toLong(),
        lessonNo = unit.toString(),
        rooms = if (room.isEmpty()) emptyList() else listOf(EamsRoom(1L, room, "明理北楼", "")),
        teachers = emptyList(),
        weeks = "",
        weekstate = "",
        studentCount = 0,
    )

    fun snapshot(
        lessons: List<EamsLesson>,
        nowMinute: Int,
        today: LocalDate = friday,
        weekCached: Boolean = true,
        sem: EamsSemester? = semester,
    ) = WidgetSnapshot(
        semester = sem,
        today = today,
        nowMinute = nowMinute,
        lessons = lessons,
        weekCached = weekCached,
    )

    /** 上午三节、下午一节的标准样本。 */
    val threeLessons = listOf(
        lesson("高等数学", start = "08:00", end = "09:40", unit = 1),
        lesson("大学物理", start = "10:00", end = "11:40", unit = 3),
        lesson("大学英语", start = "14:00", end = "15:40", unit = 5),
    )
}
