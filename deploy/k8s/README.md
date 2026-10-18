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
| `resolver 127.0.0.11`(Docker 内置 DNS) | **sed 成 CoreDNS 的 ClusterIP** | 不改的话 `/api` 全是 502 |
| `set $upstream_gateway app-gateway:1025;` | **sed 成 `app-gateway.solvela.svc.cluster.local:1025`** | 🔴 比上一条隐蔽得多,见下 |
| cloudflared | **没有**,换 NodePort | 见上面隔离表 |
| 不发布任何端口 | NodePort | ⚠️ 代价见下 |

### 🔴 nginx 的 resolver 不吃 search 域

这条值得单独记,因为它的症状会骗人:**静态站 200、管理端 401 都正常,只有 `/api` 全是 502**。

`nginx -t` 不报错,配置也确实加载了。原因是 **nginx 自己的 `resolver` 完全绕开 libc,不会用 `/etc/resolv.conf` 里的 `search` 域**。Pod 里明明有:

```
nameserver 10.96.0.10
search solvela.svc.cluster.local svc.cluster.local cluster.local
options ndots:5
```

但 nginx 拿裸名 `app-gateway` 直接去问 CoreDNS,CoreDNS 对裸名回 SERVFAIL:

```
[error] app-gateway could not be resolved (2: Server failure)
```

Docker 内置 DNS 能解析裸容器名,CoreDNS 不能 —— 所以 compose 那份配置照搬过来,**换了 resolver 地址还是 502**,上游主机名也必须补成 FQDN。`setup.sh` 两处一起 sed,并且带校验:残留任何裸名上游就直接报错退出,不会装出一个"看着起来了、一调接口就 502"的环境。

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

**看得到**(`Solvela 容器与中间件`,9 个面板,每一条查询都实测有数据):

- 每个 Pod 的内存 / CPU —— cAdvisor,对着 `limits` 看
- 容器重启次数 —— kube-state-metrics,一直涨就是有东西在 CrashLoopBackOff
- MySQL:连接数、QPS、慢查询
- Redis:已用内存、命中率、连接数
- MinIO 自带的 cluster 指标(靠 `MINIO_PROMETHEUS_AUTH_TYPE=public`,**compose 那套没有这一行**)
- 网络:**节点级,按网卡**,不是按 Pod

### ⚠️ cAdvisor 在 Docker Desktop 上给的东西比标准集群少

两处要注意,都已经反映在面板的查询里了:

1. **没有 `container` 标签。** 这套运行时(cri-dockerd)下 cAdvisor 只吐 **Pod 级**序列。所以查询写的是 `{namespace="solvela",pod!=""}` —— 照抄社区面板里常见的 `container!="",container!="POD"` 会把**全部序列筛掉**,面板一片空白而 target 显示 `up`,很难看出是查询的问题。
2. **没有 per-pod 网络。** `container_network_*` 全都只有 `id="/"` 和 `interface` 两个标签,共 7 条节点级序列。所以网络面板是按网卡画的;`cni0` 是 Pod 网络的桥,它那条约等于集群内所有 Pod 的流量总和。

---

## 已知取舍

- **全部单副本。** admin 尤其不能加副本:定时任务是它独占的,多一个进程跑 `@Scheduled` 就是发奖和结算重复执行,而多发的那份没人会来报。
- **`strategy: Recreate`** 而不是滚动更新:PVC 是 ReadWriteOnce,滚动时新旧 Pod 抢同一块盘,新 Pod 会永远 Pending。
- **mysqld-exporter 用 root 连库**。体验图省事;长期跑该建个只有 `PROCESS / REPLICATION CLIENT / SELECT` 的 exporter 账号。
- **C 端图片看不到。** `FILE_STORAGE_PUBLIC_URL_PREFIX` 留空(本地没有域名),图片会退回要登录态的下载接口 —— 管理端能看,C 端是叉。
- **Prometheus 和 Grafana 都用 emptyDir**,重建就清零。面板和数据源是 provisioning 出来的,清零也会自己回来。
- **镜像 tag 写死在 manifests 里**(`3.0.0`)。重新 `docker compose build` 之后 tag 不变,所以要让 k8s 用上新代码得 `kubectl -n solvela rollout restart deploy/admin deploy/app-biz deploy/app-gateway deploy/web`。
- **改了 `deploy/nginx/nginx.conf` 要重跑 `setup.sh`,不能只改 ConfigMap。** nginx.conf 是用 `subPath` 挂的,而 **subPath 挂进去的 ConfigMap 更新不会传播到容器里**(普通目录挂载会在几十秒内自己同步,subPath 是一次性拷贝,永远停在 Pod 创建那一刻)。`setup.sh` 把配置内容的哈希打在 pod template 的注解上:内容没变不会白重启,变了才滚动一次。
- **`:latest` 的镜像必须显式写 `imagePullPolicy: IfNotPresent`。** k8s 对 `:latest` 默认是 `Always`,于是哪怕本机已经有(生产正在用同一个),kubelet 还是会去拉 —— 而 `quay.io/minio/minio:latest` 对此回 `401 UNAUTHORIZED`,直接 ImagePullBackOff。

### 两个基线塞进 ConfigMap 的两道上限

两个基线现在约 514KB,装在一个 ConfigMap 里。这里有**两道**上限,而卡人的是更紧的那一道:

- ConfigMap 整个对象 **1MiB** —— 还有余量,`setup.sh` 会先量一下、超了直接报错
- 注解总大小 **256KiB** ←← 真正的瓶颈

所以这一个 ConfigMap **不能用 `kubectl apply`**:apply 会把整个对象再塞进 `kubectl.kubernetes.io/last-applied-configuration` 注解里,514KB 的 data 存两遍,直接撞 256KiB:

```
The ConfigMap "mysql-initdb" is invalid: metadata.annotations: Too long: may not be more than 262144 bytes
```

`setup.sh` 改成先 `delete` 再 `create`(create 不写那个注解),一样可重复执行。

### 基线哪天超过 1MiB
真超了就把 `10-data.yaml` 里 `initdb` 那个 volume 换成 hostPath:

```yaml
- name: initdb
  hostPath:
    path: /run/desktop/mnt/host/d/workspace/solvela/数据库SQL脚本/mysql
    type: Directory
```

(Docker Desktop 的节点是个 Linux VM,Windows 盘挂在 `/run/desktop/mnt/host/<盘符小写>/` 下面。
注意这样挂进去的是整个目录 —— 里面那些造数脚本会**一起被 initdb 执行**,所以得先挑出两个基线放到单独目录。)
