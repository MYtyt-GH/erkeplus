package gitee.leolin_me.erkepp.ui.timetable

/**
 * 课表的尺寸决策：给定屏幕与字号，算出列宽、节次栏宽、行高和「用哪种信息密度」。
 *
 * 抽成纯函数是为了能真的验：用户手机尺寸千差万别（还有横屏、平板、系统字号 0.85–2.0），
 * 靠一台机器截图看不出问题，所以这张矩阵写在 `TimetableMetricsTest` 里。
 *
 * 规则（2026-11-15 定）：
 * - 列宽 = 屏宽 / 7，夹到 96–168dp：宽屏 7 天看全，窄屏横向滑；
 * - 行高 = (可用高 − 表头) / 每天节数；
 * - 三行字（课程名/楼栋/房间号）放得下 → [Mode.FULL]；只放得下两行 → [Mode.DENSE]；
 *   两行都放不下 → [Mode.SCROLL]，行高给下限、整块竖向滚动。**宁可滚动，不挤字。**
 * - 所有行高阈值都乘系统字体缩放。
 */
enum class TimetableMode {
    /** 课程名 + 楼栋 + 房间号，三行。 */
    FULL,

    /** 课程名 + 教室，两行（教室名自带楼宇前缀）。 */
    DENSE,

    /** 两行都放不下：行高取下限，整块竖向滚动。 */
    SCROLL,
}

data class TimetableMetrics(
    /** 每一天一列的宽度。 */
    val columnWidthDp: Float,
    /** 左侧节次栏宽度。 */
    val gutterWidthDp: Float,
    /** 每一节（行）的高度。 */
    val rowHeightDp: Float,
    val mode: TimetableMode,
    /** 一屏能完整看到几天，用于决定要不要提示「左右滑动」。 */
    val visibleDays: Float,
) {
    val dense: Boolean get() = mode != TimetableMode.FULL
}

object TimetableLayout {

    /** 表头高度，和 TimetableGrid 里的数值保持一致。 */
    const val HEADER_DP = 24f

    private const val MIN_COLUMN_DP = 88f
    private const val MAX_COLUMN_DP = 168f
    private const val MIN_GUTTER_DP = 44f
    private const val MAX_GUTTER_DP = 58f

    /** 三行文字大约需要的高度（1 倍字号下）。 */
    private const val FULL_ROW_DP = 46f

    /** 两行文字大约需要的高度（1 倍字号下）。 */
    private const val DENSE_ROW_DP = 32f

    fun of(
        availableWidthDp: Float,
        availableHeightDp: Float,
        maxUnit: Int,
        fontScale: Float,
    ): TimetableMetrics {
        val safeFontScale = fontScale.coerceIn(0.5f, 3f)
        val units = maxUnit.coerceAtLeast(1)
        val gutter = (availableWidthDp * 0.13f).coerceIn(MIN_GUTTER_DP, MAX_GUTTER_DP)
        // 列宽按**扣掉节次栏之后**的宽度算，否则宽屏上第 7 天会被切掉一条
        val gridWidth = (availableWidthDp - gutter).coerceAtLeast(1f)
        val column = (gridWidth / 7f).coerceIn(MIN_COLUMN_DP, MAX_COLUMN_DP)
        val naturalRow = ((availableHeightDp - HEADER_DP) / units).coerceAtLeast(1f)
        val fullRow = FULL_ROW_DP * safeFontScale
        val denseRow = DENSE_ROW_DP * safeFontScale

        val mode = when {
            naturalRow >= fullRow -> TimetableMode.FULL
            naturalRow >= denseRow -> TimetableMode.DENSE
            else -> TimetableMode.SCROLL
        }
        val rowHeight = if (mode == TimetableMode.SCROLL) denseRow else naturalRow
        return TimetableMetrics(
            columnWidthDp = column,
            gutterWidthDp = gutter,
            rowHeightDp = rowHeight,
            mode = mode,
            visibleDays = gridWidth / column,
        )
    }
}
