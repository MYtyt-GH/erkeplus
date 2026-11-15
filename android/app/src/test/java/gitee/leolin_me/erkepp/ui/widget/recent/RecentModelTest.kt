package gitee.leolin_me.erkepp.ui.widget.recent

import gitee.leolin_me.erkepp.ui.widget.WidgetFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 最近两节课：永远只取「现在往后」最近的两节，跨天也算。 */
class RecentModelTest {

    @Test
    fun `早上取最近两节`() {
        val model = RecentModel.build(WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 7 * 60))
        assertEquals("接下来", model.label)
        assertEquals(2, model.rows.size)
        assertEquals("高等数学", model.rows[0].name)
        assertEquals("大学物理", model.rows[1].name)
        assertNull(model.notice)
    }

    @Test
    fun `正在上时标题改成正在上`() {
        val model = RecentModel.build(WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 10 * 60 + 30))
        assertEquals("正在上", model.label)
        assertEquals("大学物理", model.rows[0].name)
        assertTrue(model.rows[0].ongoing)
        // 上完的那节不再占位置
        assertEquals("大学英语", model.rows[1].name)
    }

    @Test
    fun `今天都上完了就接明天的课`() {
        val lessons = listOf(
            WidgetFixtures.lesson("高等数学", start = "08:00", end = "09:40", unit = 1),
            WidgetFixtures.lesson(
                "大学化学",
                date = WidgetFixtures.saturday,
                start = "08:00",
                end = "09:40",
                unit = 1,
            ),
        )
        val model = RecentModel.build(WidgetFixtures.snapshot(lessons, nowMinute = 21 * 60))
        assertEquals("接下来", model.label)
        assertEquals(1, model.rows.size)
        assertEquals("大学化学", model.rows[0].name)
        assertEquals("明天 08:00", model.rows[0].start)
    }

    @Test
    fun `接下来没有课`() {
        val model = RecentModel.build(WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 22 * 60))
        assertEquals("接下来没有课", model.notice?.title)
        assertTrue(model.rows.isEmpty())
    }

    @Test
    fun `没缓存只说没缓存`() {
        val model = RecentModel.build(
            WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 9 * 60, weekCached = false),
        )
        assertEquals("这周的课表还没下载", model.notice?.title)
    }

    @Test
    fun `没登录教务`() {
        val model = RecentModel.build(WidgetFixtures.snapshot(emptyList(), nowMinute = 9 * 60, sem = null))
        assertEquals("还没有课表", model.notice?.title)
    }
}
