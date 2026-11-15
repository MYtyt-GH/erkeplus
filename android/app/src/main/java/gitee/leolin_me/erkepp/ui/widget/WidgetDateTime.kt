package gitee.leolin_me.erkepp.ui.widget

import gitee.leolin_me.erkepp.data.eams.EamsLesson
import java.time.LocalDate

/** 一天里的三个阶段，按官方作息表的节次切，不按钟点猜。 */
enum class DayStage(val label: String) {
    MORNING("上午"),
    AFTERNOON("下午"),
    EVENING("晚上"),
}

/**
 * 小组件里所有「把时间变成人话」的换算。
 *
 * 全是纯函数：不碰 Android、不读文件，单测直接喂日期和分钟数。
 */
object WidgetDateTime {

    /** 12:00 与 18:00 是阶段分界：上午最后一节 11:40 结束，下午最后一节 17:40 结束。 */
    private const val AFTERNOON_FROM = 12 * 60
    private const val EVENING_FROM = 18 * 60

    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun dateNumber(date: LocalDate): String = date.dayOfMonth.toString()

    fun monthLabel(date: LocalDate): String = "${date.monthValue}月"

    fun weekdayLabel(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    /** 「11月13日」 */
    fun monthDayLabel(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    /**
     * 这一天的叫法：今天返回空串（标题就不必再写「今天」），
     * 明天/后天给相对说法，更远的给周几。
     */
    fun dayHint(date: LocalDate, today: LocalDate): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
        return when {
            days <= 0L -> ""
            days == 1L -> "明天"
            days == 2L -> "后天"
            else -> weekdayLabel(date)
        }
    }

    /** `"08:00"` → 480。空串、越界、非数字一律 null。 */
    fun minuteOf(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun startMinute(lesson: EamsLesson): Int? = minuteOf(lesson.startTime)

    fun endMinute(lesson: EamsLesson): Int? = minuteOf(lesson.endTime)

    /** `08:00–09:40`；缺一头就只写有的那一头。 */
    fun timeRange(lesson: EamsLesson): String {
        val start = lesson.startTime.trim()
        val end = lesson.endTime.trim()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start–$end"
            start.isNotBlank() -> start
            else -> ""
        }
    }

    /** `明理北楼 明理北103`；多处教室补「等 N 处」。 */
    fun roomText(lesson: EamsLesson): String {
        val rooms = lesson.rooms
        if (rooms.isEmpty()) return ""
        val first = rooms.first()
        val place = listOf(first.building, first.name)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { first.address }
        return if (rooms.size > 1) "$place 等 ${rooms.size} 处" else place
    }

    /** 这节课属于哪个阶段：先看节次（1–4 / 5–8 / 9+），节次坏了才看钟点。 */
    fun stageOf(lesson: EamsLesson): DayStage = when {
        lesson.startUnit in 1..4 -> DayStage.MORNING
        lesson.startUnit in 5..8 -> DayStage.AFTERNOON
        lesson.startUnit >= 9 -> DayStage.EVENING
        else -> stageAt(lesson.startTime)
    }

    /** 几点钟算哪个阶段（给「当前处在哪半天」用）。 */
    fun stageAt(timeText: String): DayStage {
        val minute = minuteOf(timeText) ?: 0
        return stageAtMinute(minute)
    }

    fun stageAtMinute(minuteOfDay: Int): DayStage = when {
        minuteOfDay < AFTERNOON_FROM -> DayStage.MORNING
        minuteOfDay < EVENING_FROM -> DayStage.AFTERNOON
        else -> DayStage.EVENING
    }

    fun isPast(lesson: EamsLesson, nowMinute: Int): Boolean {
        val end = endMinute(lesson) ?: return false
        return nowMinute >= end
    }

    fun isOngoing(lesson: EamsLesson, nowMinute: Int): Boolean {
        val start = startMinute(lesson) ?: return false
        val end = endMinute(lesson) ?: return false
        return nowMinute in start until end
    }

    /** 同一门课在表格里合并显示时用：课名 + 老师，方便扫读。 */
    fun teacherText(lesson: EamsLesson): String = lesson.teachers.joinToString("、") { it.name }
}
