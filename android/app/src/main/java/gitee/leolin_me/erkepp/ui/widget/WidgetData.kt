package gitee.leolin_me.erkepp.ui.widget

import android.content.Context
import gitee.leolin_me.erkepp.data.eams.CourseAliases
import gitee.leolin_me.erkepp.data.eams.EamsLesson
import gitee.leolin_me.erkepp.data.eams.EamsSemester
import gitee.leolin_me.erkepp.data.eams.SemesterCalendar
import gitee.leolin_me.erkepp.data.eams.TimetableCache
import java.time.LocalDate
import java.time.LocalTime

/**
 * 小组件能看到的世界：学期、今天、现在第几分钟，以及**已经落在手机里**的课。
 *
 * 只读缓存、不发请求：小组件是被系统在后台随手唤起的东西，网络与执行时机都不可靠
 * （见 [docs/design/widget.md] 与 [docs/design/oem-compat.md]）。
 * 这里同步读文件，是因为数据很小（一周几十门课）且小组件没有生命周期做异步。
 */
data class WidgetSnapshot(
    val semester: EamsSemester?,
    val today: LocalDate,
    val nowMinute: Int,
    val lessons: List<EamsLesson>,
    /** 今天所在那一周的缓存是否存在。缺它就只能显示「这周的课表还没下载」。 */
    val weekCached: Boolean,
    /** 课程别名（课程键 → 别名），有的话优先展示。 */
    val aliases: Map<String, String> = emptyMap(),
) {
    val gate: WidgetGate get() = WidgetStatus.gateOf(semester, today, weekCached)
}

object WidgetData {

    fun load(context: Context, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()): WidgetSnapshot {
        val nowMinute = now.hour * 60 + now.minute
        val cache = TimetableCache(context)
        val semester = cache.loadSemester()
            ?: return WidgetSnapshot(null, today, nowMinute, emptyList(), weekCached = false)

        val week = SemesterCalendar.weekOf(semester, today)
        val thisWeek = cache.load(semester.id, SemesterCalendar.weekStart(semester, week))
        // 顺带读下一周：周日晚上、周一早上要能看到「明天/下周」的课，靠的就是它
        val nextWeek = if (SemesterCalendar.hasWeek(semester, week + 1)) {
            cache.load(semester.id, SemesterCalendar.weekStart(semester, week + 1))
        } else {
            null
        }
        val lessons = (thisWeek?.lessons.orEmpty() + nextWeek?.lessons.orEmpty())
            .sortedWith(compareBy({ it.date }, { it.startUnit }))
        return WidgetSnapshot(
            semester = semester,
            today = today,
            nowMinute = nowMinute,
            lessons = lessons,
            weekCached = thisWeek != null,
            aliases = CourseAliases(context).all(),
        )
    }
}
