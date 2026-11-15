package gitee.leolin_me.erkepp.ui.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.ui.CourseColor

/**
 * 三种小组件共用的画法：背景、文字配色与字号、一行课、整块点击。
 *
 * 背景是一张运行时画的圆角位图（RemoteViews 没法给任意颜色的圆角图形），
 * 文字颜色与字号按用户在「更多 → 桌面小组件」里的设置逐个改；
 * 没设置的项一律留给布局里的 `values{,-night}` 资源，跟随系统深浅色。
 */
object WidgetViews {

    /** 套用外观：背景位图 + 文字字号 + 文字颜色。 */
    fun decorate(
        context: Context,
        views: RemoteViews,
        appearance: WidgetAppearance,
        widthDp: Int,
        heightDp: Int,
        texts: List<WidgetTextSpec>,
    ) {
        views.setImageViewBitmap(R.id.widget_bg, WidgetBackground.bitmap(context, appearance, widthDp, heightDp))
        WidgetText.applySizes(views, texts, appearance.effectiveTextScale)
        WidgetText.applyColors(views, texts, appearance)
    }

    /**
     * 半天课表的一行：圆点 + 课名 + 「起止时间 · 教室」。
     *
     * 这个组件的格子通常只有 4×2，三行排不下两节课，所以压成两行。
     */
    fun row(
        context: Context,
        row: WidgetLessonRow,
        layoutRes: Int,
        showRoom: Boolean,
        appearance: WidgetAppearance,
        texts: List<WidgetTextSpec>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        views.setTextViewText(R.id.row_name, row.name)
        val meta = if (showRoom && row.room.isNotBlank()) "${row.range} · ${row.room}" else row.range
        views.setTextViewText(R.id.row_meta, meta)
        // 状态只改底图、不改文字颜色：文字颜色留在 XML 里由 values-night 决定，
        // 这样用户切换深色模式后重绘时颜色自动跟着变（绝对色值会卡在旧主题上）。
        views.setInt(
            R.id.row_root,
            "setBackgroundResource",
            when {
                row.ongoing -> R.drawable.widget_row_now
                row.past -> R.drawable.widget_row_past
                else -> android.R.color.transparent
            },
        )
        views.setInt(R.id.row_dot, "setBackgroundResource", CourseColor.dotRes(row.colorIndex))
        views.setOnClickPendingIntent(R.id.row_root, WidgetIntents.openTimetable(context))
        WidgetText.applySizes(views, texts, appearance.effectiveTextScale)
        WidgetText.applyColors(views, texts, appearance)
        return views
    }

    /**
     * 最近两节课的一行：圆点 + 课名 + 「时间 · 教室」（窄档位只留时间）。
     *
     * 它没有单独的时间列：2×2 的格子里再切一列给时间，课名就只剩「中国…」了。
     */
    fun recentRow(
        context: Context,
        row: WidgetLessonRow,
        showRoom: Boolean,
        layoutRes: Int,
        appearance: WidgetAppearance,
        texts: List<WidgetTextSpec>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        views.setTextViewText(R.id.row_name, row.name)
        val meta = if (showRoom && row.room.isNotBlank()) "${row.start} · ${row.room}" else row.start
        views.setTextViewText(R.id.row_meta, meta)
        views.setInt(
            R.id.row_root,
            "setBackgroundResource",
            when {
                row.ongoing -> R.drawable.widget_row_now
                row.past -> R.drawable.widget_row_past
                else -> android.R.color.transparent
            },
        )
        views.setInt(R.id.row_dot, "setBackgroundResource", CourseColor.dotRes(row.colorIndex))
        views.setOnClickPendingIntent(R.id.row_root, WidgetIntents.openTimetable(context))
        WidgetText.applySizes(views, texts, appearance.effectiveTextScale)
        WidgetText.applyColors(views, texts, appearance)
        return views
    }

    /**
     * 课程看板的一行：圆点 + 时间 + 课名（大字）+ 教室。
     *
     * 「一列课表、一列教室」：课名拿最大宽度，教室靠右成一列；窄档位藏掉教室那一列。
     */
    fun boardRow(
        context: Context,
        row: WidgetLessonRow,
        showRoom: Boolean,
        appearance: WidgetAppearance,
    ): RemoteViews {
        // 课名最多两行（每行 4 字），右边留给教室列；窄到 170dp 以下才藏教室
        val views = RemoteViews(context.packageName, R.layout.widget_board_row)
        views.setTextViewText(R.id.row_time, row.startBlock)
        views.setTextViewText(R.id.row_name, boardNameLines(row.name))
        val showRoomText = showRoom && row.room.isNotBlank()
        views.setViewVisibility(R.id.row_room, if (showRoomText) View.VISIBLE else View.GONE)
        if (showRoomText) views.setTextViewText(R.id.row_room, row.room)
        views.setInt(
            R.id.row_root,
            "setBackgroundResource",
            when {
                row.ongoing -> R.drawable.widget_row_now
                row.past -> R.drawable.widget_row_past
                else -> android.R.color.transparent
            },
        )
        views.setInt(R.id.row_dot, "setBackgroundResource", CourseColor.dotRes(row.colorIndex))
        views.setOnClickPendingIntent(R.id.row_root, WidgetIntents.openTimetable(context))
        WidgetText.applySizes(views, WidgetText.boardRow, appearance.effectiveTextScale)
        WidgetText.applyColors(views, WidgetText.boardRow, appearance)
        return views
    }

    /** 看板里「换了一天」那一行。 */
    fun boardDayMarker(context: Context, label: String, appearance: WidgetAppearance): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_board_day)
        views.setTextViewText(R.id.board_day_marker, label)
        WidgetText.applySizes(views, WidgetText.boardDayMarker, appearance.effectiveTextScale)
        WidgetText.applyColors(views, WidgetText.boardDayMarker, appearance)
        views.setOnClickPendingIntent(R.id.board_day_marker, WidgetIntents.openTimetable(context))
        return views
    }
    /** 空状态文案：标题 + 说明合成一段，标题在前。 */
    fun noticeText(notice: WidgetNotice): String =
        if (notice.note.isNullOrBlank()) notice.title else "${notice.title}\n${notice.note}"
}
