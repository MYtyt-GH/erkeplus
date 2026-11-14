# 教务系统 App 接口（`jwglyd`，2026-11-15 实测）

- 目标站点：`https://jwglyd.aust.edu.cn`（**教务 App 的 H5 + JSON 接口**，不是 `jwgl` 那个网页版教务）
- 最近验证：2026-11-15（浏览器登录态内只读请求 + 前端 bundle 源码；未执行任何写操作）
- 结论：接口干净、**校外可用**、无验证码（当前），课表一次拿到整周数据

> 来源说明：本轮事实来自 `https://jwglyd.aust.edu.cn/app-web/` 的 `main.*.js`
> 与真实调用；签名算法用自写的 MD5 复现并被服务端接受（`err_code=00000`），不是猜的。
>
> 客户端实现：`android/app/src/main/java/gitee/leolin_me/erkepp/data/eams/`，
> 其中 `EamsProtocol.kt` 就是下面这套签名/信封的 Kotlin 版本，配了独立向量单测
> （`EamsProtocolTest`，期望值用 .NET 的 MD5 单独算过，不是自证）。

## 基础事实

| 项 | 结论 |
| --- | --- |
| 接口前缀 | `https://jwglyd.aust.edu.cn/app-ws/ws/app-service` |
| 方法 | 全部 **POST**，`Content-Type: application/x-www-form-urlencoded` |
| 请求体 | 参数按 key 升序拼成 `&k1=v1&k2=v2…`，**开头就是一个 `&`**，不是 JSON |
| 鉴权 | 请求头 `token: <token>`（未登录时为空串）；业务参数里也常带一份 `token` |
| 公共参数 | `timestamp`（毫秒）、`random`（0..99999）、`sign` |
| 签名 | `sign = MD5(secret + "|" + v1 + "|" + v2 + … + timestamp + "|" + random).toUpperCase()`，`v` 为业务参数值（按 key 升序，各自后面带 `|`），`sign`/`timestamp`/`random` 自身不参与 |
| secret | 登录前是固定常量 `supwisdom_eams_app_secret`；登录后用响应里的 `secret`（前端叫 `dynamicSecret`） |
| 响应 | `{err_code, err_msg, business_data}`，`business_data` 是 **Base64 的 UTF-8 JSON**，`err_code == "00000"` 才算成功 |
| 错误码 | `"20"` = token invalid；`"03000"` = 业务错误（前端弹 `err_msg`）；`"03010"` = 验证码错误。**`err_code` 是字符串且长度不固定**，别按整数比较 |
| 登录态存储 | 前端放 `localStorage.currentUser`：`{token, dynamicSecret, accessMethod, identity, roleId, user_info{…}}` |

## 已验证的接口

| 端点 | 参数 | 返回 | 验证日期 |
| --- | --- | --- | --- |
| `/verify-code` | 无 | `{enable:false}` | 2026-11-15 |
| `/common/get-semester` | `biz_type_id=1` | `{cur_semester_id, semesters[]{id,year,name,season,start_date,end_date,week_start_day}}` | 2026-11-15 |
| `/course/schedule/get-max-unit` | `biz_type_id=1` | `{max_unit: 12}` | 2026-11-15 |
| `/student/course/schedule/get-course-tables` | `biz_type_id=1&semester_id&start_date&end_date` | 见下 | 2026-11-15 |
| `/login` | `{user_code, passwd: Base64(明文)}` | **假账号**实测：`err_code=03000`、`err_msg=用户名或密码不正确！` | 2026-11-15 |

学期样本（当前学期 id=340）：`{id:"340", year:"2026-2027", name:"1", season:"秋", start_date:"2026-08-31", end_date:"2027-01-17", week_start_day:1}`。
`week_start_day=1` 表示周一为一周之首；**第 N 周 = `floor((日期 - start_date) / 7) + 1`**
（实测 2026-11-09 ~ 11-15 是第 11 周）。

## 课表返回结构（扁平数组，不是网格）

`get-course-tables` 返回**一次课一条记录**的数组：

```json
{"course_code":"1302001111","course_name":"数学分析(一)","date":"2026-11-12",
 "start_unit":7,"end_unit":8,"start_time":"16:00","end_time":"17:40",
 "lesson_id":401117,"lesson_no":"1302001111.03",
 "rooms":[{"id":358,"code":"045103","name":"明理北103","building":"明理北楼","address":"校本部 明理北楼 明理北103"}],
 "teachers":[{"id":2749,"code":"2017027","name":"某老师"}],
 "weeks":"5,6,7,8,9,10,11,12,13,14,15,16,17","weekstate":"5-17",
 "teachclass_std_count":83}
```

- 16 条样本里上述字段**全部非空**，没有需要兜底的空字段。
- `rooms` / `teachers` 是数组（合班、多教师会多于一个）。
- `start_unit` / `end_unit` 是第几节（1..12），`start_time` / `end_time` 是钟点，**两个都给**，客户端不用自己算节次时间表。
- 单双周、调课都由服务端拆成多条记录（同 `lesson_id` 不同 `weeks`／`date`），客户端照收。

## 登录链路（H5 源码核对，**未实际执行**）

1. `POST /verify-code`（用固定 secret 签名）→ `{enable, key, imageArray}`；`enable=false` 时不需要验证码（当前就是 false）。
2. 不需要验证码：`POST /login`，参数 `{user_code, passwd}`，其中
   **`passwd = Base64(UTF8(明文密码))`**（`CryptoJS.enc.Base64.stringify(CryptoJS.enc.Utf8.parse(pwd))`）。
   **这是编码不是加密**，安全性全靠 https。
   - 已用**假账号**（`user_code=0000000000`）实际调过一次：返回 `err_code=03000`、
     `err_msg=用户名或密码不正确！`。说明服务端确实按 Base64 解出了密码再校验，
     客户端直接把 `err_msg` 展示给用户即可。
   - 单次请求没有触发频控，但限流阈值仍未探明。
3. 需要验证码时走 `/login-with-verify-code`，参数多 `verify_code` 与 `verify_code_key`（= `/verify-code` 的 `key`）。
4. 成功返回 `{token, secret, access_method, cur_identity, roleId, user_info{user_code,user_name,gender,birthday,…}}`，
   之后所有请求的 `secret` 换成这个 `secret`。

> 未验证项：真实凭据的成功登录（返回字段的取值、`user_info` 的结构）、`imageArray` 的图片格式、
> token 有效期——都要等真机输入一次才知道。失败分支已验（见上）。

## 网络与可达性

| 端点 | 域名 | 解析 | 校外 |
| --- | --- | --- | --- |
| 新教务（本文件） | `jwglyd.aust.edu.cn` | `210.45.144.172 / 19 / 18`（**公网 IP**） | 用户报告可用 |
| 老教务（网页版） | `jwgl.aust.edu.cn` | 含 `172.16.6.34`（校内私网） | 用户报告**不可用** |

- 同一路径 `https://` 能连、`http://` 反而不行（与老教务相反）：老教务 `http` 通、`https` 不通，别套用。
- 客户端必须按「校外可能拿不到」设计：**失败沿用缓存 + 退避**，绝不能把空结果当真实课表覆盖缓存。

## 尚未验证

- 真实登录（token 有效期、密码错误码、是否需要二次验证）
- 验证码开启时的 `imageArray` 结构与识别方案（当前 `enable=false`）
- 频控：未测出限流阈值；按保守值（请求间隔 ≥1s、失败退避）实现
- `/course/schedule/get-unscheduled-lessons`（未排课：实验课、线上课）参数未确定，传 `semester_id` 返回空
