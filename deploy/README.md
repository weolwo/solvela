# 部署：本机 Docker + Cloudflare 隧道

整套东西跑在你自己的机器上，通过 Cloudflare 隧道对外。
**没有任何端口对公网开放** —— 隧道是出站连接，路由器不用做端口映射，公网也扫不到这台机器。

```
公网 ──► Cloudflare 边缘 ──► cloudflared（出站）──► web(nginx) ──► 三个后端进程
                                                                    └──► mysql / redis / minio
```

| 域名 | 打到 | 是什么 |
|---|---|---|
| `app.taozicn.me` | web:8080 | C 端（静态站 + `/api` → 网关 + `/support/file/public/` → 图片） |
| `taozicn.me` | web:8080 | 同上，裸域也能进 |
| `admin.taozicn.me` | web:8081 | 管理端（静态站 + `/api` → admin） |

---

## 一、准备（只做一次）

### 1. 填 `.env`

```bash
cp .env.example .env
```

把每一处 `change-me` 都换掉。四把密钥一次生成：

```bash
for i in 1 2 3 4; do openssl rand -base64 30 | tr -d '/+=' | cut -c1-40; done
```

> 🔴 这四把里有三把**上线后不能改**（改了旧数据解不开、老会员登不进来、会员号可能撞号）。
> `.env.example` 里每一把都写了它属于哪一类，填之前读一遍。

### 2. 建隧道

```bash
cloudflared tunnel login          # 浏览器里授权 taozicn.me
cloudflared tunnel create solvela # 记下它打印的 UUID
cp ~/.cloudflared/<UUID>.json deploy/cloudflared/credentials.json
```

把 `deploy/cloudflared/config.yml` 里的 `tunnel: CHANGE-ME-TUNNEL-UUID` 换成那个 UUID。

### 3. 建三条 DNS 记录

```bash
cloudflared tunnel route dns solvela app.taozicn.me
cloudflared tunnel route dns solvela admin.taozicn.me
cloudflared tunnel route dns solvela taozicn.me
```

> ⚠️ 建出来的是 CNAME，**必须保持「橙云」代理状态**。
> 改成灰云等于绕过 Cloudflare，而 `CF-Connecting-IP` 正是 Cloudflare 写的 ——
> 绕过之后所有访客的 IP 会变成同一个（cloudflared 容器的地址）。不报错，只是登录日志和归属地全失去意义。

### 4. 🔴 给管理端加 Access 策略

**这一步不做，管理端登录页就是对全公网开放的。** 隧道本身不做鉴权，它只是一条管子。

Cloudflare Dashboard → Zero Trust → Access → Applications → Add an application：

- 类型 **Self-hosted**
- Application domain：`admin.taozicn.me`
- Policy：Action `Allow`，Include → `Emails` → 填你自己的邮箱

配好之后，没通过验证的人连管理端的登录页都看不到 —— 请求在 Cloudflare 边缘就被拒了，**根本到不了你家宽带**。免费额度 50 个用户。

---

## 二、起服务

```bash
docker compose up -d
docker compose logs -f admin
```

首次启动要等 MySQL 初始化 + 三个 Java 进程起来，约 1–2 分钟。
`docker compose ps` 里五个业务服务都是 `healthy` 才算好。

---

## 三、切到 MinIO 存图（可选，但建议）

默认 `FILE_STORAGE_MODE=local`，图片写在 admin 容器的卷里。改成 `cloud` 就写进 MinIO：

```bash
# .env
FILE_STORAGE_MODE=cloud
```

然后 `docker compose up -d admin app-biz`。

图片的 URL 形态**不变**（仍然是 `/support/file/public/<key>`，由 admin 从 MinIO 流出来），所以桶保持私有，前端一行不用动。

> ⚠️ 切换**之前**上传的文件，storageKey 指向的是本地磁盘 —— `admin-upload` 那个卷不要删。

---

## 四、几件容易踩的事

**发信要单独的服务商。** Cloudflare Email Routing 只能**收**信，不能发。注册验证码是发出去的，需要真正的 SMTP（Resend / Brevo / Mailgun / SendGrid，或者 Gmail、163 的应用专用密码）。填在 `.env` 的 `SMTP_*` 里。不填也能启动，发信时才失败。

**别给任何服务加 `ports`。** 尤其是 `web`。nginx 那份真实 IP 归一化（`deploy/nginx/snippets/proxy-upstream.conf`）成立的前提就是「只有 cloudflared 够得着 web」。一旦 web 的端口对外开放，任何人都能自己发一个 `CF-Connecting-IP` 头进来，把 IP 伪造成任意值。本地要看页面就临时加 `"127.0.0.1:8080:8080"`，用完删掉。

**改了取 IP 的请求头清单，两边都要改。** Java 侧在 `ClientIp` 和 `SolvelaServletUtil`，nginx 侧在那个 snippet 里。`ClientIpHeaderCoverageTest` 会盯着这件事 —— 它读的就是真文件。

**日志在卷里，不在容器里。**

```bash
docker compose logs -f app-gateway     # 最近的
docker volume inspect solvela_admin-logs   # 全量
```

---

## 五、更新

```bash
git pull
docker compose build          # 三个后端共用一次 mvn package，不是各编一遍
docker compose up -d
```

隧道不用动 —— cloudflared 是独立容器，后端重建时它一直连着。
