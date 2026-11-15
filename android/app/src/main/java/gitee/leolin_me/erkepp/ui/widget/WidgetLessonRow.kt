package gitee.leolin_me.erkepp.ui.widget

import gitee.leolin_me.erkepp.data.eams.EamsLesson
import gitee.leolin_me.erkepp.data.eams.displayName
import gitee.leolin_me.erkepp.ui.CourseColor
import java.time.LocalDate

/**
 * 小组件里一行课（三种组件共用）：起止时间、课名、教室、圆点颜色。
 *
 * 时间给起止两个：只看起课时间容易误判「这节上到几点」，
 * 而桌面上一行字能装下就装下，装不下再去课表页。
 */
data class WidgetLessonRow(
    /** 起课时间，`08:00`。 */
    val start: String,
    /** 下课时间，`09:40`；服务端没给就空着。 */
    val end: String,
    val name: String,
    val room: String,
    val colorIndex: Int,
    /** 已上完：底色调暗，不隐藏——上午上过什么也是信息。 */
    val past: Boolean = false,
    /** 正在上：底色调亮。 */
    val ongoing: Boolean = false,
) {
    /** 时间列要显示的两行：`08:00` / `09:40`。 */
    val startBlock: String get() = if (end.isBlank()) start else "$start\n$end"

    /** 「08:00–09:40」；只有一头就只写那一头。 */
    val range: String get() = when {
        start.isNotBlank() && end.isNotBlank() -> "$start–$end"
        start.isNotBlank() -> start
        else -> end
    }
}

/**
 * 看板里的课名按「每行最多 4 个字」折行：教务的课名普遍偏长，
 * 16sp 的一行只放得下 5 个字，折成两行比截断成「思想道德与…」好读。
 * 超过两行就用省略号收尾——再多就该去课表页看。
 */
fun boardNameLines(name: String, perLine: Int = 4): String {
    if (name.length <= perLine) return name
    val lines = name.chunked(perLine)
    return if (lines.size <= 2) {
        lines.joinToString("\n")
    } else {
        "${lines[0]}\n${lines[1].take((perLine - 1).coerceAtLeast(1))}…"
    }
}

/** 把一条课变成一行。今天之外的日子没有「上完了」这回事。 */
fun widgetRow(
    lesson: EamsLesson,
    today: LocalDate,
    nowMinute: Int,
    aliases: Map<String, String> = emptyMap(),
): WidgetLessonRow {
    val isToday = lesson.date == today
    return WidgetLessonRow(
        start = lesson.startTime.trim().ifBlank { "—" },
        end = lesson.endTime.trim(),
        name = aliases.displayName(lesson).ifBlank { "未命名课程" },
        room = WidgetDateTime.roomText(lesson),
        colorIndex = CourseColor.indexOf(lesson),
        past = isToday && WidgetDateTime.isPast(lesson, nowMinute),
        ongoing = isToday && WidgetDateTime.isOngoing(lesson, nowMinute),
    )
}
