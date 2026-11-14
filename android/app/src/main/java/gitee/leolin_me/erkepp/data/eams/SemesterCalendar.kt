package gitee.leolin_me.erkepp.data.eams

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 学期 ↔ 周次的计算。
 *
 * 依据 2026-11-15 实测：`start_date` 就是第 1 周的周一（`week_start_day=1`），
 * 2026-11-09 ~ 11-15 对应第 11 周（(11-09 减 08-31) 是 70 天 = 10 周，+1 = 11）。
 *
 * 纯函数，不依赖 Android，提醒与桌面小组件也会用到。
 */
object SemesterCalendar {

    /** 第 [week] 周的第一天。1 = 开学那一周。 */
    fun weekStart(semester: EamsSemester, week: Int): LocalDate =
        semester.startDate.plusWeeks((week - 1).toLong())

    /**
     * [date] 落在第几周。开学前返回 1、学期结束后返回最后一周——
     * 界面上永远给它一个合法值，不要在 UI 里再夹一次。
     */
    fun weekOf(semester: EamsSemester, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(semester.startDate, date)
        val raw = Math.floorDiv(days, 7L).toInt() + 1
        return raw.coerceIn(1, weekCount(semester))
    }

    /** 学期总周数（向上取整，2026-2027-1 实测是 20 周）。 */
    fun weekCount(semester: EamsSemester): Int {
        val days = ChronoUnit.DAYS.between(semester.startDate, semester.endDate) + 1
        // 不用 Math.ceilDiv：那是 Java 18 才有的，Android 上没有
        return ((days + 6) / 7).toInt().coerceAtLeast(1)
    }

    /** 学期是否还没结束（用来决定「下一周」能不能点）。 */
    fun hasWeek(semester: EamsSemester, week: Int): Boolean = week in 1..weekCount(semester)
}

/** 某一天有哪些课，按开始节次排好。给今日摘要、上课提醒、桌面小组件共用。 */
fun List<EamsLesson>.on(date: LocalDate): List<EamsLesson> =
    filter { it.date == date }.sortedBy { it.startUnit }

/** 一周的课按「距周一几天」分组（0 = 周一）。周表格直接照这个下标取列。 */
fun List<EamsLesson>.byDayIndex(weekStart: LocalDate): Map<Int, List<EamsLesson>> =
    groupBy { ChronoUnit.DAYS.between(weekStart, it.date).toInt() }
        .filterKeys { it in 0..6 }
        .mapValues { (_, lessons) -> lessons.sortedBy { it.startUnit } }
