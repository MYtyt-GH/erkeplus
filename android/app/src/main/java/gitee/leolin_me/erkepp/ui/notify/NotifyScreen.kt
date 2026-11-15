package gitee.leolin_me.erkepp.ui.notify

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gitee.leolin_me.erkepp.data.NoticeItem
import gitee.leolin_me.erkepp.data.NoticeTarget
import gitee.leolin_me.erkepp.data.NotifyRepository
import gitee.leolin_me.erkepp.data.WorkItem
import gitee.leolin_me.erkepp.data.noticeTargetOf
import gitee.leolin_me.erkepp.data.toUserMessage
import gitee.leolin_me.erkepp.ui.EmptyState
import gitee.leolin_me.erkepp.ui.InlineLoading
import gitee.leolin_me.erkepp.ui.PageLoading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 「我的阅办」：官方 `#/needDealt` 那一页。
 *
 * 顶部两个 tab，默认停在**待办**（和官方一致）：
 * - 待办：服务端给的 `{icon, workDes, count, route}`，点一行走它给的路由；
 *   认得的映射进原生页，认不出的回退官方网页。学生账号这里是空列表 + 「暂无待办内容」，
 *   这是正确状态，不写成报错。
 * - 消息：时间倒序分页；未读行加粗并带一个点；点开自动标已读并按落点跳转。
 *
 * 没有未读角标，也不做轮询：未读数只在进这一页时拉一次（官方网页也是这样，
 * 它没有 WebSocket 也没有定时器，见 docs/research/yueban.md）。
 */
enum class NotifyTab(val label: String) {
    WORK("待办"),
    MESSAGE("消息"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifyScreen(
    token: String,
    repository: NotifyRepository,
    myUserId: Long,
    preloadEnabled: Boolean = true,
    onOpenActivity: (Long) -> Unit,
    onOpenOrganizations: () -> Unit,
    onOpenOrganization: (Long) -> Unit,
    /** 待办的服务端路由交给外面拼官方网址（这条路只在网页里能走通）。 */
    onOpenWorkRoute: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(NotifyTab.WORK) }

    // ---- 待办 ----
    var works by remember(token) { mutableStateOf<List<WorkItem>>(emptyList()) }
    var worksLoading by remember(token) { mutableStateOf(true) }
    var worksError by remember(token) { mutableStateOf<String?>(null) }
    var worksTick by remember { mutableIntStateOf(0) }

    // ---- 消息 ----
    var items by remember(token) { mutableStateOf<List<NoticeItem>>(emptyList()) }
    var total by remember(token) { mutableIntStateOf(0) }
    var unread by remember(token) { mutableIntStateOf(0) }
    var page by remember(token) { mutableIntStateOf(0) }
    var lastPage by remember(token) { mutableStateOf(true) }
    var loading by remember(token) { mutableStateOf(true) }
    var loadingMore by remember(token) { mutableStateOf(false) }
    var refreshing by remember(token) { mutableStateOf(false) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var markingAll by remember { mutableStateOf(false) }
    var messagesTick by remember { mutableIntStateOf(0) }
    // 没有落点、正文只能在本地给全的那条（黑名单通知、管理员视角通知…）
    var detail by remember { mutableStateOf<NoticeItem?>(null) }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun loadWorks() {
        worksLoading = true
        try {
            works = repository.processing(token)
            worksError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            worksError = failure.toUserMessage()
        } finally {
            worksLoading = false
        }
    }

    suspend fun loadMessages(target: Int, fromPull: Boolean = false) {
        if (target == 1) {
            if (fromPull) refreshing = true else loading = true
            loadingMore = false
        } else {
            loadingMore = true
        }
        try {
            val result = repository.list(token, target)
            items = if (target == 1) result.list else items + result.list
            total = result.total
            page = target
            lastPage = result.lastPage
            error = null
            // 未读数只作为「全部已读」那一条的显示依据，拿不到就沿用上一次的数
            unread = runCatching { repository.unreadCount(token) }.getOrElse { unread }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            if (target == 1) {
                error = failure.toUserMessage()
            } else {
                // 翻页失败不要清空已经看到的内容，说一声就行
                Toast.makeText(context, failure.toUserMessage(), Toast.LENGTH_SHORT).show()
            }
        } finally {
            loading = false
            loadingMore = false
            refreshing = false
        }
    }

    // 切 tab / 重试 / 下拉都从这里发起：每次进「消息」重拉第一页，和官方网页的行为一致
    LaunchedEffect(tab, worksTick, messagesTick) {
        when (tab) {
            NotifyTab.WORK -> loadWorks()
            NotifyTab.MESSAGE -> loadMessages(1)
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    // key 里不放 loadingMore：它在本效果发起的请求里被改写，会取消刚发的请求、翻页空转
    LaunchedEffect(nearEnd, items.size, lastPage, tab) {
        if (tab != NotifyTab.MESSAGE) return@LaunchedEffect
        if (!nearEnd || loadingMore || lastPage || items.isEmpty()) return@LaunchedEffect
        if (!preloadEnabled && items.size - (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > 2) {
            return@LaunchedEffect
        }
        loadMessages(page + 1)
    }

    fun markAllRead() {
        if (markingAll) return
        markingAll = true
        scope.launch {
            try {
                repository.markAllRead(token)
                Toast.makeText(context, "已全部标为已读", Toast.LENGTH_SHORT).show()
                loadMessages(1)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Toast.makeText(context, "操作失败：${failure.toUserMessage()}", Toast.LENGTH_SHORT).show()
            } finally {
                markingAll = false
            }
        }
    }

    /**
     * 点一条消息：先按落点走，未读的顺手标已读。
     *
     * 标记已读失败不改回未读样式——用户已经看过了，界面再变回未读只会让人以为没点上；
     * 下次下拉刷新会以服务端为准。
     */
    fun openMessage(item: NoticeItem) {
        if (!item.isRead) {
            items = items.map { if (it.id == item.id) it.copy(isRead = true) else it }
            unread = (unread - 1).coerceAtLeast(0)
            scope.launch {
                runCatching { repository.markRead(token, item.id) }
                    .onFailure { failure ->
                        Toast.makeText(context, "已读状态没标上：${failure.toUserMessage()}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        when (val target = noticeTargetOf(item, myUserId)) {
            is NoticeTarget.Activity -> onOpenActivity(target.id)
            is NoticeTarget.Organization -> onOpenOrganization(target.id)
            NoticeTarget.MyOrganizations -> onOpenOrganizations()
            NoticeTarget.None -> detail = item
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "我的阅办",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NotifyTab.entries.forEach { option ->
                FilterChip(
                    selected = tab == option,
                    onClick = { tab = option },
                    label = { Text(option.label) },
                )
            }
        }

        when (tab) {
            NotifyTab.WORK -> Box(modifier = Modifier.weight(1f)) {
                when {
                    worksLoading && works.isEmpty() -> PageLoading()

                    worksError != null && works.isEmpty() -> ErrorBlock(
                        text = worksError.orEmpty(),
                        onRetry = { worksTick++ },
                    )

                    works.isEmpty() -> EmptyState("暂无待办内容")

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(works, key = { "${it.workDes}-${it.route}" }) { item ->
                            WorkRow(
                                item = item,
                                onClick = {
                                    if (item.route.isNotBlank()) onOpenWorkRoute(item.route)
                                },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }

            NotifyTab.MESSAGE -> Box(modifier = Modifier.weight(1f)) {
                when {
                    loading && items.isEmpty() -> PageLoading()

                    error != null && items.isEmpty() -> ErrorBlock(
                        text = error.orEmpty(),
                        onRetry = { messagesTick++ },
                    )

                    items.isEmpty() -> EmptyState("还没有消息")

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "共 $total 条",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            PullToRefreshBox(
                                isRefreshing = refreshing,
                                onRefresh = { scope.launch { loadMessages(1, fromPull = true) } },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                    if (unread > 0) {
                                        item(key = "unread-banner") {
                                            UnreadBanner(
                                                count = unread,
                                                busy = markingAll,
                                                onClick = { markAllRead() },
                                            )
                                        }
                                    }
                                    items(items, key = { it.id }) { item ->
                                        NoticeRow(
                                            item = item,
                                            onClick = { openMessage(item) },
                                        )
                                    }
                                    if (!lastPage) {
                                        item(key = "more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 14.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (loadingMore) {
                                                    InlineLoading(size = 20.dp)
                                                } else {
                                                    TextButton(onClick = { scope.launch { loadMessages(page + 1) } }) {
                                                        Text("加载更多")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = {
                Text(
                    text = item.subTypeName.ifBlank { item.typeName },
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column {
                    Text(text = item.content, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = listOf(item.typeName, noticeTimeText(item.ctime))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun ErrorBlock(text: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

/** 「N 条未读通知」那一条：点一下全部标已读。 */
@Composable
internal fun UnreadBanner(count: Int, busy: Boolean, onClick: () -> Unit) {
    // 不用 primaryContainer：主题里没定义它，会落到 Material 的紫调，和这里的蓝对不上。
    // 用「主题色 12% 底 + 主题色字」，和别处的 Tag 小牌子是同一套做法。
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = !busy, onClick = onClick),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$count 条未读通知",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (busy) {
                InlineLoading(size = 16.dp)
            } else {
                Text(
                    text = "全部标为已读 ›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun NoticeRow(item: NoticeItem, onClick: () -> Unit) {
    val unread = !item.isRead
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // 未读点：不用颜色区分正文，暗色模式下也能看清
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (unread) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.subTypeName.ifBlank { item.typeName },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 标题自己吃掉剩下的宽度：再塞一个 Spacer(weight) 会把标题挤成半个宽，
                        // 稍微长一点的「黑名单解除审核通过」就要省略号
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = noticeTimeText(item.ctime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = item.typeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 33.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun WorkRow(item: WorkItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.route.isNotBlank(), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标只有服务端给了才画：学生账号的待办恒为空，这个分支实际还没见过真实取值
        if (item.icon.isNotBlank()) {
            AsyncImage(
                model = item.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = item.workDes,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (item.count > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = item.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        if (item.route.isNotBlank()) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
