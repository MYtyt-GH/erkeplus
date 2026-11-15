package gitee.leolin_me.erkepp.ui.widget.board

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

/** 课程看板：从「现在」往后的连续课表——时间 / 课名（大字）/ 教室，跨天插一行日期。 */
class BoardWidgetProvider : BaseWidgetProvider() {

    override fun render(context: Context, appWidgetId: Int, options: Bundle): RemoteViews {
        val snapshot = WidgetData.load(context)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val heightDp = maxOf(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
        )
        val appearance = WidgetAppearance.of(context)
        val fontScale = context.resources.configuration.fontScale * appearance.effectiveTextScale
        val compact = WidgetSizing.compactBoard(widthDp.toFloat())
        val model = BoardModel.build(
            snapshot = snapshot,
            maxRows = WidgetSizing.boardRows(heightDp.toFloat(), fontScale),
        )
        return views(context, model, compact, appearance, widthDp, heightDp)
    }

    private fun views(
        context: Context,
        model: BoardModel,
        compact: Boolean,
        appearance: WidgetAppearance,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_board)
        WidgetViews.decorate(context, views, appearance, widthDp, heightDp, WidgetText.boardWidget)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.openTimetable(context))

        views.setTextViewText(R.id.board_week, model.weekLabel)
        views.setViewVisibility(R.id.board_week, if (model.weekLabel.isBlank()) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.board_today, model.dateLabel)
        val footer = model.footer
        views.setViewVisibility(R.id.board_footer, if (footer != null) View.VISIBLE else View.GONE)
        if (footer != null) views.setTextViewText(R.id.board_footer, footer)
        views.removeAllViews(R.id.board_rows)

        val notice = model.notice
        if (notice != null) {
            views.setViewVisibility(R.id.board_empty, View.VISIBLE)
            views.setTextViewText(R.id.board_empty, WidgetViews.noticeText(notice))
            return views
        }

        views.setViewVisibility(R.id.board_empty, View.GONE)
        model.blocks.forEach { block ->
            when (block) {
                is BoardBlock.Day -> views.addView(
                    R.id.board_rows,
                    WidgetViews.boardDayMarker(context, block.label, appearance),
                )

                is BoardBlock.Course -> views.addView(
                    R.id.board_rows,
                    WidgetViews.boardRow(context, block.row, !compact, appearance),
                )
            }
        }
        return views
    }
}
