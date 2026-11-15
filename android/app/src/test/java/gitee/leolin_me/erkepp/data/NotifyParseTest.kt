package gitee.leolin_me.erkepp.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的阅办」的解析。
 *
 * 样本按 2026-11-15 真机抓到的字段形状手写（正文换成合成文案、id 换成合成值），
 * 字段名与类型（`isRead` 整数、`jumpInfo` 数字与字符串都有、`extras` 嵌一层）都是实测的。
 */
class NotifyParseTest {

    private val unreadItem = """
        {"id":900001,"type":2,"typeName":"活动通知","subType":6,"subTypeName":"学时已发放",
         "content":"恭喜你参加的活动 5.0 学时已发放","isRead":0,"ctime":1794456345000,
         "icon":"","showDelete":"false","extras":{"managerId":1001,"jumpInfo":424242}}
    """.trimIndent()

    private val readItem = """
        {"id":900002,"type":3,"typeName":"组织通知","subType":13,"subTypeName":"成功加入组织",
         "content":"你已成功加入某组织","isRead":1,"ctime":1794370000000,
         "extras":{"managerId":0,"jumpInfo":"17085"}}
    """.trimIndent()

    @Test
    fun `unread activity notice keeps its fields`() {
        val parsed = parseNoticeItem(Json.parseToJsonElement(unreadItem))!!
        assertEquals(900001L, parsed.id)
        assertEquals(2, parsed.type)
        assertEquals("活动通知", parsed.typeName)
        assertEquals(6, parsed.subType)
        assertEquals("学时已发放", parsed.subTypeName)
        assertFalse(parsed.isRead)
        assertEquals(1794456345000L, parsed.ctime)
        assertEquals("424242", parsed.jumpInfo)
        assertEquals(1001L, parsed.managerId)
    }

    @Test
    fun `read organization notice accepts string jumpInfo`() {
        val parsed = parseNoticeItem(Json.parseToJsonElement(readItem))!!
        assertTrue(parsed.isRead)
        assertEquals(3, parsed.type)
        // type=3 的 jumpInfo 实测是字符串，活动那条是数字，两种都要收
        assertEquals("17085", parsed.jumpInfo)
        assertEquals(0L, parsed.managerId)
    }

    @Test
    fun `item without id is dropped`() {
        val parsed = parseNoticeItem(
            Json.parseToJsonElement("""{"type":2,"subType":6,"content":"没有 id"}"""),
        )
        assertNull(parsed)
    }

    @Test
    fun `missing extras does not crash`() {
        val parsed = parseNoticeItem(
            Json.parseToJsonElement("""{"id":900003,"type":162,"isRead":0,"ctime":1794000000000}"""),
        )!!
        assertEquals("", parsed.jumpInfo)
        assertEquals(0L, parsed.managerId)
    }

    @Test
    fun `page uses total to decide last page`() {
        // total=27、每页 10：第 3 页是最后一页，第 2 页不是
        val third = parseNoticePage(
            Json.parseToJsonElement("""{"total":27,"pageNum":3,"pageSize":10,"list":[$unreadItem]}"""),
            page = 3,
            pageSize = 10,
        )
        assertEquals(27, third.total)
        assertTrue(third.lastPage)

        val second = parseNoticePage(
            Json.parseToJsonElement("""{"total":27,"pageNum":2,"pageSize":10,"list":[$unreadItem]}"""),
            page = 2,
            pageSize = 10,
        )
        assertFalse(second.lastPage)

        // 响应里没有 lastPage，总数也拿不到时，退回「这一页没满就是最后一页」
        val unknown = parseNoticePage(
            Json.parseToJsonElement("""{"list":[$unreadItem]}"""),
            page = 1,
            pageSize = 10,
        )
        assertEquals(1, unknown.total)
        assertTrue(unknown.lastPage)
    }

    @Test
    fun `unread count accepts integers and strings`() {
        assertEquals(10, parseUnreadCount(Json.parseToJsonElement("""{"code":0,"data":{"unReadCount":10}}""")))
        // 同一族接口里广场那一个给的是字符串，这里也一并认
        assertEquals(0, parseUnreadCount(Json.parseToJsonElement("""{"code":0,"data":{"unReadCount":"0"}}""")))
        // 信封结构变了也不至于归零
        assertEquals(3, parseUnreadCount(Json.parseToJsonElement("""{"unReadCount":3}""")))
        assertEquals(0, parseUnreadCount(Json.parseToJsonElement("""{"code":0,"data":{}}""")))
        assertEquals(0, parseUnreadCount(Json.parseToJsonElement("""{"code":0}""")))
    }

    @Test
    fun `work items parse the four fields`() {
        val works = parseWorkItems(
            Json.parseToJsonElement(
                """[{"icon":"https://example.invalid/icon.png","workDes":"活动审核","count":3,
                    "route":"/activityAudit/xxx"}]""",
            ),
        )
        assertEquals(1, works.size)
        assertEquals("活动审核", works[0].workDes)
        assertEquals(3, works[0].count)
        assertEquals("/activityAudit/xxx", works[0].route)
        assertEquals("https://example.invalid/icon.png", works[0].icon)
    }

    @Test
    fun `work item without a name is dropped`() {
        val works = parseWorkItems(Json.parseToJsonElement("""[{"count":2,"route":"/x"},{"workDes":" "}]"""))
        assertTrue(works.isEmpty())
    }

    @Test
    fun `work list tolerates an empty array and a missing data`() {
        assertTrue(parseWorkItems(Json.parseToJsonElement("[]")).isEmpty())
        assertTrue(parseWorkItems(null).isEmpty())
        // 学生账号实测就是空数组；万一服务端回成对象也不崩
        assertTrue(parseWorkItems(Json.parseToJsonElement("""{"list":[]}""")).isEmpty())
    }
}
