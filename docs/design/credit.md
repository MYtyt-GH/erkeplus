# 诚信值页

入口：「我的」页的资料卡区（两处可点，都进同一页）。对应官方 `#/honestValue`。

## 结构

分数与等级在最上面，下面四类记录，跟官方一致：

```
诚信值 97.78  [已被拉黑]           更新时间：2026.10.25
[黑名单原因整句]                    [查看申请]
待守约 | 已守约 | 已失约 | 申诉记录
记录条目 …（扣分/加分、时间、活动名）
```

- 等级不单独存：`credit` 落在 `dict/school/system-para/credit/list` 的哪个分数段就是哪个等级
- 被拉黑时顶部多一条原因和「查看申请」；原因是**一句带时间的中文**，整句展示，
  不要解析这句话算到期时间
- 规则弹窗一屏看完，不做折叠

## 接口

| 用途 | 接口 |
| --- | --- |
| 分数、等级、黑名单 | `GET credit/user/detail` |
| 记录（`type` 1 待守约 / 2 已守约 / 3 已失约） | `GET credit/user/list` |
| 申诉记录 | `GET credit/user/appeal-list`（**不是** `credit/user/list` 的 type=4） |
| 分数段字典 | `GET dict/school/system-para/credit/list` |
| 移出黑名单的申请详情 | `GET credit/user/blacklist/relieve/detail` |

字段与坑都记在 [docs/api](../api/win-9xueqi-api.md) 的「诚信值」与「移除黑名单」两节。

## 不单独做「更多」页（2026-10-29 实测）

官方的记录栏里还有一个「更多」。实测点它**没有发出任何新请求**，页面正文也不变，
全部数据仍然来自上面那几个只读接口；客户端这四个 tab 已经把同样四类记录列全了。
所以不单独开一个「更多」页面——多一个页面只会多一次点击，不会多出信息。

## 没有做的

- **申请移出黑名单**：接口已知（`POST credit/user/blacklist/relieve/submit`），但它是写操作，
  请求体编码没实测过。要做先在测试环境真提一次，规则见 [待办](../plan/backlog.md)
- 黑名单的「两个维度」字典（`fieldType=blackList` 返回 `day` / `num`）暂时没用到，
  等服务端按天/按次数给出不同原因时再说
