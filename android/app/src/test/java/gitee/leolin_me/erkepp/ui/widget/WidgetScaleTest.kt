package gitee.leolin_me.erkepp.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三档文字大小必须真的「小 < 标准 < 大」。
 *
 * 这就是以前那个 bug 的守卫：标准档曾经靠「等于 1 就跳过、用布局 XML 的字号」，
 * 而大小档用 [WidgetText] 里的基准表，两边漂移之后「大」会比「标准」还小，
 * 桌面上的表现就是点「大」跟没点一样。现在只有一份基准，标准档也显式写一遍。
 */
class WidgetScaleTest {

    private val small = WidgetAppearance(
        backgroundColor = null,
        backgroundAlpha = 100,
        textColor = null,
        textScale = WidgetAppearance.TEXT_SCALE_SMALL,
    ).effectiveTextScale

    private val normal = WidgetAppearance(
        backgroundColor = null,
        backgroundAlpha = 100,
        textColor = null,
        textScale = WidgetAppearance.TEXT_SCALE_NORMAL,
    ).effectiveTextScale

    private val large = WidgetAppearance(
        backgroundColor = null,
        backgroundAlpha = 100,
        textColor = null,
        textScale = WidgetAppearance.TEXT_SCALE_LARGE,
    ).effectiveTextScale

    /** 三种组件里用到的所有文本，一个都不漏。 */
    private val allSpecs: List<WidgetTextSpec> = listOf(
        WidgetText.boardWidget,
        WidgetText.boardDayMarker,
        WidgetText.boardRow,
        WidgetText.halfDayWidget,
        WidgetText.timelineRowMedium,
        WidgetText.recentWidget,
        WidgetText.recentRow,
    ).flatten()

    @Test
    fun `scale presets are ordered`() {
        assertEquals(0.9f, small, 0.0001f)
        assertEquals(1.0f, normal, 0.0001f)
        assertEquals(1.35f, large, 0.0001f)
        assertTrue("大 必须比 标准 大", large > normal)
        assertTrue("标准 必须比 小 大", normal > small)
    }

    @Test
    fun `standard keeps the base size unchanged`() {
        // 标准档 = 基准值本身，也就是用户现在看到的那套字号
        allSpecs.forEach { spec ->
            assertEquals(spec.baseSp, WidgetText.scaledSp(spec.baseSp, normal), 0.0001f)
        }
    }

    @Test
    fun `every text grows from small to large`() {
        allSpecs.forEach { spec ->
            val s = WidgetText.scaledSp(spec.baseSp, small)
            val n = WidgetText.scaledSp(spec.baseSp, normal)
            val l = WidgetText.scaledSp(spec.baseSp, large)
            assertTrue("小 < 标准 失败：${spec.baseSp}", s < n)
            assertTrue("标准 < 大 失败：${spec.baseSp}", n < l)
            // 差不出来就等于没调：最小的 10sp 也要差 3sp 以上
            assertTrue("大 与 标准 的差太小：${spec.baseSp}", l - n >= 3f)
        }
    }

    @Test
    fun `out of range values are clamped instead of exploding`() {
        fun scaleOf(percent: Int) = WidgetAppearance(
            backgroundColor = null,
            backgroundAlpha = 100,
            textColor = null,
            textScale = percent,
        ).effectiveTextScale

        // 老版本可能存过 115 之类的值，也认；过小的按 80%、过大的按 150%
        assertEquals(1.15f, scaleOf(115), 0.0001f)
        assertEquals(0.8f, scaleOf(1), 0.0001f)
        assertEquals(1.5f, scaleOf(999), 0.0001f)
    }
}
