package gitee.leolin_me.erkepp.ui.widget

import android.util.TypedValue
import android.widget.RemoteViews
import gitee.leolin_me.erkepp.R

/**
 * 每个文本控件在布局里的基准字号，以及它算「主文字」还是「次要文字」。
 *
 * 这是**唯一一份**字号来源：布局 XML 里的 `textSize` 只是「不套外观时」的兜底，
 * 套了外观（标准档也算）就按这张表写一遍。两边一旦漂移，就会出现「标准档比大小档还大」
 * 这种没法解释的现象——以前这里就漏过一次（顶部周次表里 13sp、布局里 12sp）。
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
        WidgetTextSpec(R.id.board_week, 12f, primary = true),
        WidgetTextSpec(R.id.board_today, 11f, primary = false),
        WidgetTextSpec(R.id.board_footer, 10f, primary = false),
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
        // 标准档（1.0）也要显式写一遍，不再「等于 1 就跳过」：
        // 跳过时标准档用的是 XML 里的字号，而大小档用的是这张表，两套基准没法保证一致
        specs.forEach { spec ->
            views.setTextViewTextSize(spec.id, TypedValue.COMPLEX_UNIT_SP, scaledSp(spec.baseSp, scale))
        }
    }

    /**
     * 基准字号 × 档位比例，取到 0.1sp。
     *
     * 抽成纯函数是为了能直接单测「小 < 标准 < 大」——这个顺序以前没有任何测试守着。
     */
    fun scaledSp(baseSp: Float, scale: Float): Float = Math.round(baseSp * scale * 10f) / 10f

    fun applyColors(views: RemoteViews, specs: List<WidgetTextSpec>, appearance: WidgetAppearance) {
        val primary = appearance.resolvedTextColor() ?: return
        val secondary = appearance.resolvedSecondaryTextColor() ?: primary
        specs.forEach { spec ->
            views.setTextColor(spec.id, if (spec.primary) primary else secondary)
        }
    }
}
