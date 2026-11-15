package gitee.leolin_me.erkepp.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.ApiClient
import gitee.leolin_me.erkepp.data.DeviceId
import gitee.leolin_me.erkepp.data.NoticeItem
import gitee.leolin_me.erkepp.data.NotifyRepository
import gitee.leolin_me.erkepp.data.SessionStore
import gitee.leolin_me.erkepp.data.WorkItem
import gitee.leolin_me.erkepp.ui.ErkeppTheme
import gitee.leolin_me.erkepp.ui.notify.NoticeRow
import gitee.leolin_me.erkepp.ui.notify.NotifyScreen
import gitee.leolin_me.erkepp.ui.notify.UnreadBanner
import gitee.leolin_me.erkepp.ui.notify.WorkRow

/**
 * 只在 debug 包里存在的「阅办」行样式预览。
 *
 * 为什么需要它：未读/已读的差别（加粗 + 左侧圆点）只有服务端给 `isRead=0` 时才看得到，
 * 而账号里的未读一旦被「全部已读」点掉就再也造不出来了。这里用合成数据把三种行
 * （未读消息、已读消息、待办）铺出来，用来对照暗色/亮色、长标题与折行。
 *
 * ```
 * adb shell am start -n gitee.leolin_me.erkepp.debug/gitee.leolin_me.erkepp.debug.NoticePreviewActivity \
 *     --es theme dark     # dark / light / 留空跟随系统
 * ```
 */
class NoticePreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val asked = intent.getStringExtra("theme")
        // `--ez full true`：把正式页面按指定主题铺开（借当前登录态真拉数据），
        // 用来在不动系统深色设置的前提下走查整页在亮色下的样子
        val full = intent.getBooleanExtra("full", false)
        val session = if (full) SessionStore(this).load() else null

        setContent {
            // isSystemInDarkTheme() 只能在组合里调，所以这段不能挪到 setContent 外面
            val dark = when (asked) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            ErkeppTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (full) {
                        if (session == null) {
                            Text(
                                text = "没有登录态，先登录再看整页预览",
                                modifier = Modifier.padding(24.dp),
                            )
                        } else {
                            // 正式页面在 MainActivity 的 Surface 里是让开状态栏的，预览这块自己补上
                            Box(modifier = Modifier.systemBarsPadding()) {
                                NotifyScreen(
                                token = session.token,
                                repository = NotifyRepository(
                                    ApiClient(deviceId = { DeviceId.get(applicationContext) }),
                                ),
                                myUserId = session.profile.userId,
                                preloadEnabled = true,
                                // 预览页不导航：点了就停在原地，看清样式为准
                                onOpenActivity = {},
                                onOpenOrganizations = {},
                                onOpenOrganization = {},
                                onOpenWorkRoute = {},
                                onBack = { finish() },
                                )
                            }
                        }
                    } else {
                        PreviewContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewContent() {
    val now = System.currentTimeMillis()
    val samples = listOf(
        NoticeItem(
            id = 1,
            type = 2,
            typeName = "活动通知",
            subType = 6,
            subTypeName = "学时已发放",
            content = "恭喜你参加的活动，5.0学时已发放",
            isRead = false,
            ctime = now - 40 * 60_000L,
            jumpInfo = "565487",
            managerId = 0,
        ),
        NoticeItem(
            id = 2,
            type = 162,
            typeName = "黑名单通知",
            subType = 163,
            subTypeName = "黑名单解除审核通过",
            content = "恭喜，黑名单解除申请，已审核通过。",
            isRead = true,
            ctime = now - 26 * 60 * 60_000L,
            jumpInfo = "",
            managerId = 0,
        ),
        NoticeItem(
            id = 3,
            type = 2,
            typeName = "活动通知",
            subType = 12,
            subTypeName = "开始签到、签退",
            content = "你参加的数大学院消防安全讲座活动，管理员开始签到啦，快去签到吧！这条故意写长一点，" +
                "看看三行之后的省略号是不是正常，以及已读那种浅一点的颜色在暗色下还看不看得清",
            isRead = false,
            ctime = now - 3 * 24 * 60 * 60_000L,
            jumpInfo = "586570",
            managerId = 0,
        ),
        NoticeItem(
            id = 4,
            type = 3,
            typeName = "组织通知",
            subType = 13,
            subTypeName = "成功加入部落",
            content = "恭喜你成功加入人工智能协会组织",
            isRead = true,
            ctime = now - 40 * 24 * 60 * 60_000L,
            jumpInfo = "17085",
            managerId = 0,
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 正式页面在 SecondaryPage 里，状态栏是让开的；预览页自己补上，截图才对得上
            .systemBarsPadding(),
    ) {
        UnreadBanner(count = 3, busy = false, onClick = {})
        samples.forEach { item ->
            NoticeRow(item = item, onClick = {})
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "待办行（学生账号恒为空，只有合成数据能看）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        WorkRow(item = WorkItem(icon = "", workDes = "活动审核", count = 3, route = "/activityAudit/xxx"), onClick = {})
        WorkRow(item = WorkItem(icon = "", workDes = "待提交作业", count = 0, route = ""), onClick = {})
    }
}
