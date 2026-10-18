# 本地 k8s 体验环境（和 docker compose 并存）

把 `docker-compose.yml` 那八个服务搬到 Kubernetes 上跑一遍，外加一套 Prometheus + Grafana。
**目的是体验 k8s，不是替换现有部署。**

```
bash deploy/k8s/setup.sh      # 装（可重复执行）
bash deploy/k8s/teardown.sh   # 拆
```

前提:Docker Desktop → Settings → Kubernetes → **Enable Kubernetes**,Apply 之后等它变绿。
`setup.sh` 会先自己检查连得上集群、以及 context 是不是 `docker-desktop`。

---

## 🔴 它凭什么不会弄坏现有部署

这是这套东西唯一真正要紧的性质,所以逐条列出来 —— 每一条都是**结构上**成立的,不是"小心一点就行":

| 隔离点 | 怎么保证的 |
|---|---|
| **数据** | 独立 PVC(`mysql-data` 等 4 块),**不复用** `solvela_mysql-data` 那些 docker 卷。k8s 这套库是从两个基线现灌的干净库 |
| **命名空间** | 全部在 `namespace: solvela` 里。`teardown.sh` 删掉它就等于全清 |
| **端口** | compose 那套**一个端口都不发布**(只有 minio 两个绑回环),这边只用 NodePort 30080/30081/30090/30300 —— 不重叠 |
| **隧道** | **不跑 cloudflared**。两个客户端连同一条隧道会互相抢路由,而凭据只有一份 |
| **仓库文件** | 没有改 `docker-compose.yml`、`deploy/nginx/nginx.conf`、`.env` 中的任何一个字 |
| **镜像** | 复用 `docker compose build` 出来的那四个本地镜像,不重新构建、不改 tag |

唯一共享的是**这台机器的 CPU 和内存**。两套同时跑大约要 6-7GB,Docker Desktop 的内存上限不够的话 k8s 这边会先被 OOM 掉(compose 那套有 `restart: unless-stopped` 兜着)。

---

## 文件

| 文件 | 内容 |
|---|---|
| `00-base.yaml` | namespace + 非机密环境变量 ConfigMap |
| `10-data.yaml` | MySQL / Redis / MinIO + 4 块 PVC + 建桶 Job |
| `20-backend.yaml` | admin:1024 / app-gateway:1025 / app-biz:1026 |
| `30-web.yaml` | nginx 静态站 + NodePort |
| `monitoring/40-prometheus.yaml` | Prometheus + RBAC + kube-state-metrics + mysqld/redis exporter |
| `monitoring/41-grafana.yaml` | Grafana + 数据源与面板的 provisioning |
| `setup.sh` / `teardown.sh` | 装 / 拆 |

**机密不在这些文件里。** `setup.sh` 从 `.env` 现读,建成 `solvela-secret`;
`nginx.conf` 和两个基线同理 —— 从仓库里那份现读现建 ConfigMap,所以仓库里不会出现第二份副本。

---

## 跑起来之后

```
C 端          http://localhost:30080
管理端        http://localhost:30081     Basic Auth,口令同 deploy/nginx/admin.htpasswd
Prometheus    http://localhost:30090
Grafana       http://localhost:30300     admin / 见 deploy/k8s/.grafana-admin-password
```

```bash
kubectl -n solvela get pod
kubectl -n solvela logs -f deploy/admin
kubectl -n solvela port-forward deploy/mysql 13306:3306   # 连库调试
```

---

## 和 compose 的逐项差异

compose 里的每个机制都有 k8s 的对应物,下面是对照 —— 以及**不一样**的地方:

| compose | k8s 这边 | 为什么 |
|---|---|---|
| `depends_on: condition: service_healthy` | initContainer 真连一次 MySQL / Redis | k8s 没有 depends_on。判据刻意保持"真连一次"而不是"端口通" —— MySQL 在 initdb 期间端口早就通了 |
| `HEALTHCHECK`(镜像里的 java 探针) | `exec` 探针跑**同一个** `/opt/probe/HealthCheck` | distroless 没有 shell/curl。不能换成 `httpGet`:它只认 2xx/3xx,而 app-biz 的 `GET /` 返回 404 |
| 命名日志卷 ×3 | `emptyDir` | 日志在 k8s 里看 `kubectl logs`。要留档再换 PVC |
| `admin-upload` 卷 | PVC | `FILE_STORAGE_MODE=local` 时图片落在这儿,emptyDir 会跟着 Pod 一起没 |
| `deploy.resources.limits.memory` | `resources.limits` 同值 | admin/app-biz 1Gi、网关 768Mi,和 `.env` 的默认一致 |
| `resolver 127.0.0.11`(Docker 内置 DNS) | **sed 成 CoreDNS 的 ClusterIP** | 整套搬迁唯一一处非改不可的。不改的话全站 502,而 nginx 日志只报解析失败 |
| cloudflared | **没有**,换 NodePort | 见上面隔离表 |
| 不发布任何端口 | NodePort | ⚠️ 代价见下 |

### ⚠️ 真实 IP 归一化在这套里**不成立**

`deploy/nginx/snippets/proxy-upstream.conf` 那份 `CF-Connecting-IP` 归一化,前提是**只有 cloudflared 够得着 web**。
这边开了 NodePort,本机任何进程都能自己发一个 `CF-Connecting-IP` 头进来,把 IP 伪造成任意值。

**所以这套只能在本机玩,不要往公网接。** 要接公网就得把 NodePort 换成 Ingress + 真实的 Cloudflare 隧道,并且重新想清楚那一层谁可信。

---

## Grafana 能看到什么、看不到什么

**看不到:JVM 堆 / GC / HTTP QPS / 连接池 —— 应用级指标一个都没有。**

`solvela-api/pom.xml:288` 把 `spring-boot-actuator` 从 redisson 的传递依赖里显式排掉了,三个 Java 进程都没有 `/actuator/**`。
要补就得把 actuator + `micrometer-registry-prometheus` 加回去 —— 那是改代码,不在"搬 compose"这件事里。

(补的时候还有一个坑:`/actuator/**` 正好是网关安全闸的**蜜罐路径**之一。Prometheus 必须绕过网关直连容器端口,从 `/api` 那条路进去会被当扫描器罚 24 小时。)

**看得到**(`Solvela 容器与中间件` 这个面板):

- 每个 Pod 的内存 / CPU / 网络 —— cAdvisor,对着 `limits` 看
- 容器重启次数 —— kube-state-metrics,一直涨就是有东西在 CrashLoopBackOff
- MySQL:连接数、QPS、慢查询
- Redis:已用内存、命中率、连接数
- MinIO 自带的 cluster 指标(靠 `MINIO_PROMETHEUS_AUTH_TYPE=public`,**compose 那套没有这一行**)

---

## 已知取舍

- **全部单副本。** admin 尤其不能加副本:定时任务是它独占的,多一个进程跑 `@Scheduled` 就是发奖和结算重复执行,而多发的那份没人会来报。
- **`strategy: Recreate`** 而不是滚动更新:PVC 是 ReadWriteOnce,滚动时新旧 Pod 抢同一块盘,新 Pod 会永远 Pending。
- **mysqld-exporter 用 root 连库**。体验图省事;长期跑该建个只有 `PROCESS / REPLICATION CLIENT / SELECT` 的 exporter 账号。
- **C 端图片看不到。** `FILE_STORAGE_PUBLIC_URL_PREFIX` 留空(本地没有域名),图片会退回要登录态的下载接口 —— 管理端能看,C 端是叉。
- **Prometheus 和 Grafana 都用 emptyDir**,重建就清零。面板和数据源是 provisioning 出来的,清零也会自己回来。
- **镜像 tag 写死在 manifests 里**(`3.0.0`)。重新 `docker compose build` 之后 tag 不变,所以要让 k8s 用上新代码得 `kubectl -n solvela rollout restart deploy/admin deploy/app-biz deploy/app-gateway deploy/web`。

### 基线哪天超过 1MiB

两个基线现在约 527KB,塞在一个 ConfigMap 里(上限 1MiB),`setup.sh` 会先量一下、超了直接报错。
真超了就把 `10-data.yaml` 里 `initdb` 那个 volume 换成 hostPath:

```yaml
- name: initdb
  hostPath:
    path: /run/desktop/mnt/host/d/workspace/solvela/数据库SQL脚本/mysql
    type: Directory
```

(Docker Desktop 的节点是个 Linux VM,Windows 盘挂在 `/run/desktop/mnt/host/<盘符小写>/` 下面。
注意这样挂进去的是整个目录 —— 里面那些造数脚本会**一起被 initdb 执行**,所以得先挑出两个基线放到单独目录。)
