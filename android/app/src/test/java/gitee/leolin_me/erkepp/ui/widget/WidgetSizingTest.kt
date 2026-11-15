package gitee.leolin_me.erkepp.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 小组件尺寸判断：能放几行、什么时候缩成窄档。
 *
 * 这一组就是用户那句「我的用户不会只有一种手机尺寸」在桌面上的版本。
 */
class WidgetSizingTest {

    @Test
    fun `常见的 4x2 与 2x2`() {
        // 4x2 大约 170dp 高：扣掉顶部还能放两行课
        assertEquals(2, WidgetSizing.boardRows(170f, 1f))
        // 2x2 大约 110dp：一两行
        assertTrue(WidgetSizing.boardRows(110f, 1f) >= 1)
        // 4x4 能多放
        assertTrue(WidgetSizing.boardRows(340f, 1f) > 4)
    }

    @Test
    fun `半天课表 4x2 能放三行`() {
        // 两行字的行高 30dp：140dp 的格子扣掉标题还能放三节
        assertEquals(3, WidgetSizing.halfDayRows(140f, 1f))
    }

    @Test
    fun `字号放大行数变少`() {
        val normal = WidgetSizing.boardRows(300f, 1f)
        val large = WidgetSizing.boardRows(300f, 1.5f)
        assertTrue("字号越大行数应该越少：$normal / $large", normal > large)
        assertTrue(large >= 1)
    }

    @Test
    fun `极度拥挤时至少留一行`() {
        assertEquals(1, WidgetSizing.boardRows(20f, 1f))
        assertEquals(1, WidgetSizing.boardRows(0f, 1f))
        assertEquals(1, WidgetSizing.halfDayRows(60f, 2f))
    }

    @Test
    fun `窄档位判定`() {
        assertTrue(WidgetSizing.compact(110f))
        assertTrue(WidgetSizing.compact(199f))
        assertFalse(WidgetSizing.compact(200f))
        assertFalse(WidgetSizing.compact(320f))
        // 系统没报宽度（0）时按「不窄」处理，宁可多显示
        assertFalse(WidgetSizing.compact(0f))
    }

    @Test
    fun `课程看板窄到放不下才藏教室列`() {
        assertTrue(WidgetSizing.compactBoard(160f))
        assertTrue(WidgetSizing.compactBoard(169f))
        assertFalse(WidgetSizing.compactBoard(170f))
        assertFalse(WidgetSizing.compactBoard(200f))
        assertFalse(WidgetSizing.compactBoard(0f))
    }
}
