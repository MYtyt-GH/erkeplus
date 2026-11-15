package gitee.leolin_me.erkepp.ui.widget.halfday

import gitee.leolin_me.erkepp.ui.widget.WidgetFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 半天课表：当前阶段有课就看当前阶段，没有就看下一阶段，全都没有就写「暂无半天课表」。
 *
 * 阶段边界（11:50）是最容易写错的地方，单独钉一条。
 */
class HalfDayModelTest {

    private fun build(
        lessons: List<gitee.leolin_me.erkepp.data.eams.EamsLesson>,
        nowMinute: Int,
        maxRows: Int = 4,
        weekCached: Boolean = true,
    ) = HalfDayModel.build(
        WidgetFixtures.snapshot(lessons, nowMinute = nowMinute, weekCached = weekCached),
        maxRows,
    )

    @Test
    fun `上午看上午`() {
        val model = build(WidgetFixtures.threeLessons, nowMinute = 9 * 60)
        assertEquals("上午", model.title)
        assertEquals("", model.dayLabel)
        assertEquals(2, model.rows.size)
        assertEquals("高等数学", model.rows[0].name)
        assertNull(model.notice)
    }

    @Test
    fun `下午看下午`() {
        val model = build(WidgetFixtures.threeLessons, nowMinute = 13 * 60)
        assertEquals("下午", model.title)
        assertEquals("大学英语", model.rows[0].name)
    }

    @Test
    fun `上午刚下课就跳到下午`() {
        // 11:50：上午最后一节 11:40 已经结束，桌面不该还停在上午
        val model = build(WidgetFixtures.threeLessons, nowMinute = 11 * 60 + 50)
        assertEquals("下午", model.title)
        assertEquals("", model.dayLabel)
        assertEquals(1, model.rows.size)
        assertEquals("大学英语", model.rows[0].name)
    }

    @Test
    fun `晚上有课就看晚上`() {
        val lessons = listOf(
            WidgetFixtures.lesson("晚自习", start = "19:40", end = "21:20", unit = 11),
        )
        val model = build(lessons, nowMinute = 19 * 60)
        assertEquals("晚上", model.title)
        assertEquals(1, model.rows.size)
    }

    @Test
    fun `晚上没课就看第二天`() {
        val lessons = listOf(
            WidgetFixtures.lesson("晚自习", start = "17:50", end = "18:35", unit = 9),
            WidgetFixtures.lesson(
                "大学化学",
                date = WidgetFixtures.saturday,
                start = "08:00",
                end = "09:40",
                unit = 1,
            ),
        )
        val model = build(lessons, nowMinute = 21 * 60)
        assertEquals("上午", model.title)
        assertEquals("明天", model.dayLabel)
        assertEquals("大学化学", model.rows[0].name)
    }

    @Test
    fun `一段课都没有`() {
        val model = build(emptyList(), nowMinute = 9 * 60)
        assertEquals("暂无半天课表", model.notice?.title)
        assertTrue(model.rows.isEmpty())
    }

    @Test
    fun `这周的课表还没下载`() {
        val model = build(WidgetFixtures.threeLessons, nowMinute = 9 * 60, weekCached = false)
        assertEquals("这周的课表还没下载", model.notice?.title)
    }

    @Test
    fun `位置不够时收进还有 N 节`() {
        val lessons = listOf(
            WidgetFixtures.lesson("第一节", start = "08:00", end = "08:45", unit = 1),
            WidgetFixtures.lesson("第二节", start = "08:55", end = "09:40", unit = 2),
            WidgetFixtures.lesson("第三节", start = "10:00", end = "10:45", unit = 3),
            WidgetFixtures.lesson("第四节", start = "10:55", end = "11:40", unit = 4),
        )
        val model = build(lessons, nowMinute = 7 * 60, maxRows = 2)
        assertEquals(1, model.rows.size)
        assertEquals("还有 3 节", model.footer)
    }
}
