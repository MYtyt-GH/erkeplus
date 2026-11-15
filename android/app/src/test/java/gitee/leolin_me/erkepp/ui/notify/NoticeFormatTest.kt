package gitee.leolin_me.erkepp.ui.notify

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** 消息时间文案。时区与「现在」都显式传入，不依赖跑测试那台机器。 */
class NoticeFormatTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now = millisOf("2026-11-15T18:10:00")

    private fun millisOf(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `today shows the clock`() {
        assertEquals("今天 09:05", noticeTimeText(millisOf("2026-11-15T09:05:00"), now, zone))
        // 同一天的零点之后也算今天
        assertEquals("今天 00:01", noticeTimeText(millisOf("2026-11-15T00:01:00"), now, zone))
    }

    @Test
    fun `yesterday is spelled out`() {
        assertEquals("昨天 23:59", noticeTimeText(millisOf("2026-11-14T23:59:00"), now, zone))
    }

    @Test
    fun `same year shows month and day`() {
        assertEquals("03-01 08:00", noticeTimeText(millisOf("2026-03-01T08:00:00"), now, zone))
    }

    @Test
    fun `older shows the year`() {
        assertEquals("2025-12-31", noticeTimeText(millisOf("2025-12-31T22:30:00"), now, zone))
    }

    @Test
    fun `second timestamps are understood`() {
        val millis = millisOf("2026-11-15T10:00:00")
        assertEquals("今天 10:00", noticeTimeText(millis, now, zone))
        assertEquals("今天 10:00", noticeTimeText(millis / 1000, now, zone))
    }

    @Test
    fun `missing timestamp shows nothing`() {
        assertEquals("", noticeTimeText(0L, now, zone))
        assertEquals("", noticeTimeText(-1L, now, zone))
    }
}
