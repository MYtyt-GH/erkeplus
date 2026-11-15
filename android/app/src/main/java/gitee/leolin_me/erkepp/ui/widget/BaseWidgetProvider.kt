package gitee.leolin_me.erkepp.ui.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews

/**
 * 三种小组件共同的壳：什么时候重画、画坏了怎么办。
 *
 * 子类只回答一个问题：**给我一个 widgetId 和它的尺寸，画出它的 RemoteViews**。
 * 刷新时机（放置、改尺寸、跨零点、改时间/时区/语言、数据变化）全在这里统一处理，
 * 免得每加一种组件就要重新踩一遍广播的坑。
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    /**
     * 画出这个组件。`options` 来自系统的 `getAppWidgetOptions`（宽度、高度等）。
     *
     * 故意是 public 的：调试用的预览页（`src/debug` 里的 `WidgetPreviewActivity`）要直接调它，
     * 才能在没有桌面的情况下按同一段代码渲染一遍。
     */
    abstract fun render(context: Context, appWidgetId: Int, options: Bundle): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        draw(context, manager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        draw(context, manager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val needsRepaint = action == WidgetRefresh.ACTION_REFRESH ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_LOCALE_CHANGED
        if (!needsRepaint) return
        val manager = AppWidgetManager.getInstance(context)
        draw(context, manager, manager.getAppWidgetIds(ComponentName(context, this::class.java)))
    }

    private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            gitee.leolin_me.erkepp.AppLog.d(
                "widget",
                "${this::class.java.simpleName} id=$id " +
                    "min=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}x" +
                    "${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)} " +
                    "max=${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)}x" +
                    "${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)}",
            )
            // 单个组件渲染失败不能连累其它组件：宁可不更新，也别把桌面上的东西弄没
            val views = runCatching {
                render(context, id, options)
            }.getOrNull() ?: return@forEach
            manager.updateAppWidget(id, views)
        }
    }
}
