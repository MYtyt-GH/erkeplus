package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 消息的落点判定。
 *
 * 规则来自官方 H5 的 `type` / `subType` 分支表，只保留我方有页面的三种，
 * 其余一律 [NoticeTarget.None]（只标已读，正文在本地弹窗里给全）。
 */
class NoticeTargetTest {

    private val me = 1001L

    private fun notice(
        type: Int,
        subType: Int = 0,
        jumpInfo: String = "424242",
        managerId: Long = 0L,
    ) = NoticeItem(
        id = 900001L,
        type = type,
        typeName = "",
        subType = subType,
        subTypeName = "",
        content = "",
        isRead = false,
        ctime = 0L,
        jumpInfo = jumpInfo,
        managerId = managerId,
    )

    @Test
    fun `activity notice opens activity detail`() {
        assertEquals(
            NoticeTarget.Activity(424242L),
            noticeTargetOf(notice(type = 2), myUserId = me),
        )
    }

    @Test
    fun `activity notice operated by me is not the student page`() {
        // managerId 是我自己时官方去管理员视角，我们没有那个页面，只标已读
        assertEquals(
            NoticeTarget.None,
            noticeTargetOf(notice(type = 2, managerId = me), myUserId = me),
        )
    }

    @Test
    fun `activity notice without usable id goes nowhere`() {
        assertEquals(
            NoticeTarget.None,
            noticeTargetOf(notice(type = 2, jumpInfo = ""), myUserId = me),
        )
        assertEquals(
            NoticeTarget.None,
            noticeTargetOf(notice(type = 2, jumpInfo = "not-a-number"), myUserId = me),
        )
    }

    @Test
    fun `joining an organization lands on my organizations`() {
        assertEquals(
            NoticeTarget.MyOrganizations,
            noticeTargetOf(notice(type = 3, subType = 13), myUserId = me),
        )
        assertEquals(
            NoticeTarget.MyOrganizations,
            noticeTargetOf(notice(type = 3, subType = 19), myUserId = me),
        )
    }

    @Test
    fun `other organization notices open organization detail`() {
        assertEquals(
            NoticeTarget.Organization(424242L),
            noticeTargetOf(notice(type = 3, subType = 7), myUserId = me),
        )
    }

    @Test
    fun `blacklist and unknown types only mark read`() {
        assertEquals(NoticeTarget.None, noticeTargetOf(notice(type = 162, subType = 163), myUserId = me))
        assertEquals(NoticeTarget.None, noticeTargetOf(notice(type = 4), myUserId = me))
        assertEquals(NoticeTarget.None, noticeTargetOf(notice(type = 22), myUserId = me))
    }

    @Test
    fun `organization notice with unusable id goes nowhere`() {
        assertEquals(
            NoticeTarget.None,
            noticeTargetOf(notice(type = 3, subType = 7, jumpInfo = ""), myUserId = me),
        )
    }
}
