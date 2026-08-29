# solvela-app-web

C 端 H5（对接 `solvela-app`，默认端口 1025）。

> 这不是管理端。管理端是 `solvela-admin-web`，两边**不共享任何约定**：技术栈、目录结构、
> 组件库、代码规范都各管各的，不要为了「一致」把一边的做法搬到另一边。

---

## 技术栈

| 类别 | 选型 | 版本 |
| --- | --- | --- |
| 框架 | Vue | 3.5.42 |
| 构建 | Vite | 8.2.2 |
| 语言 | TypeScript | 6.0.3 |
| 路由 / 状态 | vue-router / pinia | 5.3.0 / 4.0.3 |
| UI | Varlet | 3.20.6 |
| HTTP | axios | 1.20.0 |
| 金额 | decimal.js | 10.6.0 |
| 时间 | dayjs | 1.11.23 |
| 测试 | Vitest | 4.1.11 |

**TypeScript 锁在 6.x，不要升 7。** 7.0.x 是 Go 重写的原生编译器，重构了包结构：
`vue-tsc@3.3.11` 找不到 `typescript/lib/tsc` 会直接崩溃，`.vue` 一个文件都检查不了；
`typescript-eslint@8.68.0` 的 peer 也卡在 `<6.1.0`。等这两个跟上再升，那时是个纯升级动作。

**依赖精确锁版本**（`.npmrc` 里 `save-exact=true`）。C 端跑在用户设备上，
依赖漂移一个小版本就可能带来体积或兼容问题，升级走显式 PR。

**不用 Tailwind、不做国际化**，理由见文末「已定型的取舍」。

---

## 命令

```bash
npm install
npm run dev          # 本地开发，:5175，通过 vite proxy 转发到后端
npm run typecheck    # vue-tsc，.vue 也在检查范围内
npm run lint         # eslint，--max-warnings 0
npm run format       # prettier 写入
npm run test         # vitest
npm run build        # typecheck + 构建
npm run ci           # 提交前跑这个：format:check + lint + typecheck + test + build
```

---

## 目录

```
src/
  api/        接口层。每个模块导出「已归一化」的数据，不把后端原始形状漏给上层
    http.ts     axios 实例 + 拦截器
    errors.ts   错误码契约 + ApiError
  types/      契约类型（branded type）与边界转换函数
  utils/      money / datetime / token-storage —— 都是有业务语义的封装，不是杂物间
  stores/     pinia
  router/     路由与守卫
  views/      页面
  styles/     主题变量与基础样式
```

---

## 后端契约：三条铁律

这三条是本项目最容易出事的地方，都由类型系统兜底，**不要绕过**。

### 1. ID 一律当字符串

后端 `LongJsonSerializer` 在 `|value| <= 2^53-1` 时输出 JSON **数字**，超出时输出**字符串**。
同一个字段的 JSON 类型取决于值的大小——业务量小的时候看不出来，等 ID 涨上去才炸。

- 所有主键在接口层经 `toId()` 归一成 `Id`（branded string）
- 业务代码里不做算术、不和数字字面量比较、不 `parseInt`

### 2. 金额一律走 `utils/money`

后端 `JsonConfig` 给 `BigDecimal` 挂了 `ToStringSerializer`，金额永远是十进制字符串。
`Number()` 之后 `toFixed` 是错的——`0.1 + 0.2` 那类误差在钱包和中奖金额上就是事故。

展示用的 `format()` 是**向下取整**（ROUND_DOWN）：余额宁可显示得比实际少一分，
也不能多一分，后者是客诉。

### 3. 时间没有时区

后端下发 `yyyy-MM-dd HH:mm:ss`，既没有 `T` 也没有偏移量。

- 不要 `new Date(它)`，各浏览器对这个非 ISO 格式的解析行为不一致 → 用 `utils/datetime`
- **倒计时不能用「服务端时间字符串 − 本地当前时间」**：用户设备时区或时钟不对，
  倒计时就是错的。让后端下发剩余秒数，用 `createCountdown()`

### 响应形状：没有信封

`solvela-app` 的契约是：成功 = 2xx + **数据本身**；失败 = 4xx/5xx + `{code, message, traceId}`。
不要写 `if (res.data.code === 0)` 那一套——判断依据是 HTTP 状态码，分支依据是 `code`，
给用户看的是 `message`。

**`traceId` 一定要在错误提示里露出来。** 后端把它放进响应就是为了让用户截图报障时
能一次定位到服务端日志。

### 401 有两种，不要一视同仁

| code | 含义 | 处理 |
| --- | --- | --- |
| `LOGIN_REQUIRED` | 没带令牌 / 令牌失效 | 清会话 + 跳登录（拦截器统一做） |
| `BAD_CREDENTIALS` | 这次密码输错了 | **原样抛给调用方**展示 message |

把 401 一律当掉登录态处理，结果是用户输错密码后被弹回登录页，还看不到「手机号或密码错误」。

---

## 部署约束

### 必须同源

`CorsFilterConfig` 挂着 `@Conditional(SystemEnvironmentConfig)`，只在 **dev / test** 注册——
**pre / prod 没有 CORS**。

所以 `VITE_API_BASE_URL` 只能是相对路径，由 nginx 把静态站和 `/api` 放在同一个域下。
按 `m.xxx.com` 调 `api.xxx.com` 规划的话，dev/test 一路绿灯，上 pre 全线报错。
本地开发也刻意走 vite proxy 同源转发，就是为了不养成跨域直连的习惯。

### 上线前必须确认：公开图片 URL

后端 `file.storage.public-url-prefix` 在 **test / pre / prod 三套配置里都是注释掉的**，
只有 dev 填了值。留空时公开文件会退回到要登录态的下载接口——管理端照常，**C 端图全是叉**。

而且 dev 填的那个值指向 `:1024`，那是 **solvela-admin**，公开文件接口在管理端。
上线前要决定：走 CDN / OSS（推荐），还是让 nginx 把该路径转到 admin。
后者意味着管理端要对公网可见，与 `docker-compose.yml` 里「别让应用端口直接对公网」的约定冲突。

---

## 工程规范

- **提交前跑 `npm run ci`**，五项全绿再提。仓库根目录没有 package.json，
  因此没有装 husky/lint-staged——git hook 是仓库级设施，要装得整个仓库一起商量。
  CI 在 `.github/workflows/app-web-ci.yml`，只在本目录有改动时触发。
- **路由默认需要登录**，公开页要在 `meta.anonymous` 显式开口子。
  反过来写意味着新加页面时忘了标记 = 默默裸奔，这个方向的错误在 C 端是数据泄露。
- **组件里不写死颜色**，只用 `var(--sv-*)`，活动换皮肤时只改 `styles/theme.css`。
- **不在前端重写后端已有的校验规则**。例如手机号格式校验在后端 `MemberPhoneUtil.normalize`，
  前端只做非空这类纯交互拦截——两份规则迟早对不上。
- ESLint 里这三条是地雷不是风格，别关：
  `@typescript-eslint/restrict-plus-operands`、`@typescript-eslint/no-unsafe-argument`、`eqeqeq`。

---

## 已定型的取舍

**不用 Tailwind。** 本项目的页面是高度定制的活动视觉（转盘、九宫格、皮肤、动画），
这恰恰是 utility class 收益最低、噪音最大的场景；规整间距由 Varlet 组件承担。
将来真堆出很多规整列表页了再加，是非破坏性的。

**不做国际化。** i18n 解决的是字符串问题，而进入另一个市场是支付、登录、合规、
落地节点的问题，没有一样是 locale 文件能解决的。更实际的是：后端的用户可见文案
（`ApiErrors`、`UserErrorCode`）全是写死的中文，前端加 i18n 也只能覆盖一半。
活动文案的多变性是**内容问题**，走活动展示配置接口，不要硬编码进组件。

**已知体积成本：** Varlet 的样式 chunk 约 87 KB（gzip 26 KB），
里面是 MD2 + MD3 两套设计令牌。按需引入是生效的（`components.d.ts` 里只有实际用到的组件，
CSS 里也只有 button / input 及其依赖的 ripple / elevation 等基础件）。
若要继续压，方向是只保留一套 Material 版本的令牌。

**静态资源压缩靠 nginx。** 后端 `server.compression` 只管 API 的 JSON，
碰不到这里的 JS/CSS。C 端的体积大头是首屏 bundle 不是接口。
