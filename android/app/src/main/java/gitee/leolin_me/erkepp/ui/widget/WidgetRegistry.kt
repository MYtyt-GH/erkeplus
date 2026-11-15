package gitee.leolin_me.erkepp.ui.widget

import gitee.leolin_me.erkepp.ui.widget.board.BoardWidgetProvider
import gitee.leolin_me.erkepp.ui.widget.halfday.HalfDayWidgetProvider
import gitee.leolin_me.erkepp.ui.widget.recent.RecentWidgetProvider

/** 一个可放到桌面上的组件。设置页的「添加到桌面」和刷新入口都读这张表。 */
data class WidgetEntry(
    val label: String,
    val description: String,
    val provider: Class<out BaseWidgetProvider>,
)

/**
 * 组件清单。加一类组件时：写纯 Model、写布局与 Renderer、继承 [BaseWidgetProvider]，
 * 然后来这里登记一行——设置页与刷新入口会自动带上它。
 *
 * 活动待办、活动看板将来也登记在这里，等它们的数据（消息、活动缓存）落地之后。
 */
object WidgetRegistry {

    val all: List<WidgetEntry> = listOf(
        WidgetEntry(
            label = "课程看板",
            description = "从今天往后连着列，时间、课名、教室都在",
            provider = BoardWidgetProvider::class.java,
        ),
        WidgetEntry(
            label = "最近两节课",
            description = "只看接下来最近的两节，字最大",
            provider = RecentWidgetProvider::class.java,
        ),
        WidgetEntry(
            label = "半天课表",
            description = "上午看上午、下午看下午，晚上没课就看第二天",
            provider = HalfDayWidgetProvider::class.java,
        ),
    )
}
