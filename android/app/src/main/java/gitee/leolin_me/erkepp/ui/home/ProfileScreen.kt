package gitee.leolin_me.erkepp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gitee.leolin_me.erkepp.data.CreditDetail
import gitee.leolin_me.erkepp.data.Session
import gitee.leolin_me.erkepp.data.rankScoreText
import gitee.leolin_me.erkepp.ui.EntryRow
import gitee.leolin_me.erkepp.ui.profile.AvatarImage
import java.util.Calendar
import java.util.Locale

/**
 * 「我的」：只放**和这个用户强相关**的东西，一律用「我的 X」命名。
 *
 * 判定标准（2026-11-15 与用户确认）：这件事离开「我」还成立吗？
 * 成立就不属于这里——「所有组织」「打开官方页面」离开我也成立，归「更多」；
 * 「缓存 / 日志 / 回收站占用」这种说不清是不是我的，也算不强相关，同样归「更多」。
 *
 * 结构：资料卡（点进「我的信息」）→ 一行三个数字→「我的 X」列表。
 * 数字卡里只有「学分」可点（进成绩排行榜），另外两个纯展示；成绩排行榜是全校榜单，
 * 它的**条目**放在「更多」，这里只借学分这个数字当入口，不在列表里重复一遍。
 * 「我的课表」暂未接入，点进去只弹提示。
 *
 * 资料刷新由外面驱动（本次启动首次进入自动拉一次、下拉刷新、双击底部「我的」），
 * 本页不自己发起请求，只把「上次更新时间」显示出来、把下拉动作转达出去。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    session: Session,
    credit: CreditDetail?,
    updatedAt: Long,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenPersonalInfo: () -> Unit,
    onOpenCredit: () -> Unit,
    onOpenRank: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenAttachments: () -> Unit,
    onOpenTimetable: () -> Unit,
) {
    val profile = session.profile
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // 资料卡整块可点：进「我的信息」，里面能看全信息、改密码手机号
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPersonalInfo),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(profile.avatar, profile.name)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val second = listOf(profile.college, profile.major)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (second.isNotEmpty()) {
                        Text(
                            text = second,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "我的信息 ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoChip("学号", profile.studentNo)
                if (profile.grade.isNotBlank()) InfoChip("年级", profile.grade)
                if (profile.className.isNotBlank()) InfoChip("班级", profile.className)
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    label = "学分",
                    // 和排行榜用同一个格式化：最多三位小数、去尾零。
                    // 学分是 hours × 0.025，21 学时是 0.525，按一位小数显示会被截成 0.5
                    value = rankScoreText(profile.score),
                    // 数字本身就是通往明细的门：点学分进成绩排行榜。
                    // 排行榜不在这页的列表里出现——它是公共榜单，条目在「更多」
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenRank),
                )
                StatCard("学时", format(profile.hours), Modifier.weight(1f))
                StatCard(
                    label = "诚信值",
                    value = credit?.let { formatScore(it.credit) } ?: formatScore(profile.credit),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(22.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    EntryRow(
                        title = "我的诚信值",
                        subtitle = "守约记录、失约记录与申诉",
                        onClick = onOpenCredit,
                    )
                    EntryRow(
                        title = "我的组织",
                        subtitle = "我加入的、我创建的、我退出的，以及待审核的申请",
                        onClick = onOpenOrganizations,
                    )
                    EntryRow(
                        title = "我的附件",
                        subtitle = "已下载的活动附件与回收站",
                        onClick = onOpenAttachments,
                    )
                    EntryRow(
                        title = "我的课表",
                        subtitle = "教务系统的课表，按周看；首次要点一下登录",
                        onClick = onOpenTimetable,
                        showDivider = false,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "资料更新于 ${formatUpdatedAt(updatedAt)} · 下拉或双击底部「我的」可刷新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Avatar(url: String?, name: String) {
    AvatarImage(
        url = url,
        name = name,
        contentDescription = "头像",
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape),
    )
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun format(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }

private fun formatScore(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }

/**
 * 时间戳 → 「11-15 02:31」。跨年才带年份；0 表示这次安装还没成功拉过资料。
 * 只做展示，不参与计算，所以不做「几分钟前」这类相对时间——用户要的是确切的点。
 */
private fun formatUpdatedAt(ts: Long): String {
    if (ts <= 0L) return "尚未更新"
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val now = Calendar.getInstance()
    val date = if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
        String.format(Locale.US, "%02d-%02d", then.get(Calendar.MONTH) + 1, then.get(Calendar.DAY_OF_MONTH))
    } else {
        String.format(
            Locale.US,
            "%d-%02d-%02d",
            then.get(Calendar.YEAR),
            then.get(Calendar.MONTH) + 1,
            then.get(Calendar.DAY_OF_MONTH),
        )
    }
    val time = String.format(
        Locale.US,
        "%02d:%02d",
        then.get(Calendar.HOUR_OF_DAY),
        then.get(Calendar.MINUTE),
    )
    return "$date $time"
}
