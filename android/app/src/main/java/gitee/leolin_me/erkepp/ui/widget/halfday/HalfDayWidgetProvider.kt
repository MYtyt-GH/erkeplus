package gitee.leolin_me.erkepp.ui.widget.halfday

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

/** 半天课表：上午看上午、下午看下午，晚上没课就看第二天。 */
class HalfDayWidgetProvider : BaseWidgetProvider() {

    override fun render(context: Context, appWidgetId: Int, options: Bundle): RemoteViews {
        val snapshot = WidgetData.load(context)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val appearance = WidgetAppearance.of(context)
        val fontScale = context.resources.configuration.fontScale * appearance.effectiveTextScale
        val model = HalfDayModel.build(
            snapshot = snapshot,
            maxRows = WidgetSizing.halfDayRows(heightDp.toFloat(), fontScale),
        )
        return views(context, model, WidgetSizing.compact(widthDp.toFloat()), appearance, widthDp, heightDp)
    }

    private fun views(
        context: Context,
        model: HalfDayModel,
        compact: Boolean,
        appearance: WidgetAppearance,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_halfday)
        WidgetViews.decorate(context, views, appearance, widthDp, heightDp, WidgetText.halfDayWidget)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openTimetable(context))
        views.removeAllViews(R.id.halfday_rows)

        val notice = model.notice
        if (notice != null) {
            views.setViewVisibility(R.id.halfday_title, View.GONE)
            views.setViewVisibility(R.id.halfday_day, View.GONE)
            views.setViewVisibility(R.id.halfday_hidden, View.GONE)
            views.setViewVisibility(R.id.halfday_empty, View.VISIBLE)
            views.setTextViewText(R.id.halfday_empty, WidgetViews.noticeText(notice))
            return views
        }

        views.setViewVisibility(R.id.halfday_empty, View.GONE)
        views.setViewVisibility(R.id.halfday_title, View.VISIBLE)
        views.setTextViewText(R.id.halfday_title, model.title)
        views.setViewVisibility(R.id.halfday_day, if (model.dayLabel.isBlank()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.halfday_day, model.dayLabel)
        model.rows.forEach { row ->
            views.addView(
                R.id.halfday_rows,
                WidgetViews.row(
                    context = context,
                    row = row,
                    layoutRes = R.layout.widget_course_row_medium,
                    showRoom = !compact,
                    appearance = appearance,
                    texts = WidgetText.timelineRowMedium,
                ),
            )
        }
        views.setViewVisibility(R.id.halfday_hidden, if (model.footer != null) View.VISIBLE else View.GONE)
        model.footer?.let { views.setTextViewText(R.id.halfday_hidden, it) }
        return views
    }
}
