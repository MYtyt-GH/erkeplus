package gitee.leolin_me.erkepp.ui.widget.recent

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.ui.widget.BaseWidgetProvider
import gitee.leolin_me.erkepp.ui.widget.WidgetAppearance
import gitee.leolin_me.erkepp.ui.widget.WidgetData
import gitee.leolin_me.erkepp.ui.widget.WidgetIntents
import gitee.leolin_me.erkepp.ui.widget.WidgetSizing
import gitee.leolin_me.erkepp.ui.widget.WidgetText
import gitee.leolin_me.erkepp.ui.widget.WidgetViews

/** 最近两节课：只看接下来最近的两节，字大。 */
class RecentWidgetProvider : BaseWidgetProvider() {

    override fun render(context: Context, appWidgetId: Int, options: Bundle): RemoteViews {
        val snapshot = WidgetData.load(context)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val appearance = WidgetAppearance.of(context)
        val model = RecentModel.build(snapshot)

        val views = RemoteViews(context.packageName, R.layout.widget_recent)
        WidgetViews.decorate(context, views, appearance, widthDp, heightDp, WidgetText.recentWidget)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openTimetable(context))
        views.removeAllViews(R.id.recent_rows)

        val notice = model.notice
        if (notice != null) {
            views.setViewVisibility(R.id.recent_label, View.GONE)
            views.setViewVisibility(R.id.recent_empty, View.VISIBLE)
            views.setTextViewText(R.id.recent_empty, WidgetViews.noticeText(notice))
            return views
        }

        views.setViewVisibility(R.id.recent_label, View.VISIBLE)
        views.setTextViewText(R.id.recent_label, model.label)
        views.setViewVisibility(R.id.recent_empty, View.GONE)
        val showRoom = !WidgetSizing.compact(widthDp.toFloat())
        model.rows.forEach { row ->
            views.addView(
                R.id.recent_rows,
                WidgetViews.recentRow(
                    context = context,
                    row = row,
                    showRoom = showRoom,
                    layoutRes = R.layout.widget_course_row_large,
                    appearance = appearance,
                    texts = WidgetText.recentRow,
                ),
            )
        }
        return views
    }
}
