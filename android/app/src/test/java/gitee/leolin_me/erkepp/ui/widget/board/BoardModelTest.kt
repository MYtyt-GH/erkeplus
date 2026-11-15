package gitee.leolin_me.erkepp.ui.widget.board

import gitee.leolin_me.erkepp.ui.widget.WidgetFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 课程看板：从「现在」往后的连续课表，跨天时插一行日期。
 *
 * 时间点用「第几分钟」，与真实时钟无关，所以断言不会因为哪天跑测试而变。
 */
class BoardModelTest {

    private fun build(
        snapshot: gitee.leolin_me.erkepp.ui.widget.WidgetSnapshot,
        maxRows: Int = 6,
    ) = BoardModel.build(snapshot, maxRows)

    private fun BoardModel.courses() = blocks.filterIsInstance<BoardBlock.Course>().map { it.row }
    private fun BoardModel.days() = blocks.filterIsInstance<BoardBlock.Day>().map { it.label }

    @Test
    fun `早上看今天：从第一节课开始`() {
        val model = build(WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 7 * 60))
        assertEquals("第 11 周", model.weekLabel)
        assertEquals("11/13 周五", model.dateLabel)
        assertNull(model.notice)
        assertEquals(3, model.courses().size)
        assertEquals("08:00", model.courses()[0].start)
        assertEquals("09:40", model.courses()[0].end)
        assertEquals("高等数学", model.courses()[0].name)
        assertEquals("明理北楼 明理北103", model.courses()[0].room)
        assertTrue("同一天不该插日期行", model.days().isEmpty())
    }

    @Test
    fun `中午只排还没上完的课`() {
        val model = build(WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 12 * 60))
        val courses = model.courses()
        assertEquals(1, courses.size)
        assertEquals("大学英语", courses[0].name)
    }

    @Test
    fun `正上着的那节也列出来`() {
        val model = build(WidgetFixtures.snapshot(WidgetFixtures.threeLessons, nowMinute = 10 * 60 + 30))
        val courses = model.courses()
        assertEquals(2, courses.size)
        assertTrue(courses[0].ongoing)
    }

    @Test
    fun `今天上完了就接着明天的课`() {
        val tomorrow = listOf(
            WidgetFixtures.lesson("大学化学", date = WidgetFixtures.saturday, start = "08:00", unit = 1),
        )
        val model = build(
            WidgetFixtures.snapshot(WidgetFixtures.threeLessons + tomorrow, nowMinute = 21 * 60),
            maxRows = 6,
        )
        assertEquals("明天 11/14", model.dateLabel)
        // 第一天的日期已经写在右上角，不再插一行
        assertTrue(model.days().isEmpty())
        assertEquals(1, model.courses().size)
        assertEquals("大学化学", model.courses()[0].name)
    }

    @Test
    fun `空间够就多排几节 跨天插日期`() {
        val lessons = listOf(
            WidgetFixtures.lesson("第一节", start = "08:00", end = "09:40", unit = 1),
            WidgetFixtures.lesson("第二节", start = "10:00", end = "11:40", unit = 3),
            WidgetFixtures.lesson("明天的课", date = WidgetFixtures.saturday, start = "08:00", end = "09:40", unit = 1),
            WidgetFixtures.lesson("后天的课", date = WidgetFixtures.saturday.plusDays(1), start = "10:00", end = "11:40", unit = 3),
        )
        val model = build(WidgetFixtures.snapshot(lessons, nowMinute = 7 * 60), maxRows = 4)
        assertEquals(4, model.courses().size)
        assertEquals(listOf("明天 11/14", "后天 11/15"), model.days())
        assertEquals("第一节", model.courses()[0].name)
        assertEquals("后天的课", model.courses()[3].name)
    }

    @Test
    fun `放不下的课进「还有 N 节」`() {
        val lessons = (1..5).map { i ->
            WidgetFixtures.lesson("课$i", start = "%02d:00".format(7 + i), end = "%02d:40".format(7 + i), unit = i)
        }
        val model = build(WidgetFixtures.snapshot(lessons, nowMinute = 6 * 60), maxRows = 2)
        assertEquals(2, model.courses().size)
        assertEquals("还有 3 节", model.footer)
    }

    @Test
    fun `没登录过教务`() {
        val model = build(WidgetFixtures.snapshot(emptyList(), nowMinute = 9 * 60, sem = null))
        assertEquals("还没有课表", model.notice?.title)
        assertEquals("", model.weekLabel)
        assertEquals("11/13 周五", model.dateLabel)
    }

    @Test
    fun `这周的课表还没下载`() {
        val model = build(WidgetFixtures.snapshot(emptyList(), nowMinute = 9 * 60, weekCached = false))
        assertEquals("这周的课表还没下载", model.notice?.title)
        assertEquals("第 11 周", model.weekLabel)
    }

    @Test
    fun `开学前与放假后都不画课表`() {
        val before = build(
            WidgetFixtures.snapshot(emptyList(), nowMinute = 9 * 60, today = LocalDate.of(2026, 8, 20)),
        )
        assertEquals("还没开学", before.notice?.title)
        assertEquals("距开学还有 11 天", before.notice?.note)

        val after = build(
            WidgetFixtures.snapshot(emptyList(), nowMinute = 9 * 60, today = LocalDate.of(2027, 2, 1)),
        )
        assertEquals("本学期已结束", after.notice?.title)
    }

    @Test
    fun `缓存里再往后都没课`() {
        val model = build(WidgetFixtures.snapshot(emptyList(), nowMinute = 9 * 60))
        assertEquals("今天没有课程", model.notice?.title)
        assertEquals("这周剩下的时间没课", model.notice?.note)
        assertTrue(model.blocks.isEmpty())
    }
}
