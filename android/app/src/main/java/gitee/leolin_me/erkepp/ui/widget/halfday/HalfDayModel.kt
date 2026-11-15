package gitee.leolin_me.erkepp.ui.widget.halfday

import gitee.leolin_me.erkepp.ui.widget.DayStage
import gitee.leolin_me.erkepp.ui.widget.WidgetDateTime
import gitee.leolin_me.erkepp.ui.widget.WidgetGate
import gitee.leolin_me.erkepp.ui.widget.WidgetLessonRow
import gitee.leolin_me.erkepp.ui.widget.WidgetNotice
import gitee.leolin_me.erkepp.ui.widget.WidgetSnapshot
import gitee.leolin_me.erkepp.ui.widget.WidgetStatus
import gitee.leolin_me.erkepp.ui.widget.widgetRow
import java.time.LocalDate

/**
 * 半天课表要摆的字：只画当前阶段（上午 / 下午 / 晚上）的课。
 *
 * 取哪个阶段的规则（用户 2026-11-15 定）：当前阶段有课就用当前阶段，没有就看下一阶段，
 * 依次往后；都没有就写「暂无半天课表」。
 *
 * 「有课」收紧成「还有没上完的课」：11:50 时上午的课已经结束，再画上午就等于把桌面停在过去；
 * 收紧后直接跳到下午，与看板、最近两节的「往后看」一致。
 */
data class HalfDayModel(
    /** 「上午」「下午」「晚上」。 */
    val title: String,
    /** 「明天」「周一」；今天为空。 */
    val dayLabel: String,
    val rows: List<WidgetLessonRow>,
    val footer: String?,
    val notice: WidgetNotice?,
) {
    companion object {

        /** 往后最多找 7 天，够跨一个周末；再远就是「暂无半天课表」。 */
        private const val LOOKAHEAD_DAYS = 7

        fun build(snapshot: WidgetSnapshot, maxRows: Int): HalfDayModel {
            if (snapshot.gate != WidgetGate.READY) {
                return HalfDayModel(
                    title = "",
                    dayLabel = "",
                    rows = emptyList(),
                    footer = null,
                    notice = WidgetStatus.notice(snapshot.gate, snapshot.semester, snapshot.today),
                )
            }

            val hit = firstStageWithLessons(snapshot)
                ?: return HalfDayModel("", "", emptyList(), null, WidgetNotice("暂无半天课表"))

            val (day, stage) = hit
            val items = snapshot.lessons
                .filter { it.date == day && WidgetDateTime.stageOf(it) == stage }
                .sortedBy { it.startUnit }
            val cap = if (items.size > maxRows && maxRows > 1) maxRows - 1 else maxRows
            val visible = items.take(cap)
            val hidden = (items.size - cap).coerceAtLeast(0)

            return HalfDayModel(
                title = stage.label,
                dayLabel = WidgetDateTime.dayHint(day, snapshot.today),
                rows = visible.map { widgetRow(it, snapshot.today, snapshot.nowMinute, snapshot.aliases) },
                footer = if (hidden > 0) "还有 $hidden 节" else null,
                notice = null,
            )
        }

        private fun firstStageWithLessons(snapshot: WidgetSnapshot): Pair<LocalDate, DayStage>? {
            val startStage = WidgetDateTime.stageAtMinute(snapshot.nowMinute)
            for (offset in 0 until LOOKAHEAD_DAYS) {
                val day = snapshot.today.plusDays(offset.toLong())
                val from = if (offset == 0) startStage.ordinal else 0
                for (ordinal in from..DayStage.entries.lastIndex) {
                    val stage = DayStage.entries[ordinal]
                    val items = snapshot.lessons.filter { it.date == day && WidgetDateTime.stageOf(it) == stage }
                    if (items.isEmpty()) continue
                    // 今天：这个阶段必须还有没上完的课，否则往后顺延
                    if (day == snapshot.today && items.none { !WidgetDateTime.isPast(it, snapshot.nowMinute) }) {
                        continue
                    }
                    return day to stage
                }
            }
            return null
        }
    }
}
