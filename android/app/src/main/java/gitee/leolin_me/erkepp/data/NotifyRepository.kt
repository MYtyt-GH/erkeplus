package gitee.leolin_me.erkepp.data

import gitee.leolin_me.erkepp.AppLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 「我的阅办」：官方 `#/needDealt` 那一页，装了两样东西。
 *
 * - **待办** `activity/processing/list`：要我去处理的事（审核、签到、交作业…）。
 *   行由服务端给四件套 `{icon, workDes, count, route}`，连跳转路径都是服务端给的。
 * - **消息** `message/notice/list`：系统发给我的通知，时间倒序分页。
 *
 * 学生账号的待办恒为空（接口实测，见 docs/api/win-9xueqi-api.md「我的消息」一节），
 * 空列表是**正确状态**，不是报错。
 *
 * 没有 WebSocket 也没有轮询：未读数只在打开页面时拉一次，所以这里也不做「自动变红点」的幻想。
 */
data class NoticeItem(
    val id: Long,
    /** 大类，取值见 [NotifyRepository.TYPE_ACTIVITY] 那几个常量。 */
    val type: Int,
    /** 大类的名字，服务端给（「活动通知」「组织通知」…），界面直接用，不自己写死。 */
    val typeName: String,
    val subType: Int,
    /** 细分类的名字，就是现成的短标题（「学时已发放」「开始签到、签退」…）。 */
    val subTypeName: String,
    val content: String,
    val isRead: Boolean,
    /** 毫秒时间戳。 */
    val ctime: Long,
    /** 落点 id：`type=2` 是活动 id，`type=3` 是组织 id。服务端两种类型都混着给，统一按字符串收。 */
    val jumpInfo: String,
    /** 操作人 id；等于当前用户时官方走「管理员视角」，我们没有那个页面。 */
    val managerId: Long,
)

data class NoticePage(
    val total: Int,
    val list: List<NoticeItem>,
    val lastPage: Boolean,
)

/** 待办的一行。四个字段的名字来自 H5 源码，真实取值**尚未见过**（学生账号恒为空）。 */
data class WorkItem(
    val icon: String,
    /** 待办的名字，例如「活动审核」。 */
    val workDes: String,
    /** 这一项有几条待处理，官方拿它画行尾角标。 */
    val count: Int,
    /** 官方 H5 的路由，例如 `/activityAudit/xxx`。**由服务端给，不猜**。 */
    val route: String,
)

/** 点开一条消息之后能落到哪。 */
sealed interface NoticeTarget {
    /** 活动详情（只读，学生视角）。 */
    data class Activity(val id: Long) : NoticeTarget

    /** 组织详情。 */
    data class Organization(val id: Long) : NoticeTarget

    /** 「我的组织」列表（加入组织这类通知，官方落在 `#/myApplyorg`）。 */
    data object MyOrganizations : NoticeTarget

    /** 没有对应页面：只标已读，正文由本地弹窗给全。 */
    data object None : NoticeTarget
}

/**
 * 落点判定，照官方 H5 的分支表（见 docs/api 的「点一条消息落到哪」）收窄成三种能接的：
 *
 * - `type=2` 活动通知：`managerId` 不是我就进活动详情。是我时官方去管理员视角，我们没有，归 [NoticeTarget.None]。
 * - `type=3` 组织通知：`subType` 13（成功加入）/ 19 → 「我的组织」；其余进组织详情。
 * - 其它（含 `type=162` 黑名单通知）：官方也不跳，归 [NoticeTarget.None]。
 */
internal fun noticeTargetOf(notice: NoticeItem, myUserId: Long): NoticeTarget {
    val id = notice.jumpInfo.trim().toLongOrNull() ?: 0L
    return when (notice.type) {
        NotifyRepository.TYPE_ACTIVITY -> {
            if (notice.managerId == myUserId) {
                NoticeTarget.None
            } else {
                if (id > 0) NoticeTarget.Activity(id) else NoticeTarget.None
            }
        }

        NotifyRepository.TYPE_ORGANIZATION -> {
            if (notice.subType == NotifyRepository.ORG_SUBTYPE_APPLY ||
                notice.subType == NotifyRepository.ORG_SUBTYPE_APPLY_OTHER
            ) {
                NoticeTarget.MyOrganizations
            } else {
                if (id > 0) NoticeTarget.Organization(id) else NoticeTarget.None
            }
        }

        else -> NoticeTarget.None
    }
}

class NotifyRepository(private val api: ApiClient) {

    /** 消息列表。`pageNum` / `pageSize` 必填，不传服务端直接回 `10002 参数错误`。 */
    suspend fun list(token: String, page: Int, pageSize: Int = PAGE_SIZE): NoticePage {
        val response = api.get(
            path = "message/notice/list",
            params = buildJsonObject {
                put("pageNum", JsonPrimitive(page))
                put("pageSize", JsonPrimitive(pageSize))
            },
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"] ?: return NoticePage(0, emptyList(), true)
        val parsed = parseNoticePage(data, page, pageSize)
        // 只打条数，不打正文：正文里有活动名、组织名
        AppLog.d(
            "notify",
            "list page=$page size=${parsed.list.size} total=${parsed.total} " +
                "unreadOnPage=${parsed.list.count { !it.isRead }}",
        )
        return parsed
    }

    /** 未读数。服务端给的是**整数**（广场那一个 `dynamic/unread/count` 反而是字符串）。 */
    suspend fun unreadCount(token: String): Int {
        val response = api.get(
            path = "message/notice/un-read-count",
            params = buildJsonObject {},
            token = token,
        )
        response.requireSuccess()
        val count = parseUnreadCount(response)
        AppLog.d("notify", "unread=$count")
        return count
    }

    /** 单条已读。点开一条 `isRead == 0` 的消息时自动发，界面随后重拉列表。 */
    suspend fun markRead(token: String, id: Long) {
        val response = api.post(
            path = "message/notice/read",
            params = buildJsonObject { put("id", JsonPrimitive(id)) },
            token = token,
        )
        response.requireSuccess()
        AppLog.d("notify", "read ok id=$id")
    }

    /** 全部已读：参数就是 `{id: 0}`（H5 里 `readNotice(0)`）。 */
    suspend fun markAllRead(token: String) {
        val response = api.post(
            path = "message/notice/read-all",
            params = buildJsonObject { put("id", JsonPrimitive(0)) },
            token = token,
        )
        response.requireSuccess()
        AppLog.d("notify", "read-all ok")
    }

    /** 待办列表。学生账号返回空数组，这是正常状态。 */
    suspend fun processing(token: String): List<WorkItem> {
        val response = api.get(
            path = "activity/processing/list",
            params = buildJsonObject {},
            token = token,
        )
        response.requireSuccess()
        val data = response.jsonObject["data"]
        val items = parseWorkItems(data)
        AppLog.d("notify", "processing count=${items.size}")
        return items
    }

    companion object {
        const val PAGE_SIZE = 10

        /** 活动通知（学时发放、报名结果、签到提醒…）。 */
        const val TYPE_ACTIVITY = 2

        /** 组织通知。 */
        const val TYPE_ORGANIZATION = 3

        /** 黑名单通知：官方点了也不跳转，只标已读。 */
        const val TYPE_BLACKLIST = 162

        /** 组织通知里的「成功加入组织」：官方落在「我的组织」。 */
        const val ORG_SUBTYPE_APPLY = 13

        /** 组织通知里的另一个「申请」类子类型，官方同样落在「我的组织」。 */
        const val ORG_SUBTYPE_APPLY_OTHER = 19
    }
}

internal fun parseNoticePage(
    data: JsonElement,
    page: Int = 1,
    pageSize: Int = NotifyRepository.PAGE_SIZE,
): NoticePage {
    val obj = data as? JsonObject ?: return NoticePage(0, emptyList(), true)
    val list = (obj["list"] as? JsonArray)?.mapNotNull { parseNoticeItem(it) }.orEmpty()
    val rawTotal = obj.noticeInt("total")
    // 响应里**没有 lastPage**（H5 读的就是这个不存在的字段，只能一直翻到空列表）。
    // 总数拿不到时退回「这一页没满就是最后一页」。
    val lastPage = if (rawTotal > 0) page * pageSize >= rawTotal else list.size < pageSize
    return NoticePage(
        total = if (rawTotal > 0) rawTotal else list.size,
        list = list,
        lastPage = lastPage,
    )
}

internal fun parseNoticeItem(element: JsonElement?): NoticeItem? {
    val item = element as? JsonObject ?: return null
    val id = item["id"]?.let { (it as? JsonPrimitive)?.contentOrNull }?.toLongOrNull() ?: 0L
    if (id <= 0) return null
    val extras = item["extras"] as? JsonObject
    return NoticeItem(
        id = id,
        type = item.noticeInt("type"),
        typeName = item.noticeStr("typeName"),
        subType = item.noticeInt("subType"),
        subTypeName = item.noticeStr("subTypeName"),
        content = item.noticeStr("content"),
        // 0 未读、1 已读；别的值按未读处理，宁可多标一次已读也不要漏
        isRead = item.noticeInt("isRead") == 1,
        ctime = item.noticeLong("ctime"),
        jumpInfo = extras?.noticeStr("jumpInfo").orEmpty(),
        managerId = extras?.noticeLong("managerId") ?: 0L,
    )
}

/**
 * 未读数。外层是 `{code, data}` 信封，但为了不被信封结构变化打回，这里两处都认；
 * 值可能是整数也可能是字符串（同一族接口里两种都出现过）。
 */
internal fun parseUnreadCount(element: JsonElement): Int {
    val root = element as? JsonObject ?: return 0
    val holder = root["data"] as? JsonObject ?: root
    val primitive = holder["unReadCount"] as? JsonPrimitive ?: return 0
    return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
}

/** 待办列表的 `data` 是一个数组；取不到就当空列表（学生账号的正常形态就是空）。 */
internal fun parseWorkItems(data: JsonElement?): List<WorkItem> {
    val array = data as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val name = item.noticeStr("workDes").ifBlank { item.noticeStr("name") }
        if (name.isBlank()) return@mapNotNull null
        WorkItem(
            icon = item.noticeStr("icon"),
            workDes = name,
            count = item.noticeInt("count"),
            route = item.noticeStr("route"),
        )
    }
}

private fun JsonObject.noticeStr(key: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.noticeInt(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull
        ?: (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        ?: 0

private fun JsonObject.noticeLong(key: String): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
