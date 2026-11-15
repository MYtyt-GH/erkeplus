package gitee.leolin_me.erkepp.ui.widget

import android.content.Context
import android.content.Intent

/**
 * 数据变了，主动推一次桌面。
 *
 * 不做「等系统定时刷新」：`updatePeriodMillis` 下限 30 分钟且不保证准点（见 oem-compat）。
 * 课表页刚刷新完、外观刚改完这类时刻，桌面必须立刻跟着变。
 */
object WidgetRefresh {

    const val ACTION_REFRESH = "gitee.leolin_me.erkepp.action.WIDGET_REFRESH"

    /** 课表缓存变了：三种组件都读课表，一起推。 */
    fun timetable(context: Context) = send(context, WidgetRegistry.all)

    /** 外观等全局设置变了。 */
    fun all(context: Context) = send(context, WidgetRegistry.all)

    private fun send(context: Context, entries: List<WidgetEntry>) {
        entries.forEach { entry ->
            // 没放到桌面上时没人接这条广播，代价为 0
            runCatching {
                context.sendBroadcast(Intent(ACTION_REFRESH).setClass(context, entry.provider))
            }
        }
    }
}
