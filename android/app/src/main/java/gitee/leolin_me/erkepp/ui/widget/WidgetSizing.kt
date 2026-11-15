package gitee.leolin_me.erkepp.ui.widget

/**
 * 桌面小组件的尺寸判断：能放几行、要不要缩成窄档。
 *
 * 系统给的尺寸随手机、格子数、横竖屏变化，用户还能自己拉大拉小；
 * 行高按字号等比放大：系统字号 1.3 时一行就是 1.3 倍高，行数自然变少。
 */
object WidgetSizing {

    /** 组件自己的上下内边距合计（布局里是 12dp + 12dp，最近两节课是 14dp）。 */
    const val VERTICAL_PADDING_DP = 28f

    /** 窄于这个宽度就是 2×2 那一档：藏掉教室，只留时间与课名。 */
    const val COMPACT_WIDTH_DP = 200f

    /** 课程看板窄于这个宽度才藏掉教室列（200dp 左右仍放得下「课名 4 字 + 教室」）。 */
    const val BOARD_COMPACT_WIDTH_DP = 170f

    /** 课程看板顶部（周次 + 日期）占的高度。 */
    const val BOARD_HEADER_DP = 26f

    /** 课程看板一行：课名最多两行（每行 4 字）+ 右侧教室，字放大后行也高一点。 */
    const val BOARD_ROW_DP = 40f

    /** 半天课表的标题行（「上午」+「明天」）。 */
    const val HALFDAY_HEADER_DP = 22f

    /** 半天课表一行：中号字两行。 */
    const val HALFDAY_ROW_DP = 30f

    /** 最近两节课固定两行——它就是为了「只看接下来两节」才存在的。 */
    const val RECENT_ROWS = 2

    const val MAX_ROWS = 8

    /** 课程看板能放几行（先扣掉顶部）。 */
    fun boardRows(heightDp: Float, fontScale: Float): Int {
        val scale = fontScale.coerceIn(1f, 1.6f)
        return rows(heightDp - VERTICAL_PADDING_DP - BOARD_HEADER_DP * scale, BOARD_ROW_DP, scale)
    }

    /** 半天课表能放几行（先扣掉标题）。 */
    fun halfDayRows(heightDp: Float, fontScale: Float): Int {
        val scale = fontScale.coerceIn(1f, 1.6f)
        return rows(heightDp - VERTICAL_PADDING_DP - HALFDAY_HEADER_DP * scale, HALFDAY_ROW_DP, scale)
    }

    /**
     * 宽度为 0 表示系统没报（个别桌面会这样），这时按「不窄」处理：
     * 宁可多显示一截被省略号的教室，也别默认把信息藏掉。
     */
    fun compact(widthDp: Float): Boolean = widthDp > 0f && widthDp < COMPACT_WIDTH_DP

    /** 课程看板：窄了就藏掉教室那一列。 */
    fun compactBoard(widthDp: Float): Boolean = widthDp > 0f && widthDp < BOARD_COMPACT_WIDTH_DP

    private fun rows(usable: Float, rowDp: Float, fontScale: Float): Int {
        if (usable <= 0f) return 1
        val scale = fontScale.coerceIn(1f, 1.6f)
        return (usable / (rowDp * scale)).toInt().coerceIn(1, MAX_ROWS)
    }
}
