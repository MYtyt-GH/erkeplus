package gitee.leolin_me.erkepp.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import gitee.leolin_me.erkepp.MainActivity

/**
 * 小组件的点击去向。
 *
 * 只做一件事：带着 `open=timetable` 打开主界面，`MainActivity` 看到这个标记就直接铺开课表页。
 * 课表页返回后落在主界面（发现页）——应用本来就在这一页，不需要额外写代码。
 */
object WidgetIntents {

    const val EXTRA_OPEN = "open"
    const val OPEN_TIMETABLE = "timetable"

    private const val REQUEST_TIMETABLE = 4001

    fun openTimetable(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN, OPEN_TIMETABLE)
            // 应用已经开着时，回到它并重新交付这个 intent，而不是再叠一个 MainActivity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_TIMETABLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
