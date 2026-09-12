# syntax=docker/dockerfile:1
#
# 两个前端 + nginx，打成一个镜像。
#
#     docker build -f deploy/web.Dockerfile -t solvela-web:3.0.0 .
#     （构建上下文是【仓库根目录】，因为要同时拿到两个前端目录和 deploy/nginx）
#
# 为什么合成一个镜像而不是两个：它们前面是同一个 nginx。拆成两个容器就要么各带一个
# nginx（多一份配置、多一份进程），要么再加一层代理。而这两个站的静态文件加起来
# 只有几 MB —— 分开部署省不下任何东西，只是多了一处要对齐的配置。
#
# 🔴 两个站的代码是【完全隔离】的：分别构建、分别落在 /srv/app 与 /srv/admin，
#    nginx 里由两个 server 块分别 root 过去。管理端的 JS 不会出现在 C 端页面上。

# ============================================================================
# ① C 端（solvela-app-web）
# ============================================================================
FROM node:24-alpine AS app-web
WORKDIR /src
# 先只拷 lockfile 装依赖：源码改动不会让这一层失效，重建时省掉整个 npm ci
COPY solvela-app-web/package.json solvela-app-web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY solvela-app-web/ ./
# build:prod 用的是 .env.production，里面 VITE_API_BASE_URL=/api（同源相对路径）。
# ⚠️ 那个文件里的注释写得很清楚：pre/prod 没有 CorsFilter，
#    填成绝对地址会全线 CORS 失败。别改成域名。
RUN npm run build:prod

# ============================================================================
# ② 管理端（solvela-admin-web）
# ============================================================================
FROM node:24-alpine AS admin-web
WORKDIR /src
COPY solvela-admin-web/package.json solvela-admin-web/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY solvela-admin-web/ ./
RUN npm run build:prod

# ============================================================================
# ③ 运行
# ============================================================================
#
# 用 unprivileged 版：进程以 uid 101 跑，不是 root。
# 代价是绑不了 1024 以下的端口，所以两个站监听 8080 / 8081 而不是 80 / 8080。
# 反正对外那一跳是 cloudflared，端口号是什么没人看得见。
FROM nginxinc/nginx-unprivileged:1.27-alpine

# 整份换掉，不是往 conf.d 里塞一个片段 —— 这份配置改了 http 块级别的东西
# （client_max_body_size、log_format、gzip），放 conf.d 里够不着。
COPY deploy/nginx/nginx.conf          /etc/nginx/nginx.conf
COPY deploy/nginx/snippets/           /etc/nginx/snippets/

COPY --from=app-web   /src/dist/ /srv/app/
COPY --from=admin-web /src/dist/ /srv/admin/

EXPOSE 8080 8081

# 配置语法错误要在启动时就炸，而不是等第一个请求进来才发现
RUN nginx -t
