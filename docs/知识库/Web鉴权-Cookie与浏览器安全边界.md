# Web 鉴权：Cookie 与浏览器安全边界

> 撰写 2026-09-26 · 类型：**知识库**（通用原理，脱离本项目也成立）
> 本项目怎么落地见 `docs/业务/会员/账号安全-方案评估与Web端Cookie改造.md`。
>
> 读完应该能回答：cookie 的每个属性在防什么；为什么"能发不能读"是一切的地基；
> CSRF 防御凭什么有效、攻击者为什么"拿不到那个值"；前端签名 / 全量加密为什么不是答案。

---

## 0. 一句话

**浏览器会自动带上 cookie。** cookie 的全部安全机制都在回答同一个问题：
怎么保证它只在我们希望的场景下被带上，而且谁也拿不走。

---

## 1. 为什么需要 cookie

HTTP 是**无状态**的：服务端不记得上一个请求是谁发的。登录成功后，服务端要发给你一个凭证，
你之后每次请求都带上它。凭证怎么带，有两大类：

| 做法 | 怎么带 | 谁负责带 |
|---|---|---|
| cookie | 浏览器自动放进 `Cookie` 请求头 | **浏览器** |
| 请求头令牌 | 前端 JS 从 localStorage 读出，放进 `Authorization` | **前端代码** |

后面几乎所有安全问题都源于这个差别：**浏览器自动带**，让 cookie 更难被偷，但更容易被借用。

---

## 2. cookie 本身怎么工作

### 2.1 一来一回

```http
# 服务端在响应里下发
POST /auth/login
→ 200 OK
  Set-Cookie: sess=abc123; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000

# 之后浏览器发往这个域名的请求，自动带上
GET /mall/commodity
Cookie: sess=abc123
```

### 2.2 每个属性对应一个问题

| 属性 | 作用 | 不设会怎样 |
|---|---|---|
| `Domain` | 发给哪些域名。**不写 = 只发给设置它的那个主机（host-only）** | 写成 `Domain=example.com`，所有子域都会收到 |
| `Path` | 发给哪些路径 | 一般设 `/`。**它不是安全边界**，别指望靠它隔离 |
| `Max-Age` / `Expires` | 活多久。**都不写 = 会话 cookie**，关浏览器就没了（"记住我"就靠这个区别） | — |
| `Secure` | 只在 HTTPS 下发送 | 公共 Wi-Fi 下走一次 HTTP 就被嗅探 |
| `HttpOnly` | JS 读不到（`document.cookie` 里看不见） | XSS 脚本直接读出来发走 |
| `SameSite` | 跨站请求时带不带（见 §4.3） | — |

两个由浏览器强制执行的**名字前缀**：

| 前缀 | 浏览器强制要求 | 用途 |
|---|---|---|
| `__Secure-` | 必须 `Secure` | — |
| `__Host-` | 必须 `Secure`、`Path=/`、**不能写 Domain** | 保证只属于当前主机，兄弟子域写不进同名 cookie 来覆盖它 |

### 2.3 容易踩的细节

- **删除** = 再发一次同名 cookie 并设 `Max-Age=0`，且 **Path、Domain 必须与设置时完全一致**，否则删不掉（浏览器当成另一个 cookie）。
- **单个 cookie 约 4KB 上限**，且每个请求都带，不适合放大块数据。
- **Chrome 把有效期上限截断为 400 天。**
- **`SameSite` 不写时各浏览器默认值不一致**（Chrome 当作 `Lax`，别家未必），永远显式写出来。
- **本地开发**：`http://localhost` 上 Chrome 允许 `Secure` cookie，别的浏览器未必。`Secure` 与 `__Host-` 前缀要能按环境关掉。

---

## 3. 浏览器的安全边界：一切的地基

### 3.1 Origin（源）与 Site（站）

- **Origin** = 协议 + 主机 + 端口，三者全等。
- **Site** = 协议 + 可注册域名（eTLD+1），即去掉子域后剩下的部分。

| 对比 | 同源？ | 同站？ |
|---|---|---|
| `https://app.example.com` vs `https://app.example.com/mall` | ✅ | ✅ |
| `https://app.example.com` vs `https://admin.example.com` | ❌ 主机不同 | ✅ |
| `https://app.example.com` vs `http://app.example.com` | ❌ 协议不同 | ❌ |
| `https://app.example.com` vs `https://evil.com` | ❌ | ❌ |

**同源比同站严格。** `SameSite` 看"站"，同源策略看"源"——所以 `SameSite` 挡不住兄弟子域。

### 3.2 同源策略：能发，不能读

`evil.com` 上的页面：

| 行为 | 能不能 |
|---|---|
| 让浏览器向我们**发请求**（表单、图片、fetch） | ✅ 能，并且会带 cookie（取决于 SameSite） |
| **读**我们的响应 | ❌ |
| **读**我们的 cookie、localStorage | ❌ |
| 给我们的域名**写** cookie | ❌ |
| 跨域请求**加自定义请求头** | ❌ 要先过 CORS 预检，服务端不同意就发不出 |

**"能发不能读"四个字，决定了 CSRF 为什么存在，也决定了它的所有防法为什么有效。**

### 3.3 CORS：同源策略的开口

CORS 是服务端主动放宽同源策略：用响应头告诉浏览器"允许某来源读我的响应"。浏览器把跨域请求分两类：

- **简单请求**：GET / HEAD，或 POST 且 Content-Type 只是表单类（`x-www-form-urlencoded` / `multipart/form-data` / `text/plain`），无自定义请求头。**直接发出**，只是响应读不到。HTML 表单天生就能发这种请求。
- **非简单请求**：带自定义头、`Content-Type: application/json`、PUT / DELETE 等。**先发 OPTIONS 预检**，服务端同意才发正式请求。

> 纯 JSON 接口跨域时本来就要预检，所以**天然挡住了大部分 CSRF**。但这很脆弱：
> 哪天服务端也接受 `text/plain` 并当 JSON 解析，表单就能打进来。仍需明确的防御。

> 🔴 **最危险的 CORS 配置**：`allowCredentials(true)` + 允许任意来源（`*` pattern 会回显请求方 origin）。
> 等于任何网站都能带着用户的 cookie 调你、并读到响应。依赖"跨域加不了自定义头"的防御会在这一刻静默失效。

---

## 4. 攻击与防御，一一对应

### 4.1 网络嗅探
**攻击**：公共 Wi-Fi 下监听流量，看到 cookie。
**防御**：全站 HTTPS + `Secure`；再加 HSTS 响应头，强制以后只走 HTTPS。

### 4.2 XSS（跨站脚本）
**攻击**：攻击者的脚本混进我们的页面，以**我们的身份**运行。
**防御两层**：
- **别让脚本混进来**：输出转义（Vue 的 `{{ }}` 默认转义，危险的是 `v-html`）+ CSP 响应头。这是根本。
- **混进来也偷不走**：`HttpOnly`。脚本仍可在页面内以用户身份发请求，但带不走凭证去长期离线使用。

**HttpOnly 是止损，不是防 XSS。** 它把"偷走后长期使用"降级为"只能趁用户开着页面现场操作"。

另外：HttpOnly 只防 **JS** 读，不防**人**在开发者工具里看、不防恶意软件读 cookie 文件（见 §4.6）。

### 4.3 CSRF（跨站请求伪造）

**攻击**：利用"能发不能读" + "自动带 cookie"，让受害者的浏览器替攻击者发写请求。

```html
<!-- evil.com 上 -->
<form action="https://app.example.com/api/mall/redeem" method="POST">...</form>
<script>document.forms[0].submit()</script>
```

#### 先想清楚：攻击者站在哪

| 攻击者位置 | 能带上受害者的 cookie | 能读到我们域名下的东西 |
|---|---|---|
| **自己的机器**（curl / 脚本） | ❌ 没有受害者的 cookie | ✅ 随便构造（但没用） |
| **借受害者的浏览器**（CSRF） | ✅ 浏览器自动带 | ❌ 同源策略挡着 |
| **我们自己的页面** | ✅ | ✅ |

所有 CSRF 防御都是在要求**两列同时满足**——只有我们自己的页面做得到。

- 在位置 1 挡住攻击者的是**会话 cookie 本身**，CSRF 防御根本不参与。
- 如果设想的攻击者"能直接复制受害者浏览器里的值"，他所在的位置就已经是受害者的电脑（开发者工具、恶意软件），
  那时他能直接复制会话 cookie，问题是**会话被盗**（§4.6），不再是 CSRF。

#### 防御手段，从粗到细

| 手段 | 原理 | 局限 |
|---|---|---|
| `SameSite=Lax` | 跨站的 POST / iframe / fetch 不带 cookie；只有顶层跳转的 GET 会带 | 挡不住同站（兄弟子域）；**因此 GET 接口绝不能有副作用** |
| `SameSite=Strict` | 任何跨站请求都不带 | 用户从微信 / 外链点进来显示未登录，体验差 |
| **校验 `Sec-Fetch-Site` / `Origin`** | 浏览器自动填、JS 改不了的请求头，告诉服务端请求从哪来 | 老浏览器可能无 `Sec-Fetch-Site`，回退看 `Origin` |
| 自定义请求头（值随意） | 跨域加不了自定义头（要预检） | **完全依赖 CORS 配置不出错** |
| CSRF 令牌（double-submit） | 只有同源 JS 读得到的暗号，必须放进请求头 | 部件多；要靠 `__Host-` 防被兄弟子域覆盖 |

#### `Sec-Fetch-Site`：目前最推荐的做法

现代浏览器（Chrome 76+、Firefox 90+、Safari 16.4+）在每个请求上自动带：

| 值 | 含义 |
|---|---|
| `same-origin` | 来自我们自己的页面 |
| `same-site` | 来自兄弟子域 |
| `cross-site` | 来自别的网站 |
| `none` | 用户在地址栏输入、点书签 |

`Sec-` 开头的是**禁止修改的请求头**，任何页面 JS 都改不了。判定规则：

```
仅对「靠 cookie 认证」的写请求（非 GET / HEAD / OPTIONS）：
  Sec-Fetch-Site = same-origin              → 放行
  Sec-Fetch-Site = same-site / cross-site   → 拒绝
  没有 Sec-Fetch-Site                        → 看 Origin，等于自己的源才放行
  两者都没有                                 → 不是浏览器（curl / Postman / 脚本），放行
                                               —— 它们带的是调用者自己的 cookie，不存在"借用"
```

Go 1.25 标准库的 `http.CrossOriginProtection` 就是这套逻辑。

| | CSRF 令牌 | 自定义请求头 | Sec-Fetch-Site / Origin |
|---|---|---|---|
| 额外 cookie | 要 | 不要 | 不要 |
| 前端改动 | 读 cookie + 加头 | 加固定头 | **无** |
| 依赖 CORS 配置正确 | 否 | **是** | 否 |
| 挡兄弟子域 | 靠 `__Host-` | 靠 CORS | 直接挡 |
| Postman 测试 | 要写脚本搬值 | 手动加头 | **无感** |

> ⚠️ 这类检查**只对 cookie 认证有意义**。用 `Authorization` 头传令牌的客户端（原生 App、自动化测试）
> 不会被浏览器自动附带凭证，不受 CSRF 影响，要直接跳过，不能误拦。

#### CSRF 令牌为什么攻击者"带不过来"（常见疑问）

- **复制受害者的值**：需要看到受害者的浏览器 → 已是会话被盗场景，不是 CSRF。
- **用自己账号的值**：服务端比对的是**同一请求里**请求头与 cookie 的值；受害者浏览器带的是受害者的 cookie，对不上。
- **在 evil.com 上把值塞进去**：给别人域名写 cookie 会被浏览器忽略；跨域加自定义头要预检，发不出去。
- **唯一失效条件**：攻击代码已运行在我们的源上（XSS）。那时问题已不是 CSRF。

所以 CSRF cookie **故意不设 HttpOnly**：它不是对我们自己页面保密的秘密，而是只有同源代码才读得到的暗号。

### 4.4 会话固定（Session Fixation）
**攻击**：先拿到一个会话 ID，诱导受害者带着它登录，登录后这个 ID 变成已登录，攻击者手里也有。
**防御**：**登录成功一定签发全新的会话**，不沿用登录前的。

### 4.5 Cookie 覆盖（Cookie Tossing）
**攻击**：被攻破的兄弟子域写 `Domain=example.com` 的同名 cookie，把受害者登进攻击者的账号——
受害者随后绑的地址、充的钱都进了攻击者的号。
**防御**：`__Host-` 前缀。

### 4.6 会话被盗
**攻击**：恶意软件读浏览器的 cookie 文件；有人在开发者工具里手动复制。
**防御**：cookie 本身无能为力，靠：服务端可随时吊销（会话存在服务端）、用户能看到并踢掉异常登录、
敏感操作二次验证、把会话绑定到设备硬件（DBSC，见 `docs/知识库/设备身份-Web与原生的能力边界.md`）。

### 4.7 退出没有真正退出
**问题**：前端删了 cookie，服务端会话还活着，之前被偷的那份照样能用。
**防御**：退出时**服务端先吊销**，再清 cookie。

### 4.8 点击劫持（Clickjacking）
**攻击**：用透明 iframe 把我们的页面嵌进 evil.com，诱导点击。
**防御**：`Content-Security-Policy: frame-ancestors 'self'`（旧写法 `X-Frame-Options: DENY`）。
另外 `SameSite=Lax` 的 cookie 在跨站 iframe 里不发送，被嵌的页面是未登录态。

---

## 5. 三种凭证存放方式

| | localStorage + 请求头 | cookie（非 HttpOnly） | HttpOnly cookie |
|---|---|---|---|
| XSS 能偷走 | ✅ | ✅ | ❌ |
| 有 CSRF 风险 | ❌（浏览器不自动带） | ✅ | ✅ |
| Safari ITP 自动清理 | JS 写入的存储，7 天无交互即清 | JS 写的同样清 | 服务端下发的第一方 cookie 不受此 7 天限制 |
| 适合 | 原生 App、第三方 API 调用 | 一般不用 | **Web 端首选** |

本质是一个取舍：**用 CSRF 风险换 XSS 防护。** CSRF 能用 SameSite + 来源校验彻底堵住，XSS 很难百分百杜绝，所以 Web 端选 HttpOnly cookie。

---

## 6. 常见"更简单方案"为什么不成立

### 6.1 前端每次请求带签名，服务端验签
签名要密钥，前端签名密钥就得在前端：
- **写死在 JS 里**：打包产物谁都能下载，格式化一下就找到（B 站 WBI 签名 `w_rid` 很早就被社区逆向）。它的作用是提高爬虫成本，不是安全机制。
- **登录后由服务端下发**：前端要把它存在 JS 可读处，性质就等于 CSRF 令牌，只是多了计算。

对 CSRF：签名放**请求头**时，拦住攻击的是"跨域加不了自定义头"，与签名对不对无关；
放**表单字段 / URL 参数**时，密钥公开，evil.com 自己能算，CSRF 照样成功。防篡改、防重放，HTTPS 已在传输层提供。

### 6.2 请求和响应全量加密
浏览器里的加密，钥匙（或生成钥匙的代码）一定下发给浏览器。黑产要么逆向重写，
要么不逆向、直接把网站 JS 放进 Node / 无头浏览器执行（"补环境"），或 RPC 调用真实浏览器里的加密函数。

| 问题 | 全量加密能否解决 |
|---|---|
| XSS 偷令牌 | ❌ 令牌照样在存储里；注入脚本可直接调用页面自己的加密函数 |
| CSRF | ⚠️ 仅当每会话一把、只有同源 JS 可读的密钥——那就是 CSRF 令牌 |
| 会话被盗 | ❌ 偷的是 cookie，与请求体无关 |
| 撞库 | ❌ 补环境 / RPC 照样批量，只是成本更高 |

它真正的用途：**提高爬虫成本**、**满足金融 / 政务的合规条款**（国密 SM2/SM4）、
**防止 HTTPS 终止之后的明文泄露**——TLS 在 CDN / 负载均衡处就解开了，之后的链路与日志看到的是明文。

针对最后一点，可以只对**密码字段**用服务端**公钥**加密（RSA-OAEP / SM2，浏览器 `crypto.subtle` 支持 RSA-OAEP），
只有后端持有私钥。这里公钥加密成立，是因为它防的不是浏览器端的人，而是**传输链路上的第三方与中间层**。
代价：密钥管理与轮换、密文带时间戳防重放。

---

## 7. 完整走一遍（HttpOnly cookie + Sec-Fetch-Site 方案）

```http
# ① 首次打开，领设备身份
POST /api/device/register
Sec-Fetch-Site: same-origin
→ Set-Cookie: __Host-dv=...; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=34560000

# ② 登录（自动带设备 cookie）
POST /api/auth/login
Cookie: __Host-dv=...
→ Set-Cookie: __Host-sess=...; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000
  {"member": {...}}                    ← 响应体不含令牌

# ③ 正常写请求
POST /api/mall/redeem
Cookie: __Host-dv=...; __Host-sess=...
Sec-Fetch-Site: same-origin            ← 同源，放行

# ④ evil.com 发起的 CSRF
POST /api/mall/redeem
Cookie: （SameSite=Lax，跨站 POST 不带）
Sec-Fetch-Site: cross-site             ← 即使带了也拒绝

# ⑤ 退出
POST /api/auth/logout
→ 服务端吊销会话
  Set-Cookie: __Host-sess=; Path=/; Max-Age=0; ...   （设备 cookie 保留）
```

| 步骤 | 起作用的机制 |
|---|---|
| ① | HttpOnly（设备令牌不被脚本复制）、服务端下发（不受 Safari 7 天清理） |
| ② | 登录签发新会话（防会话固定）、响应体不含令牌 |
| ③ | 浏览器自动携带、Secure（防嗅探） |
| ④ | SameSite=Lax + Sec-Fetch-Site（防 CSRF）、`__Host-`（防兄弟子域覆盖） |
| ⑤ | 服务端吊销（真正退出） |

---

## 8. 一张图

```
                    凭证怎么保管？
                         │
           ┌─────────────┴─────────────┐
      放在 JS 能碰的地方           交给浏览器自动带（cookie）
           │                             │
      怕：XSS 偷走              怕：被借用（CSRF）、被覆盖、被嗅探
                                         │
                          HttpOnly  ─────────── 防偷
                          Secure    ─────────── 防嗅探
                          SameSite + Sec-Fetch-Site ── 防借用
                          __Host-   ─────────── 防兄弟子域覆盖
                                         │
                          会话存服务端 ───────── 能随时吊销
                          登录换新会话 ───────── 防会话固定
```

**判断一个机制有没有用，先问：这个攻击者能看到什么、能控制什么。** 每个机制都只针对一种攻击者位置。
