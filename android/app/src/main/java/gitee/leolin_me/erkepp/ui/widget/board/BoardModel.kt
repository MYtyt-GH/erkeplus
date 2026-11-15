package gitee.leolin_me.erkepp.ui.widget.board

import gitee.leolin_me.erkepp.data.eams.EamsLesson
import gitee.leolin_me.erkepp.data.eams.SemesterCalendar
import gitee.leolin_me.erkepp.data.eams.on
import gitee.leolin_me.erkepp.ui.widget.WidgetDateTime
import gitee.leolin_me.erkepp.ui.widget.WidgetGate
import gitee.leolin_me.erkepp.ui.widget.WidgetLessonRow
import gitee.leolin_me.erkepp.ui.widget.WidgetNotice
import gitee.leolin_me.erkepp.ui.widget.WidgetSnapshot
import gitee.leolin_me.erkepp.ui.widget.WidgetStatus
import gitee.leolin_me.erkepp.ui.widget.widgetRow
import java.time.LocalDate

/** 看板里的一行：要么是「换了一天」的标签，要么是一节课。 */
sealed interface BoardBlock {
    data class Day(val label: String) : BoardBlock

    data class Course(val row: WidgetLessonRow) : BoardBlock
}

/**
 * 课程看板要摆的全部字：从「现在」往后的连续课表——今天没上完的接着明天的，
 * 只要桌面放得下就多摆几节，换天时插一行「明天 11/16」。
 *
 * 为什么不只画今天：傍晚以后今天就没课了，只画今天等于桌面空着；
 * 连续排下去，晚上能看到明天的课，才配叫「看板」。
 */
data class BoardModel(
    val weekLabel: String,
    /** 右上角：今天写「11/13 周五」；第一节课不在今天时写「明天 11/14」。 */
    val dateLabel: String,
    val blocks: List<BoardBlock>,
    val footer: String?,
    val notice: WidgetNotice?,
) {
    companion object {

        /** 往后最多找几天，够跨一个周末。 */
        private const val LOOKAHEAD_DAYS = 7

        fun build(snapshot: WidgetSnapshot, maxRows: Int): BoardModel {
            val today = snapshot.today
            val weekLabel = snapshot.semester
                ?.let { "第 ${SemesterCalendar.weekOf(it, today)} 周" }
                .orEmpty()
            val notice = WidgetStatus.notice(snapshot.gate, snapshot.semester, today)
            if (snapshot.gate != WidgetGate.READY) {
                return BoardModel(weekLabel, dateLabelOf(today, today), emptyList(), null, notice)
            }

            val capacity = maxRows.coerceAtLeast(1)
            val blocks = mutableListOf<BoardBlock>()
            var courses = 0
            var firstDay: LocalDate? = null

            for (offset in 0 until LOOKAHEAD_DAYS) {
                val day = today.plusDays(offset.toLong())
                val lessons = snapshot.lessons.on(day).filter { lesson ->
                    day != today || !WidgetDateTime.isPast(lesson, snapshot.nowMinute)
                }
                if (lessons.isEmpty()) continue
                firstDay = firstDay ?: day
                // 只有这一天真的有课能摆出来，才插日期行；否则会出现「周三」后面一节课都没有
                val visible = lessons.take((capacity - courses).coerceAtLeast(0))
                if (visible.isEmpty()) continue
                if (day != firstDay) {
                    blocks += BoardBlock.Day(dayLabelOf(day, today))
                }
                visible.forEach { lesson ->
                    blocks += BoardBlock.Course(widgetRow(lesson, today, snapshot.nowMinute, snapshot.aliases))
                    courses++
                }
            }

            if (blocks.isEmpty()) {
                // 今天上过课、只是全上完了，和「今天本来就没课」要分开说
                val hadToday = snapshot.lessons.on(today).isNotEmpty()
                return BoardModel(
                    weekLabel = weekLabel,
                    dateLabel = dateLabelOf(today, today),
                    blocks = emptyList(),
                    footer = null,
                    notice = if (hadToday) {
                        WidgetNotice("今天的课上完了", "这周剩下的时间没课")
                    } else {
                        WidgetNotice("今天没有课程", "这周剩下的时间没课")
                    },
                )
            }

            return BoardModel(
                weekLabel = weekLabel,
                dateLabel = dateLabelOf(firstDay ?: today, today),
                blocks = blocks,
                footer = footerOf(snapshot.lessons.on(today), snapshot.nowMinute),
                notice = null,
            )
        }

        /**
         * 页脚：今天还剩几节（全天的、含已显示的），今天没课/课上完了也如实说。
         * 具体哪几节没摆上桌面，用户进课表页看得到，不必在这里报数。
         */
        private fun footerOf(todays: List<EamsLesson>, nowMinute: Int): String? {
            if (todays.isEmpty()) return null
            val left = todays.count { !WidgetDateTime.isPast(it, nowMinute) }
            return if (left > 0) "今天还有 $left 节" else "今天的课上完了"
        }

        /** 今天写「11/13 周五」，别的日子写「明天 11/14」——桌面上一行长，日期要短。 */
        private fun dateLabelOf(day: LocalDate, today: LocalDate): String {
            val short = "${day.monthValue}/${day.dayOfMonth}"
            val hint = WidgetDateTime.dayHint(day, today)
            return if (hint.isBlank()) "$short ${WidgetDateTime.weekdayLabel(day)}" else "$hint $short"
        }

        private fun dayLabelOf(day: LocalDate, today: LocalDate): String =
            "${WidgetDateTime.dayHint(day, today)} ${day.monthValue}/${day.dayOfMonth}"
    }
}
