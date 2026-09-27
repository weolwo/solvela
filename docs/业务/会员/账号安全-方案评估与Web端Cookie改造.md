# 账号安全：方案评估 与 Web 端 Cookie 鉴权改造

> 撰写 2026-09-26 · 状态：**§2.1 已实施（observe 档）、§3 已实施、§4 浏览器安全头已实施（CSP 为 Report-Only），其余待决策**
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
| 人机滑块 | C 端无任何验证码 | ✅ 已实施（2026-09-27），见 §2.5 |
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

### 2.1 资产出口二次验证 —— ✅ 已实施（2026-09-26，observe 档）

**落地时的三处修订**（相对最初的设想）：
- **只拦「指定新的收件方」，不拦兑换。** 实物出口全部经过地址簿（兑换、补填发货都只收 `addressId`），
  守住地址簿的新增 / 修改就守住了所有实物出口；用已有地址兑换拿不走东西（寄回受害者家里）。
  唯一不经地址簿的是充话费（`targetAccount` 任意填）。拦截点因此是 `POST /address`、`PUT /address/{id}`、`POST /recharge/order`。
- **只接受邮箱验证码，不接受密码。** 撞库的人本来就知道密码。生产只开放邮箱注册，人人有已验证邮箱，不必等短信。
- **不发一次性 `reauth_proof`，而是记住「这台设备验证过」。** 验一次后这台设备受信任（180 天），同类操作不再打扰。

**什么叫受信任的设备**（`MemberStepUpService.check`）：
1. 在这台设备上通过过二次验证（Redis `mbr:trust:v:{会员号}`，不建表 —— 丢了只是再验一次）；或
2. 这台设备上该会员的**成功**登录早于 7 天（`t_member_login_log`，时间比较全在 SQL 里做，见铁律 10）。

两者都受「信任起算点」约束（`mbr:trust:since:{会员号}`）：重置密码、冻结、下线其他设备之后，此前的一切不再算数。
🔴 在新设备上点「下线其他设备」**不会**把自己洗白 —— 当前设备只有本来就受信任时才保留（有用例钉住）。

**调用链**：网关 `@StepUpRequired` + `StepUpInterceptor` → `MemberStepUpApi`（`/internal/member/step-up`）→ `MemberStepUpService`。
客户端收到 `403 STEP_UP_REQUIRED` 后，`http.ts` 统一弹 `StepUpDialog`（自动发码到已绑定邮箱）→ `/auth/step-up/verify` → **自动重试原请求**，页面代码无感。

**配置**：
| 键 | 默认 | 说明 |
|---|---|---|
| `solvela.app.step-up.mode` | `off`（四个环境均配为 `observe`） | `off / observe / enforce`，见 `StepUpProperties` |
| `solvela.member.step-up.trust-after` | `7d` | 老交情门槛 |
| `solvela.member.step-up.verified-ttl` | `180d` | 验证通过后的信任期 |

**切 enforce 的步骤**：
1. 前端带 `StepUpDialog` 的版本上线（已随本次提交）；
2. observe 跑一段时间，搜网关日志 `【二次验证】命中但放行[observe]`，看新设备用户被打扰的比例、集中在哪个接口；
3. 确认可接受后改 `mode: enforce`。回退 = 改回 `observe`。

**已知边界**：
- 设备令牌被读走后，攻击者就是「老设备」。§3 落地后它在 HttpOnly cookie 里，XSS 读不走了；本机恶意软件仍能读 cookie 文件（知识库《Web鉴权》§2.4）。
- 攻击者连邮箱一起拿下（密码复用）时挡不住。短信接入后可再加一个因子。
- 「下线**某一台**设备」只踢会话、**不撤那台的信任**；要彻底清掉请用「下线其他设备」或重置密码。后续可在契约上加 `revokeDevice`。
- enforce 档下会员服务不可用时，拦截点直接失败（不放行）—— 守资产出口的闸不能默认开门。

### 2.2 观察档改为 challenge_ticket —— ✅ 已实施（2026-09-27）
密码验对 → 签 5 分钟、绑设备的一次性凭票（`LoginChallengeStore`，Redis 只存摘要）→ `/auth/login/challenge/code` 凭票发码（发到票上的登录身份）→ `/auth/login/challenge/verify` 凭票验码即登录。码错可再试，错到作废则票一并作废（`CHALLENGE_EXPIRED`，回到输密码）。匿名短信 LOGIN 场景关闭。顺带修掉：旧前端的二次验证只会要短信码，邮箱+密码用户在观察档设备上永远过不去。

原设计稿：
- 现在观察档要求客户端把密码留在内存里再发一次。
- 更要紧：`/sms/code` 的 LOGIN 场景**匿名可调**，号码存在就真发（`solvela-member/.../sms/MemberSmsCodeIssuer.java` 的场景分派）。
  所以 `checkDeviceChallenge` 注释里"验码排在密码之后，防短信轰炸"并不完全成立——轰炸者不必走登录接口。
- 改为：密码通过 → 发 ticket → **凭 ticket** 发码、验码。发码与"已证明知道密码"绑定。短信接入后尤其值钱。

### 2.3 登录 IP 维度限频 + 不存在账号的尝试计数 —— ✅ 已实施（2026-09-27，上线即拦截）
`LoginIpGuard`：每 IP 每小时 60 次 / 失败 20 次 / 不存在账号 10 次（`solvela.member.login.ip-guard.*`），排在查会员之前，超限回 `IP_LIMITED`（措辞说「当前网络」而非账号）。
照 `DeviceGuard` 写法加两条规则，先 dry-run。

### 2.4 新设备登录通知 —— ✅ 已实施（2026-09-27）
`NewDeviceLoginNotifier`：这台设备上从没成功登录过、且不是注册时用的那台（注册时把设备号记进 Redis）、且请求有设备号时，异步发邮件（`member_new_device_login`，无任何链接）+ SYSTEM 站内信（`NEW_DEVICE_LOGIN`，关不掉）。开关 `solvela.member.login.notify-new-device`。

> 2026-09-27 上线实测修正：初版判据是「账号以前登录过」，但注册即登录且不写登录日志，一直用注册会话的会员在别处第一次登录时被当成新用户首登跳过——恰是盗号的典型场景。

原设计稿：
登录成功时设备对该会员为新，异步发邮件 / 站内信。邮件通道已有。

### 2.5 自建滑块验证码 —— ✅ 已实施（2026-09-27）
拦在**发邮箱 / 短信验证码**与**密码登录**之前，上线即拦截（不走观察期）。整套在网关，状态在 Redis：

```
受保护接口 → 403 CAPTCHA_REQUIRED
  → 前端 http 拦截器弹 CaptchaDialog → POST /captcha 取图（答案只存 Redis）
  → 拖动 → POST /captcha/verify → 通行票
  → 原请求带 X-Captcha-Token 自动重试（一张票放行一次，2 分钟过期）
```

- 出图只用 AWT 画形状、**不画文字**：生产镜像是 distroless，没有字体。
- 每张图只认一次答案：判题时用一段 Lua 原子「取出并删除」（没用 `GETDEL`：本地 Redis 版本不认）。
- 邮箱验证码登录不再额外拦：它前面「发码」那一步已经拖过了。
- 场景开关 `solvela.app.captcha.send-code` / `password-login`，容差 5px，同 IP 每分钟最多出 20 张图。
- 已知局限：挡通用脚本、不挡专门针对的脚本（缺口可被边缘检测找到，也不采轨迹）。升级路线见全景图鉴 §6.3。

#### 🔴 开发者工具里先看到一条 403 是预期行为，不是 bug
点「获取验证码」「登录」时，Network 面板里会先出现一条 `403 CAPTCHA_REQUIRED`，弹滑块、拖对之后同一个请求再发一次才成功。
这是**服务端发起挑战**（challenge-response）的标准做法，和 HTTP 的 `401 + WWW-Authenticate`、
本仓的 `STEP_UP_REQUIRED`、Cloudflare / AWS WAF 的人机挑战是同一个套路：

- **哪些操作要过滑块，只有服务端说了算**。前端不认识「哪些接口要滑块」这份清单，
  配置里关掉一个场景（`send-code` / `password-login`），前端一行不用改、也不会多弹一次；
- **那一条 403 没有任何副作用**：滑块检查排在所有业务逻辑之前 —— 不计登录失败次数、不发码、不碰 IP 闸门；
- 代价是多一次往返（几十毫秒，用户在拖滑块，感知不到）和控制台里一行红字。

曾考虑过改成「点按钮先弹滑块，再带着通行票一次发出」，2026-09-27 决定**不改**：
那要把场景清单在前端再写一份，两边一旦不一致，要么多弹、要么还得靠这条 403 兜底 —— 等于两套逻辑并存。

### 前置依赖
- **短信服务商接入**——不只是本方案的前置，手机号注册目前任何人都能拿别人的号建号（`MemberLoginController.register` 注释已标 🔴）。优先级高于本文任何一项。

---

## 3. Web 端 Cookie 鉴权改造 —— ✅ 已实施（2026-09-26）

**落地时相对设计稿的修订**：
- **来源校验对所有写请求生效，不只是「靠 cookie 认证的」。** 登录、注册这类匿名写接口也要挂，
  否则 evil.com 能替受害者提交一次攻击者自己账号的登录（登录 CSRF，知识库《Web鉴权》§4.3）。
  实现为拦截器 `CrossOriginGuardInterceptor`（排在所有拦截器最前），不需要记录「认证来源」。
- **Web 端靠请求参数 `useCookie: true` 选择 cookie 下发**，不传则照旧在响应体返回令牌（App / Postman / 自动化测试）。
- **前端「登着没有」靠一个本地提示 + 启动时 `/auth/me`。** 提示（`solvela.app.session.hint`）不是凭证，
  只决定要不要去问服务端，避免每个匿名访客都白吃一个 401。
- 服务端发现 cookie 里的令牌已失效时顺手清掉 cookie（页面脚本删不掉 HttpOnly cookie）。

**实现落点**：`WebCookieProperties`（`solvela.app.cookie.*`）、`RequestCredentials`（全网关唯一读写凭证处）、
`CrossOriginGuardInterceptor`、`MemberLoginController`（登录 / 注册 / 退出 / adopt）、`DeviceController`（复用 + cookie）；
前端 `api/http.ts`、`api/device.ts`、`stores/auth.ts`、`router/index.ts`、`utils/session-hint.ts`。

**配置**：
| 键 | dev | test / pre / prod |
|---|---|---|
| `solvela.app.cookie.secure` | `false`（http://localhost，cookie 名不带 `__Host-`） | `true`（`__Host-sv_sess` / `__Host-sv_dv`） |
| `solvela.app.cookie.cross-origin-guard` | `enforce` | `enforce` |
| `solvela.app.cookie.trusted-origins` | vite 的 5273 两个地址 | 空（由 X-Forwarded-Proto + Host 算出） |

⚠️ test / pre 的实际域名仓库里查不到，按 HTTPS 配了 `secure: true`。某个环境若走 http，表现是「登录成功、刷新就掉线」。

**验证**：`CookieSessionTest`（13 条，真端口真 Redis）+ 前端 `stores/__tests__/auth.spec.ts` 等；
并在浏览器端到端走通：注册 → 响应体 `accessToken: null`、`document.cookie` 为空 → 刷新仍登录 →
从另一站点（127.0.0.1）发起的表单 POST 与 fetch 均 403 且不影响登录 → 退出后 `/auth/me` 401 →
旧版 localStorage 令牌经 adopt 迁入 cookie、本地副本清除、用户不掉线。

---

以下为设计稿原文（保留作为取舍记录）。

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


## 4. 浏览器安全响应头 —— ✅ 已实施（2026-09-27）

HttpOnly 只是止损：有 XSS 时脚本偷不走令牌，但能**在页面里以用户身份直接发请求**，
而且它跑在用户自己那台受信任设备上，资产出口二次验证也拦不住它。所以要在 XSS 本身上再加一道。

| 头 | 作用 | 状态 |
|---|---|---|
| `Content-Security-Policy-Report-Only`（C 端） | `script-src 'self'`：内联脚本、`onerror=`、外域脚本不执行 | **Report-Only**，违规报到网关 `/csp-report`（日志搜【CSP 违规】，每分钟限 30 条） |
| `Content-Security-Policy: frame-ancestors 'self'` + `X-Frame-Options: SAMEORIGIN` | 防点击劫持 | **强制**（frame-ancestors 在 Report-Only 里会被忽略） |
| `Strict-Transport-Security: max-age=31536000` | 强制 HTTPS | 强制；刻意不带 includeSubDomains / preload |
| `X-Content-Type-Options: nosniff`、`Referrer-Policy` | 防 MIME 嗅探、不外泄路径 | 强制 |

- 配置在 `deploy/nginx/snippets/security-headers.conf`（两站共用）与 `csp-app.conf`（C 端）。管理端暂不挂 CSP。
- index.html 原来的内联主题脚本搬成了 `public/theme-init.js`（同步加载，深色模式不闪白）。
- 验证：临时 nginx 容器上各 location（含 `/assets/`、502、history 路由、管理端 401）都带齐安全头；
  浏览器里首屏零违规；注入的内联脚本与 `onerror` 均被识别；跨源 iframe 被拒、同源正常。

**切 CSP 强制执行的步骤**：观察网关日志里的【CSP 违规】一段时间（Cloudflare 注入的脚本、富文本里嵌的视频 iframe 都可能出现），
确认没有自己的资源被误报后，把 `csp-app.conf` 里的 `Content-Security-Policy-Report-Only` 改成 `Content-Security-Policy`。

**后台富文本的 `v-html`（3 处）**：按「后台可信」保持不过滤；CSP 强制之后其中的内联脚本本来就不会执行。
如需再加一道，做保存时检查（发现 `<script>`、`on*` 属性、`javascript:` 链接就拒绝保存，不改写内容）。

---

## 5. 待拍板
1. ~~先做 §2.1 还是 §3~~ —— 已先做 §2.1。
2. ~~§3 中 Web 端响应体不返回令牌~~ —— 已按「是」实施。
3. ~~refresh_token 轮换~~ —— 放二期。
4. 是否有原生 App 计划——有则设备绑定按原生方式做（Keystore + attestation），见知识库《设备身份》§2。
5. 是否评估商业风控（影子模式试用），挂进 `solvela-risk` 的 `RiskChainEngine` 作为发奖前的一个 filter。
