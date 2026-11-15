package gitee.leolin_me.erkepp.ui.widget

import android.util.TypedValue
import android.widget.RemoteViews
import gitee.leolin_me.erkepp.R

/**
 * 每个文本控件在布局里的基准字号，以及它算「主文字」还是「次要文字」。
 *
 * 用户把文字调大/调小、把文字颜色换成自定义色时，就靠这张表逐个改——
 * RemoteViews 没有「整体缩放」这种能力。
 */
data class WidgetTextSpec(
    val id: Int,
    val baseSp: Float,
    val primary: Boolean,
)

object WidgetText {

    /** 课程看板自身的文本。 */
    val boardWidget = listOf(
        WidgetTextSpec(R.id.board_week, 13f, primary = true),
        WidgetTextSpec(R.id.board_today, 12f, primary = false),
        WidgetTextSpec(R.id.board_footer, 11f, primary = false),
        WidgetTextSpec(R.id.board_empty, 12f, primary = false),
    )

    /** 看板里「换了一天」那一行。 */
    val boardDayMarker = listOf(
        WidgetTextSpec(R.id.board_day_marker, 11f, primary = false),
    )

    /** 课程看板一行：时间 + 课名（大字）+ 教室。 */
    val boardRow = listOf(
        WidgetTextSpec(R.id.row_time, 12f, primary = false),
        WidgetTextSpec(R.id.row_name, 17f, primary = true),
        WidgetTextSpec(R.id.row_room, 14f, primary = false),
    )

    val halfDayWidget = listOf(
        WidgetTextSpec(R.id.halfday_title, 15f, primary = true),
        WidgetTextSpec(R.id.halfday_day, 11f, primary = true),
        WidgetTextSpec(R.id.halfday_hidden, 10f, primary = false),
        WidgetTextSpec(R.id.halfday_empty, 13f, primary = false),
    )

    val recentWidget = listOf(
        WidgetTextSpec(R.id.recent_label, 11f, primary = true),
        WidgetTextSpec(R.id.recent_empty, 13f, primary = false),
    )

    /** 半天课表的一行：课名 + 「起止时间 · 教室」。 */
    val timelineRowMedium = listOf(
        WidgetTextSpec(R.id.row_name, 15f, primary = true),
        WidgetTextSpec(R.id.row_meta, 12f, primary = false),
    )

    /** 最近两节课的行（课名 + 「时间 · 教室」）。 */
    val recentRow = listOf(
        WidgetTextSpec(R.id.row_name, 18f, primary = true),
        WidgetTextSpec(R.id.row_meta, 14f, primary = false),
    )

    fun applySizes(views: RemoteViews, specs: List<WidgetTextSpec>, scale: Float) {
        if (scale == 1f) return
        specs.forEach { spec ->
            views.setTextViewTextSize(spec.id, TypedValue.COMPLEX_UNIT_SP, spec.baseSp * scale)
        }
    }

    fun applyColors(views: RemoteViews, specs: List<WidgetTextSpec>, appearance: WidgetAppearance) {
        val primary = appearance.resolvedTextColor() ?: return
        val secondary = appearance.resolvedSecondaryTextColor() ?: primary
        specs.forEach { spec ->
            views.setTextColor(spec.id, if (spec.primary) primary else secondary)
        }
    }
}
