package gitee.leolin_me.erkepp.ui.widget.recent

import gitee.leolin_me.erkepp.ui.widget.WidgetDateTime
import gitee.leolin_me.erkepp.ui.widget.WidgetGate
import gitee.leolin_me.erkepp.ui.widget.WidgetLessonRow
import gitee.leolin_me.erkepp.ui.widget.WidgetNotice
import gitee.leolin_me.erkepp.ui.widget.WidgetSizing
import gitee.leolin_me.erkepp.ui.widget.WidgetSnapshot
import gitee.leolin_me.erkepp.ui.widget.WidgetStatus
import gitee.leolin_me.erkepp.ui.widget.widgetRow

/**
 * 最近两节课要摆的字：只看接下来最近的两节（正在上的算第一），字比别的大一档。
 *
 * 跨天也算：晚上全上完了，它就变成「明天 08:00 高等数学」。
 */
data class RecentModel(
    /** 「正在上」或「接下来」。 */
    val label: String,
    val rows: List<WidgetLessonRow>,
    val notice: WidgetNotice?,
) {
    companion object {

        fun build(snapshot: WidgetSnapshot): RecentModel {
            if (snapshot.gate != WidgetGate.READY) {
                return RecentModel(
                    label = "",
                    rows = emptyList(),
                    notice = WidgetStatus.notice(snapshot.gate, snapshot.semester, snapshot.today),
                )
            }

            val upcoming = snapshot.lessons
                .filter { lesson ->
                    lesson.date.isAfter(snapshot.today) ||
                        (lesson.date == snapshot.today && !WidgetDateTime.isPast(lesson, snapshot.nowMinute))
                }
                .sortedWith(compareBy({ it.date }, { it.startUnit }))
                .take(WidgetSizing.RECENT_ROWS)

            if (upcoming.isEmpty()) {
                return RecentModel(
                    label = "",
                    rows = emptyList(),
                    notice = WidgetNotice("接下来没有课", "这周剩下的时间没课"),
                )
            }

            val label = if (WidgetDateTime.isOngoing(upcoming.first(), snapshot.nowMinute)) "正在上" else "接下来"
            val rows = upcoming.map { lesson ->
                val row = widgetRow(lesson, snapshot.today, snapshot.nowMinute, snapshot.aliases)
                if (lesson.date == snapshot.today) {
                    row
                } else {
                    // 时间列只有 48dp 宽，跨天时让「明天」和钟点分两行，不被截断
                    row.copy(start = "${WidgetDateTime.dayHint(lesson.date, snapshot.today)} ${row.start}")
                }
            }
            return RecentModel(label = label, rows = rows, notice = null)
        }
    }
}
