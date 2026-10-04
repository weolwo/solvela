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
| `Max-Age` / `Expires` | 活多久。**都不写 = 会话 cookie**，关浏览器通常就没了（"记住我"就靠这个区别；例外见 §2.3） | — |
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
- **会话 cookie 不保证「关浏览器就没了」。** Chrome / Edge 开着「启动时继续浏览上次打开的网页」时，重新打开浏览器会把会话 cookie 一起恢复。所以不勾「记住我」只是「多数情况下关浏览器即退出」，共用电脑上真正不留痕要靠用户主动退出 —— 退出时服务端吊销令牌，cookie 还在也没用了。
- **C 端产品通常默认一直登着。** 用户最讨厌反复登录；本项目「记住我」默认勾选，注册固定持久 cookie，只给在公用设备上登录的人留一个取消勾选的选项。
- **`SameSite` 不写时各浏览器默认值不一致**（Chrome 当作 `Lax`，别家未必），永远显式写出来。
- **本地开发**：`http://localhost` 上 Chrome 允许 `Secure` cookie，别的浏览器未必。`Secure` 与 `__Host-` 前缀要能按环境关掉。

---

### 2.4 cookie 归浏览器管：它替你做什么、墙挡谁、挡不住什么

**浏览器替你做的三件事**：

- **保管**：`Set-Cookie` 下发后存进浏览器自己的 cookie 数据库；
- **携带**：每次请求由浏览器按规则决定带哪些，前端代码不参与；
- **清理**：`Max-Age` 到期删除；会话 cookie 关浏览器删除（开了「恢复上次会话」的除外，见 §2.3）。

**它在 cookie 周围砌了几道墙，各挡一类对象**：

| 墙 | 由什么控制 | 挡住谁 |
|---|---|---|
| HttpOnly | cookie 属性 | 页面里的 JS（`document.cookie` 看不到） |
| 同源策略 | 浏览器内置 | 别的网站：读不到、写不进你域名的 cookie |
| Domain / `__Host-` | 属性 + 前缀 | 兄弟子域：覆盖不了你的 cookie |
| SameSite | cookie 属性 | 跨站请求：别的网站发起的 POST 不带它 |
| Secure | cookie 属性 | 明文网络：只走 HTTPS，防嗅探 |

**墙挡不住的三样**：

1. **挡「读」，不挡「用」**。页面 JS 读不到 cookie，但它发的请求浏览器照样自动带上。
   有 XSS 时恶意脚本偷不走令牌，却能趁用户开着页面以他的身份发请求 —— 所以 HttpOnly 是止损，不是防 XSS（§4.2）。
2. **挡不住浏览器之外的人**。用户自己在开发者工具里能看到并复制 HttpOnly cookie；
   有 cookie 权限的浏览器扩展能读全部 cookie；本机恶意软件能读浏览器存 cookie 的文件
   （Chrome 在 Windows 上对它做了加密，但跑在同一用户下的恶意软件仍有办法解开）。
   这一层靠服务端可吊销、敏感操作二次验证，再往上是 DBSC（§4.6）。
3. **挡不住用户自己清除**。「清除网站数据」一并清掉 cookie —— 墙是替用户挡别人的，不是挡用户的。

> 一句话：交给浏览器保管之后，**网页里的代码和别的网站**拿不到它；**浏览器本身和能控制这台电脑的人**，墙管不了。

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

#### `SameSite=Lax` 到底放行哪些跨站请求

判断标准只有一条：**是不是「顶层导航」且方法安全（GET）**——也就是地址栏里的页面整个换掉。

| evil.com 上发起的请求 | 带 `Lax` cookie？ |
|---|---|
| 点链接 `<a href>`、`location.href = ...`、GET 表单跳过来 | ✅ 带（所以外链点进来仍是登录态） |
| POST 表单提交（哪怕是顶层跳转） | ❌ |
| `fetch` / `XMLHttpRequest` | ❌ |
| `<img>` / `<script>` / `<iframe>` 里的请求 | ❌ |

它是 `Strict`（外链点进来也掉登录）和 `None`（什么都带，必须配 `Secure`）之间的折中。
代价写在上表：**顶层 GET 会带 cookie，所以 GET 接口绝不能改数据**。

> 为什么要显式写 `Lax`，而不是依赖"浏览器默认就是 Lax"：
> 各家默认值不一致（§2.3）；Chrome 对**没写** SameSite 的 cookie 还有一个「Lax + POST」例外——
> cookie 下发后约 2 分钟内，跨站顶层 POST 仍会带上。显式写了 `Lax` 就没有这个窗口。

#### `Sec-Fetch-Site`：目前最推荐的做法

现代浏览器（Chrome 76+、Firefox 90+、Safari 16.4+）在每个请求上自动带：

| 值 | 含义 |
|---|---|
| `same-origin` | 来自我们自己的页面 |
| `same-site` | 来自兄弟子域 |
| `cross-site` | 来自别的网站 |
| `none` | 用户在地址栏输入、点书签 |

**谁填的、能不能伪造：**

- **浏览器填**：比较「发起请求的页面」与「请求目标」得出；经过重定向时取整条链上最"外"的那个值。
  业务代码不用写任何东西，前端零改动。
- **页面 JS 改不了**：`Sec-` 开头的是规范里的**禁止请求头**（forbidden header），
  `fetch(url, { headers: { 'Sec-Fetch-Site': 'same-origin' } })` 里的这一项会被浏览器静默丢掉。`Origin` 同理。
- **curl / 脚本能随便填**——但无所谓：CSRF 的前提是「借受害者浏览器里的 cookie」，
  自己发请求的人手里只有自己的凭证（见上文「攻击者站在哪」）。挡他们靠鉴权、限流、验证码，不靠这个头。
- 同一族还有 `Sec-Fetch-Mode`（navigate / cors / no-cors…）、`Sec-Fetch-Dest`（document / image / script…）、
  `Sec-Fetch-User`（是否用户手势触发）。防 CSRF 只用 `Site` 就够。

判定规则：

```
所有写请求（非 GET / HEAD / OPTIONS），包括登录、注册这类匿名接口（见下文「登录 CSRF」）：
  Sec-Fetch-Site = same-origin / none       → 放行（none = 用户自己在地址栏 / 书签发起）
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

> ⚠️ 原生 App、自动化测试（用 `Authorization` 头传令牌）不会带这两个头，按最后一条放行 ——
> 它们没有「被浏览器自动附带凭证」这回事，不受 CSRF 影响，不能误拦。
> 反过来，不必去区分「这个请求是不是靠 cookie 认证的」：只要是浏览器发的写请求，就只认同源。

#### CSRF 令牌为什么攻击者"带不过来"（常见疑问）

- **复制受害者的值**：需要看到受害者的浏览器 → 已是会话被盗场景，不是 CSRF。
- **用自己账号的值**：服务端比对的是**同一请求里**请求头与 cookie 的值；受害者浏览器带的是受害者的 cookie，对不上。
- **在 evil.com 上把值塞进去**：给别人域名写 cookie 会被浏览器忽略；跨域加自定义头要预检，发不出去。
- **唯一失效条件**：攻击代码已运行在我们的源上（XSS）。那时问题已不是 CSRF。

所以 CSRF cookie **故意不设 HttpOnly**：它不是对我们自己页面保密的秘密，而是只有同源代码才读得到的暗号。

#### 登录 CSRF：容易被忽略的一种

上面说的都是「借用受害者的会话」。还有一种反过来：**把受害者登进攻击者的账号**。
evil.com 用攻击者自己的账号密码，替受害者的浏览器提交一次登录 —— 响应里的 `Set-Cookie` 落在受害者浏览器上，
他此后以为在用自己的号，实际绑的地址、充的钱、填的资料全进了攻击者的账号。

所以来源校验不能只挂在「已登录的写请求」上，**登录、注册这类匿名写接口也要挂**。
按 Sec-Fetch-Site 判的话最简单：所有非 GET 请求，浏览器发起的只认 same-origin，非浏览器客户端（没有这两个头）放行。

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

### 4.9 把这些防御落成响应头：几个一定会踩的坑

XSS 的第二道防线（CSP）、点击劫持、强制 HTTPS，最终都是**响应头**。它们写错时页面照常工作，所以格外容易漏：

- **nginx 的 `add_header` 不叠加继承。** 某个 `location` 里只要自己写了一条 `add_header`（比如静态资源的 `Cache-Control`），
  server 级的 `add_header` 在那个 location 里**全部失效**。做法：把安全头放进一个 snippet，server 级和每个自带 `add_header`
  的 location 各 include 一次。再加 `always`，否则 4xx / 5xx 响应上没有这些头。
- **`frame-ancestors` 在 Report-Only 策略里会被浏览器直接忽略**，也不能写在 `<meta>` 里。
  所以防点击劫持那一条必须**单独以强制方式下发**（`Content-Security-Policy: frame-ancestors 'self'` + `X-Frame-Options` 兜底），
  其余策略才可以先 Report-Only 观察。多条 CSP 头并存时浏览器各自独立执行，互不冲突。
- **`script-src 'self'` 不加 `'unsafe-inline'` 才有意义。** 它挡住的正是 `<script>...</script>` 与 `onerror=` 这类内联脚本 ——
  富文本 XSS 最常见的形态。加了 `'unsafe-inline'` 这道防线就等于没有。
  代价是自己页面里的内联脚本也要搬成同源文件（比如「JS 加载前先设深色主题」那段）。
  `style-src` 则常常要留 `'unsafe-inline'`：富文本里的 `style="..."` 是排版，而 CSS 注入的危害远小于脚本。
- **Report-Only 要有收报告的地方**（`report-uri`），否则违规只出现在用户自己的控制台里，你一条都看不到，
  也就永远没有依据切成强制。收报告的接口是匿名写日志的，要限流。
- **HSTS 先别加 `includeSubDomains` / `preload`。** 前者强制所有子域走 HTTPS，后者进了浏览器内置名单几乎撤不回来。
  另外浏览器只在 HTTPS 响应上认 HSTS，本地 http 访问时被忽略。


## 5. 三种凭证存放方式

| | localStorage + 请求头 | cookie（非 HttpOnly） | HttpOnly cookie |
|---|---|---|---|
| XSS 能偷走 | ✅ | ✅ | ❌ |
| 有 CSRF 风险 | ❌（浏览器不自动带） | ✅ | ✅ |
| Safari ITP 自动清理 | JS 写入的存储，7 天无交互即清 | JS 写的同样清 | 服务端下发的第一方 cookie 不受此 7 天限制 |
| 适合 | 原生 App、第三方 API 调用 | 一般不用 | **Web 端首选** |

本质是一个取舍：**用 CSRF 风险换 XSS 防护。** CSRF 能用 SameSite + 来源校验彻底堵住，XSS 很难百分百杜绝，所以 Web 端选 HttpOnly cookie。

---

### 5.1 🔴 HttpOnly 只在「令牌只出现在 Set-Cookie 里」时才成立

常见的半截改造：令牌放进了 HttpOnly cookie，登录接口的 JSON 里**也照样返回一份**。这等于白做。

HttpOnly 挡的是页面脚本「读 cookie」；而登录响应的 JSON 是页面脚本**自己发请求拿到的**，它当然读得到。
混进页面的恶意脚本不必碰 cookie，只要包一层 `fetch` 或挂一个 axios 拦截器，就能从响应体里把令牌截走。

```http
# ❌ 半截：cookie 里有，响应体里也有
Set-Cookie: sess=mb_xxx; HttpOnly; ...
{"accessToken": "mb_xxx", "member": {...}}

# ✅ 令牌只在 Set-Cookie 里
Set-Cookie: sess=mb_xxx; HttpOnly; ...
{"member": {...}}
```

推论（实现时要守住）：

- 登录、注册、领设备身份这类「签发凭证」的接口，**浏览器端的响应体里一律不带凭证原文**；
- **不能存在任何「cookie 里的会话 → 响应体里的令牌」的接口**。迁移时把旧令牌搬进 cookie 的接口只能单向（请求头 → cookie）；
- 前端因此读不到令牌，「是否已登录」要靠启动时问一次服务端（`/auth/me`），而不是看本地有没有令牌。

原生 App、自动化测试不受影响：它们声明走请求头模式，令牌仍在响应体里返回，之后用 `Authorization` 头调用 ——
那类客户端没有「页面里混进脚本」这个威胁模型。

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
