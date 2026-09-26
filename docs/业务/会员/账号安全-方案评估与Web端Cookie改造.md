# 账号安全：方案评估 与 Web 端 Cookie 鉴权改造

> 撰写 2026-09-26 · 状态：**待决策，未实施**
> 输入：一份外部"C 端设备信任 + 2FA + 风控"通用方案；本仓会员登录 / 设备 / 会话代码（结论均对着代码读过）。
> 原理部分见知识库：`docs/知识库/Web鉴权-Cookie与浏览器安全边界.md`、`docs/知识库/设备身份-Web与原生的能力边界.md`。

---

## 0. 结论

1. 外部方案技术上都可行，但**约六成本仓已有**，且在设备身份上现有做法更稳（服务端 HMAC 签发，而非客户端软指纹）。
2. 外部方案的核心新增——**"新设备一律 2FA"——现在做不了**：短信尚未接入服务商（`UnavailableSmsSender` 调用即抛），手机号 + 密码用户收不到第二因子。
3. 本仓盗号 / 买号的变现路径是 **兑换积分（`/mall/redeem`）+ 改收货地址（`/delivery/{id}/address`）**。
   风险集中在**资产出口**而非登录，所以投入产出最高的是**资产出口二次验证**，不是登录 2FA。
4. Web 端 Cookie 改造是一次正确的**凭证保管加固**（防 XSS 偷令牌、避开 Safari 7 天清理、设备注册幂等），
   但**对撞库、羊毛、买号帮助不大**。优先级排在资产出口二次验证之后。

---

## 1. 外部方案 vs 现状

| 外部方案条目 | 本仓现状 | 判断 |
|---|---|---|
| 按软指纹计数防撞库 | 已有且更强：`dv_` 设备令牌由服务端 HMAC 签发（`solvela-auth/.../device/DeviceTokenCodec.java`）；`DeviceGuard` 管登录频次 / 失败次数 / 一机多号 / 批量注册，带 dry-run | **不要**换成客户端软指纹——脚本每次换一个，计数永远是 1 |
| 账号维度失败限制 | 已有（`t_member_operation_limit`，排在验密码之前） | 已覆盖 |
| 人机滑块 | C 端无任何验证码 | 缺口，等 dry-run 数据证明有撞库流量再接 |
| IP 维度登录限频 | 注册有、登录无；"不存在的账号被反复尝试"无处记录（`MemberAuthService.authenticate` 查无此人分支的注释自己承认） | 缺口，成本低 |
| 新设备一律 2FA | 反向：默认放行，设备进"观察档"才多验一道（`MemberAuthService.checkDeviceChallenge`） | 核心分歧，见 §0.3 |
| challenge_ticket | 无。观察档是无状态的：客户端带密码 + 验证码再调一次 `/login` | 值得做，见 §2.2 |
| Access 15m + Refresh 轮转 | 不透明令牌存 Redis，TTL 30 天，每请求查、可即时吊销 | 收益低、坑多（多标签页 / 重试并发会误判"重放"导致全量下线），**不建议** |
| 设备管理 / 踢下线 | 已有：`/auth/sessions`、`revoke`、`revokeOthers`；重置密码、冻结会吊销全部会话 | 已覆盖 |
| 敏感操作 step-up | 仅换绑邮箱 / 手机要求当前密码或旧码 | 推广到资产出口 |
| Impossible Travel | 网关解析不了 IP 归属地（`MemberLoginService.sessionContext` 里 region 传 null） | 往后排 |
| 新设备登录通知 | 无 | 值得做，便宜 |
| Passkey | 无 | 往后排；微信内置浏览器支持差，只能作可选增强 |
| 新建 `user_trusted_devices` 表 | `t_device` 已存在 | 不建议照搬；真要存信任关系，加 `(member_id, device_id, trusted_at, revoked)` 关联表即可 |

---

## 2. 建议做的（按投入产出排序）

### 2.1 资产出口二次验证（约 3–5 人日）
兑换下单、改收货地址时，若当前设备对该会员是"首次出现 / N 天内新设备"，要求密码或验证码；
通过后签发 5 分钟一次性 `reauth_proof`（Redis）。
"新设备"直接复用 `t_device` 与登录日志里的 `(member_id, device_id)` 历史判断。先走已接通的邮箱码。

### 2.2 观察档改为 challenge_ticket（约 2–3 人日，前后端）
- 现在观察档要求客户端把密码留在内存里再发一次。
- 更要紧：`/sms/code` 的 LOGIN 场景**匿名可调**，号码存在就真发（`solvela-member/.../sms/MemberSmsCodeIssuer.java` 的场景分派）。
  所以 `checkDeviceChallenge` 注释里"验码排在密码之后，防短信轰炸"并不完全成立——轰炸者不必走登录接口。
- 改为：密码通过 → 发 ticket → **凭 ticket** 发码、验码。发码与"已证明知道密码"绑定。短信接入后尤其值钱。

### 2.3 登录 IP 维度限频 + 不存在账号的尝试计数（约 1–2 人日）
照 `DeviceGuard` 写法加两条规则，先 dry-run。

### 2.4 新设备登录通知（约 1–2 人日）
登录成功时设备对该会员为新，异步发邮件 / 站内信。邮件通道已有。

### 前置依赖
- **短信服务商接入**——不只是本方案的前置，手机号注册目前任何人都能拿别人的号建号（`MemberLoginController.register` 注释已标 🔴）。优先级高于本文任何一项。
- **C 端行为验证码**——等数据证明需要再接。

---

## 3. Web 端 Cookie 鉴权改造（设计稿）

### 3.1 前提条件（已核实）
- **前端与 API 同源**：nginx 把 `/api` 反代到网关（`deploy/nginx/nginx.conf`），前端 `.env.*` 全是相对路径 `/api`。第一方 cookie，无 CORS / 第三方 cookie 问题。
- **全站 HTTPS**：cloudflared 对外提供。
- **同站兄弟子域**：`app.` 与 `admin.`（`deploy/cloudflared/config.yml`）同属一个 site，`SameSite` 挡不住彼此——需要 `__Host-` 前缀 + 来源校验。

### 3.2 两个 cookie

| cookie | 对标 B 站 | 内容 | 属性 |
|---|---|---|---|
| `__Host-sv_dv` | buvid3 | 现有 `dv_` 设备令牌，格式不变 | HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=400 天 |
| `__Host-sv_sess` | SESSDATA | 现有 `mb_` 会话令牌，格式不变 | HttpOnly; Secure; SameSite=Lax; Path=/; "记住我" → Max-Age=30 天，否则会话 cookie |

- 令牌格式与 Redis 存储**一行不改**，变的只是放在哪。
- **CSRF 不用 csrf cookie**，改用 `Sec-Fetch-Site` / `Origin` 校验（理由见知识库 §4.3）：前端零改动、不依赖 CORS 配置、直接挡兄弟子域、Postman 无感。
- **请求头方式保留**（`Authorization`、`X-Device-Token`），留给原生 App 与自动化测试；服务端读取顺序：先请求头，后 cookie。
- 不照搬 B 站的：refresh_token 轮换（二期再议）、`buvid_fp` 指纹（以后直接接商业厂商）。

### 3.3 后端改动（solvela-app 网关）
1. 新增 cookie 配置类：名称、是否 Secure、有效期；按环境配置（本地 `http://localhost` 要能关 `Secure` 与 `__Host-`）。
2. `DeviceFilter` / `AuthenticationFilter`：先请求头、后 cookie，并在 request 属性记下认证来源。
3. 新增来源校验过滤器：仅对**靠 cookie 认证的写请求**生效，规则见知识库 §4.3。请求头认证直接跳过。
4. `/device/register` 改为幂等：请求已带有效设备令牌（cookie 或请求头）则复用，只回写 cookie。顺带修掉现在"非幂等、靠前端在途 promise 去重"的问题。
5. 登录 / 注册：`Set-Cookie` 下发会话；**Web 端响应体不再返回令牌**。
6. 退出：吊销后清会话 cookie，**保留设备 cookie**（退出账号 ≠ 换设备）。
7. 🔴 **统一"当前令牌"的读取**：`MemberLoginController.currentToken()` 目前只读 `Authorization` 头。不改的话切到 cookie 后：
   "下线其他设备"会**把自己也踢掉**、"我的登录设备"标不出本机、退出吊销不到当前令牌。必须写测试钉住。

### 3.4 前端改动（solvela-app-web）
1. `api/http.ts`：Web 端不再设 `Authorization` / `X-Device-Token`（同源自动带 cookie）。
2. `stores/auth.ts`：前端读不到令牌，登录态由启动时 `/auth/me` 决定；"记住我"作为登录参数交给服务端。
3. `api/device.ts`：每次启动调一次 `/device/register`（已幂等），不再读写 localStorage。
4. 迁移完成后删除 `utils/token-storage.ts`、`utils/device-storage.ts`。

### 3.5 迁移：不掉线、设备 ID 不断档
- 发现旧 `dv_`：以请求头带着调 `/device/register`，服务端把**同一个令牌**写进 cookie，设备 ID 连续；删本地副本。
- 发现旧会话令牌：调 `/auth/session/adopt`，服务端校验后把同一个令牌写进 cookie；删本地副本。
- 30 天后最后一批旧令牌过期，迁移代码删除。

### 3.6 必须守住的约束
- 🔴 **不得存在任何"cookie 会话 → 响应体令牌"的接口。** `/auth/session/adopt` 只能单向（请求头 → cookie），否则 XSS 调一下就把 HttpOnly 保护的令牌拿出来了。写测试钉住。
- 登录成功**必须签发新会话**，不沿用登录前的（防会话固定）——`tokenStore.issue` 现已满足，改代码时保住。
- GET 接口不得有副作用（`SameSite=Lax` 放行跨站顶层 GET）。
- 网关**永远不要**配置 `allowCredentials(true)` + 任意来源的 CORS。管理端 `CorsFilterConfig` 在 dev / test 就是这种配置（目前无实际风险：只在管理端、且管理端用请求头传令牌），**不要把它挪进网关**。

### 3.7 测试方式
- **Postman / curl**：不带 `Sec-Fetch-Site` 与 `Origin`，来源校验放行；登录后 Postman 的 cookie jar 自动携带。也可声明 `deviceType=APP` 走请求头模式，令牌在响应体返回。
- **Swagger UI**：同源部署时浏览器带 `Sec-Fetch-Site: same-origin`，通过。

### 3.8 实际收益 / 解决不了的
- ✅ XSS 读不到登录令牌，无法复制到别处长期使用；
- ✅ 设备身份不受 Safari 7 天清理；
- ✅ 设备注册幂等；"记住我"语义更干净；
- ❌ 用户清除网站数据后照样掉线、变新设备（与 B 站相同）；
- ❌ 撞库、羊毛、买号——靠 §2 与 `DeviceGuard`、风控链。

### 3.9 工作量
约 1.5 周：后端 3–4 天，前端 2–3 天，迁移 + 测试 + 联调 2 天。

---

## 4. 待拍板
1. 先做 §2.1 资产出口二次验证，还是先做 §3 Cookie 改造？（本文建议前者）
2. §3 中 Web 端响应体不返回令牌（建议：是）。
3. refresh_token 轮换放二期（建议：是）。
4. 是否有原生 App 计划——有则设备绑定按原生方式做（Keystore + attestation），见知识库《设备身份》§2。
5. 是否评估商业风控（影子模式试用），挂进 `solvela-risk` 的 `RiskChainEngine` 作为发奖前的一个 filter。
