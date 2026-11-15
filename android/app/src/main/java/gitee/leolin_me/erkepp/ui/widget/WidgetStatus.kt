package gitee.leolin_me.erkepp.ui.widget

import gitee.leolin_me.erkepp.data.eams.EamsSemester
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 画课之前要先判掉的情况。顺序即优先级。 */
enum class WidgetGate {
    READY,
    NO_SEMESTER,
    BEFORE_TERM,
    AFTER_TERM,
    NO_CACHE,
}

/** 空状态或提示：一行标题 + 一句说明。 */
data class WidgetNotice(
    val title: String,
    val note: String? = null,
)

/**
 * 三种小组件共用的「能不能画课」判定。
 *
 * [gitee.leolin_me.erkepp.data.eams.SemesterCalendar] 会把开学前、放假后的日期夹进学期范围里，
 * 直接用它会拿第 1 周的课去填八月的桌面；所以小组件先过这一关，再谈画什么。
 */
object WidgetStatus {

    fun gateOf(semester: EamsSemester?, today: LocalDate, weekCached: Boolean): WidgetGate = when {
        semester == null -> WidgetGate.NO_SEMESTER
        today.isBefore(semester.startDate) -> WidgetGate.BEFORE_TERM
        today.isAfter(semester.endDate) -> WidgetGate.AFTER_TERM
        !weekCached -> WidgetGate.NO_CACHE
        else -> WidgetGate.READY
    }

    fun notice(gate: WidgetGate, semester: EamsSemester?, today: LocalDate): WidgetNotice? = when (gate) {
        WidgetGate.READY -> null
        WidgetGate.NO_SEMESTER -> WidgetNotice(
            title = "还没有课表",
            note = "打开二课++，在「我的 → 我的课表」登录教务",
        )

        WidgetGate.BEFORE_TERM -> {
            val days = semester?.let { ChronoUnit.DAYS.between(today, it.startDate).toInt() } ?: 0
            WidgetNotice(title = "还没开学", note = "距开学还有 $days 天")
        }

        WidgetGate.AFTER_TERM -> WidgetNotice(
            title = "本学期已结束",
            note = "下学期课表出来后，打开课表页刷新一次",
        )

        WidgetGate.NO_CACHE -> WidgetNotice(
            title = "这周的课表还没下载",
            note = "点这里打开课表刷新",
        )
    }
}
