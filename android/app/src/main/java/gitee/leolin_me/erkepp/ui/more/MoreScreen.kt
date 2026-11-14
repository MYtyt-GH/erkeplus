package gitee.leolin_me.erkepp.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.ui.EntryRow

/**
 * 「更多」：底部导航的第三个入口，放**不是「我」的**东西。
 *
 * 判定标准（2026-11-15 与用户确认）：离开「我」仍然成立 → 放这里。
 * 所以「所有组织」「加入组织」「打开官方页面」「学分排行榜」在这里，而「我的组织」「我的附件」
 * 「我的诚信值」都在「我的」页。**同一个目的地只能有一个人口**：
 * 原来自里的「修改个人资料」和「我的 → 我的信息」是同一页，已经删掉这边那条。
 *
 * 唯一的例外是学分排行榜：榜单条目在这里，但「我的」页**学分那张数字卡**也是它的入口
 * （点学分看明细是直觉动作）。用户确认保留这个例外，所以「我的」页列表里不再重复列它。
 *
 * 说不清归属的也算不强相关，同样走这里（例：数据管理管的是缓存、日志这类本机杂物，
 * 不是「我的」资料）。以后做成原生的官方页面（成绩单、问卷、申报…）也先往这里加，
 * 不给它单独开底部 tab。
 */
@Composable
fun MoreScreen(
    onOpenAllOrganizations: () -> Unit,
    onJoinOrganization: () -> Unit,
    onOpenRank: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenWeb: () -> Unit,
    onComingSoon: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "更多",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "全校通用的入口，和与本机有关的设置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("榜单")
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    title = "学分排行榜",
                    subtitle = "成绩就是学分，由学时折算（1 学时 = 0.025 学分）",
                    onClick = onOpenRank,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("教务")
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                // 课表本页在「我的」里；成绩与考试还没做，入口先放这里，
                // 不塞进课表页底部——那两件事不属于一张周课表（2026-11-15 用户指出）
                EntryRow(
                    title = "成绩查询",
                    subtitle = "看每门课的成绩、学分与绩点",
                    onClick = { onComingSoon("成绩查询") },
                )
                EntryRow(
                    title = "考试安排",
                    subtitle = "看期末考试的时间、考场与座位号",
                    onClick = { onComingSoon("考试安排") },
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("组织")
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    title = "所有组织",
                    subtitle = "全校组织目录，先看简介再决定申不申请",
                    onClick = onOpenAllOrganizations,
                )
                EntryRow(
                    title = "加入组织",
                    subtitle = "扫组织码，直接进那个组织的详情",
                    onClick = onJoinOrganization,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("本机与网页")
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    title = "数据管理",
                    subtitle = "缓存、日志、回收站的占用，可逐项清理",
                    onClick = onOpenStorage,
                )
                EntryRow(
                    title = "打开官方页面",
                    subtitle = "网页版完整功能：成绩单、问卷、申报等",
                    onClick = onOpenWeb,
                    showDivider = false,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "还有功能没搬进来？「打开官方页面」可以在网页版里完成剩下的操作。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
