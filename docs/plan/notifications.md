# 「我的消息」与系统通知（规划，待审核）

官方 `#/needDealt` 叫「阅办」，装了两样东西：**待办**（要我去处理的事）和**消息**（系统发给我的通知）。
对普通学生来说待办恒为空，所以我们要做的就是消息那一半——在应用里叫 **「我的消息」**，
入口放「我的」页（符合「我的 X」命名规则，见 [organization.md](../design/organization.md)）。

现状：接口已实测（2026-11-15），界面、通知都还没做。
调查报告见 [docs/research/yueban.md](../research/yueban.md)，
字段与坑见 [docs/api/win-9xueqi-api.md](../api/win-9xueqi-api.md) 的「我的消息」一节。

## 协议：没有 WebSocket，也没有轮询（2026-11-15 实测）

| 验法 | 结果 |
| --- | --- |
| 抓 `#/needDealt` 真实页面的网络请求 | 18 个接口全部是 `xmlhttprequest`（HTTP），没有一个 WS 连接 |
| 5 个 shell bundle（`app` / `vendor` / `manifest` / `yb_h5` / `webchat`）全量 grep | `WebSocket` 0 处、`wss://` 0 处 |
| 415 个按需 chunk 全量下载（`static/js/<id>.<hash>.<buildStamp>.js`）后 grep | `WebSocket` **0 处** |

未读数只在**打开页面的那一刻**拉一次，之后不再变化——网页挂着不动，永远收不到新消息。

结论：**没有任何可以复用的推送通道**，系统通知只能自己轮询。

**2026-11-15 与用户确认：不做服务器方案。** 理由：所有人从同一个 IP 集中轮询容易被判异常请求；
自建服务器无法保证 token 不泄露；服务器留给其他功能。自建推送（FCM/厂商推送）同样不可行。

## 范围（2026-11-15 用户审核确定）

官方的页面是**待办 + 消息**两块，两块都要做（管理员未必不是未来的目标用户，
不能因为「学生恒为空」就砍掉）。进页面默认停在待办，切 tab 到消息。

| 阶段 | 内容 | 依赖 |
| --- | --- | --- |
| M1 | 消息列表页：分页、下拉刷新、未读样式、「N 条未读通知」一键全读 | 只读接口 + `read-all` |
| M2 | 点开消息：自动标已读 + 按落点跳活动/组织详情，认不出的回退官方页面 | `notice/read` |
| M3 | 待办 tab：`processing/list` 的 `{icon, workDes, count, route}` + 空状态「暂无待办内容」 | 只读接口 |
| M4 | 系统通知：`WorkManager` 轮询 + 本地去重 + 通知渠道 | 只读接口 |
| M5 | 设置：开关与间隔、免打扰时段 | — |

待办排在通知之后：对现在的用户它恒为空，但结构简单（列表 + 空状态），
等有组织账号用户时立刻有用。**已办 tab 暂不做**——只有组织账号（`identity=2`）能看到，
接口 `activity/activity/listByTeacher` 没有任何样本。

**明确不做的**：管理员视角消息、清空与单条删除（写操作，对用户无价值）；
消息列表不做分组/筛选（官方也只有时间倒序）。

## 设计

**入口**：「我的」页列表里加一行「我的消息」，位置在「我的组织」之后、「我的附件」之前。
未读角标第一版**不加**——加了就要为它单独轮询，等 M4 有了轮询再顺带显示。

**页面结构**：进页面是官方那个形状——顶部两个 tab（待办 / 消息），默认待办。
组织账号的第三个 tab（已办）先不做。待办 tab 直接渲染服务端给的
`{icon, workDes, count, route}`：`route` 能映射到我方页面的就跳，
映射不上（官方 `/activityAudit/xxx` 这类审核页）回退「打开官方页面」。
学生账号看到的是空列表 + 「暂无待办内容」，这本身就是正确状态，不要写成报错。

**数据层**：新增 `NotifyRepository`（`list` / `unreadCount` / `markRead` / `markAllRead`）
与 `ProcessingRepository`（`statistics` / `list`）。
列表项模型 `Notice(id, type, typeName, subType, subTypeName, content, isRead, ctime, jumpInfo, managerId)`；
待办项模型 `WorkItem(icon, workDes, count, route)`。
解析注意三个坑：响应**没有 `lastPage`**（自己算 `pageNum * pageSize >= total`）；
`dynamic/unread/count` 返回的是**字符串**；`showDelete` 是**字符串** `"false"`。

**落点**：只做三种——
`type=2` 且 `managerId != 我` → 活动详情；
`type=3` 且 subType ∈ {13, 19} → 「我的组织」；`type=3` 其它 → 组织详情；
其余（含 `type=162` 黑名单通知）→ 只标已读、不跳转，或回退「打开官方页面」。

**轮询（M4）**：`WorkManager` 的 `PeriodicWorkRequest`（系统下限 15 分钟），间隔可选
15 / 30 / 60 分钟或关闭，默认 30 分钟；另外冷启动、每次切回前台各拉一次。
Doze 与国产 ROM 会把它推后，所以文案只说「定期检查」，**不承诺实时**。
本地快照（DataStore）记「已弹过的消息 id + 上次拉取时间」用于去重，
保证同一条只弹一次、重装后也不把历史一次性刷出来。

**通知（M4）**：一个渠道「通知」，重要性默认 `DEFAULT`（用户可调到横幅）。
标题用 `subTypeName`，正文用 `content`；`isRead == 1` 的不弹；
多条同时到达时先发一条摘要（「3 条新消息」），点开进列表页。
Android 13+ 需要 `POST_NOTIFICATIONS`，在用户第一次打开「通知」开关时申请，
被拒绝时开关置灰并说明去系统设置打开，不做二次骚扰。

**边界**：退出登录、切换学校、切换账号都要清空本地快照并取消已排队通知；
服务端 `unReadCount` 与本地统计不一致时以服务端为准，不自己回算。

## 待验证（做 M1/M2 时必须先补）

- `notice/read`、`notice/read-all`、`notice/clean` 的实际返回与副作用（参数来自源码，未执行过）
- 全部已读后 `unReadCount` 是否立刻变 0、列表 `isRead` 是否同步
- `type=3` 的 `jumpInfo` 在本应用里是组织 id 还是字符串（样本里是 `"17085"` 字符串，活动是数字）
- 还有哪些 `subType` 会真的出现（目前只见过 6 种组合）
- 待办项的 `route` 长什么样（`/activityAudit/xxx` 之类只是推测），以及 `count` 与 `statistics` 是否对得上

## 里程碑与验收

| 阶段 | 验收方式 |
| --- | --- |
| M1 | 真机：分页翻到底不出错、下拉刷新、「N 条未读通知」点一下变已读 |
| M2 | 真机：点一条学时发放 → 活动详情；点一条加入组织 → 我的组织；点黑名单通知 → 只标已读 |
| M3 | 真机：学生账号进待办 tab 看到「暂无待办内容」而不是报错/白屏 |
| M4 | 真机 + 后台：关掉应用等 30 分钟收到通知；同一条不重复弹 |

每个阶段一个独立提交，版本号按需推进，不擅自发版。
