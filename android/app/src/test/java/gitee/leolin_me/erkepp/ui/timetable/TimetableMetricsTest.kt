package gitee.leolin_me.erkepp.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表在「不同手机、不同字号」下的尺寸决策。
 *
 * 这一组用例就是用户说的那句「我的用户不会只有一种屏幕」：把常见机型和字号排成矩阵，
 * 断言每种情况下都给出**可读**的布局——要么一屏装满，要么明确进入滚动模式，
 * 绝不允许出现「行高被压到放不下字」却又不让滚的情况。
 *
 * 宽度/高度都是 dp（可用区域，已扣掉系统栏与页面自己的头部）。
 */
class TimetableMetricsTest {

    private fun metrics(
        width: Float,
        height: Float,
        fontScale: Float = 1f,
        maxUnit: Int = 12,
    ) = TimetableLayout.of(width, height, maxUnit, fontScale)

    @Test
    fun `常见手机竖屏 一屏装满且列宽有下限`() {
        // 360x780 是主流尺寸；高度足够，12 节应该一屏放下
        val m = metrics(360f, 700f)
        assertEquals(TimetableMode.FULL, m.mode)
        assertEquals(96f, m.columnWidthDp, 0.01f)
        assertTrue("窄屏一屏看不到 7 天，应该能横滑", m.visibleDays < 7f)
        assertTrue("列宽不能小于 96dp，否则课程名挤成一列字", m.columnWidthDp >= 96f)
    }

    @Test
    fun `小屏 320dp 宽`() {
        val m = metrics(320f, 600f)
        assertEquals(96f, m.columnWidthDp, 0.01f)
        assertTrue(m.visibleDays > 3f)
        assertTrue(m.mode == TimetableMode.FULL || m.mode == TimetableMode.DENSE)
    }

    @Test
    fun `宽屏与平板 七天一次看全`() {
        // 800dp 宽：800/7 = 114dp，在 96..168 之间，7 天正好铺满
        val m = metrics(800f, 1100f)
        assertEquals(7f, m.visibleDays, 0.01f)
        assertEquals(800f / 7f, m.columnWidthDp, 0.01f)
        assertEquals(TimetableMode.FULL, m.mode)
    }

    @Test
    fun `超宽屏列宽封顶 168dp`() {
        val m = metrics(1400f, 1200f)
        assertEquals(168f, m.columnWidthDp, 0.01f)
        assertTrue("封顶后右侧留白，但不该再拉宽", m.visibleDays > 7f)
    }

    @Test
    fun `横屏时高度不够 自动改为竖向滚动`() {
        // 780x360 横屏：扣掉头部只剩 ~200dp，12 节每行不到 20dp，必须滚
        val m = metrics(780f, 220f)
        assertEquals(TimetableMode.SCROLL, m.mode)
        assertTrue("滚动模式下也要给足两行字的高度", m.rowHeightDp >= 32f)
    }

    @Test
    fun `系统字号放大 1_3 倍时 降级为紧凑模式`() {
        // 同一块区域，字号 1.3 倍后三行字放不下，但两行还放得下
        val normal = metrics(360f, 640f, fontScale = 1f)
        val large = metrics(360f, 640f, fontScale = 1.3f)
        assertEquals(TimetableMode.FULL, normal.mode)
        assertEquals(TimetableMode.DENSE, large.mode)
        assertTrue(large.rowHeightDp > 0f)
    }

    @Test
    fun `系统字号 2 倍时直接滚动`() {
        val m = metrics(360f, 640f, fontScale = 2f)
        assertEquals(TimetableMode.SCROLL, m.mode)
        assertTrue(m.rowHeightDp >= 64f)
    }

    @Test
    fun `任何情况下行高都不会被压到放不下字`() {
        val sizes = listOf(
            320f to 480f, 360f to 640f, 360f to 780f, 375f to 812f,
            411f to 891f, 412f to 915f, 480f to 800f, 600f to 960f, 800f to 1280f,
            640f to 360f, 780f to 360f, 915f to 412f,
        )
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f)
        for ((w, h) in sizes) {
            for (scale in scales) {
                val m = metrics(w, h, scale)
                val need = when (m.mode) {
                    TimetableMode.FULL -> 46f * scale
                    TimetableMode.DENSE -> 32f * scale
                    TimetableMode.SCROLL -> 32f * scale
                }
                assertTrue(
                    "屏幕 ${w}x$h 字号 $scale 下给定行高 ${m.rowHeightDp} < 需要 $need",
                    m.rowHeightDp + 0.01f >= need,
                )
                assertTrue("行高必须为正", m.rowHeightDp > 0f)
                assertTrue("节次栏宽度在 44..58", m.gutterWidthDp in 44f..58f)
            }
        }
    }

    @Test
    fun `每节课数变化也成立`() {
        // 有的学校 10 节、有的 14 节；行高 = 可用高 / 节数，节数多行高就小
        val ten = TimetableLayout.of(360f, 600f, 10, 1f)
        val fourteen = TimetableLayout.of(360f, 600f, 14, 1f)
        assertTrue("节数多，行高必须更小", fourteen.rowHeightDp < ten.rowHeightDp)
    }
}
