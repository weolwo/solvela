#!/usr/bin/env bash
# =============================================================================
# 把这套 manifests 装进本地 k8s。可重复执行。
#
#   bash deploy/k8s/setup.sh
#
# 🔴 它【不碰】docker compose 那一套：不同 namespace、不同 PVC、不跑 cloudflared。
#    compose 那八个容器可以一直开着，两边互不干扰（只是抢同一台机器的内存）。
#
# 机密从 .env 现读、建成 Secret，不往仓库里写任何一份拷贝。
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")/../.."          # 到仓库根
REPO="$PWD"
NS=solvela

say()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
die()  { printf '\n\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# -----------------------------------------------------------------------------
# ① 前置检查
# -----------------------------------------------------------------------------
say "检查环境"

command -v kubectl >/dev/null || die "没有 kubectl"
kubectl cluster-info >/dev/null 2>&1 || die "连不上集群。Docker Desktop → Settings → Kubernetes → Enable Kubernetes，Apply 之后等它变绿再跑。"

CTX=$(kubectl config current-context)
# 🔴 硬闸：默认只允许往 docker-desktop 这个 context 装。
#    这套 manifests 里有 NodePort 和 root 口令，误装进一个真集群是不可逆的。
if [ "$CTX" != "docker-desktop" ] && [ "${FORCE_CONTEXT:-0}" != "1" ]; then
  die "当前 context 是 [$CTX]，不是 docker-desktop。确实要装到这个集群就 FORCE_CONTEXT=1 再跑。"
fi
echo "  context: $CTX"

[ -f "$REPO/.env" ] || die "没有 .env。先 cp .env.example .env 并把 change-me 都改掉。"

# 四个本地镜像必须在 —— manifests 里是 imagePullPolicy: IfNotPresent，
# 镜像不在的话 Pod 会去 Docker Hub 找一个不存在的名字，报 ImagePullBackOff。
say "检查本地镜像"
for img in solvela-admin:3.0.0-distroless solvela-app:3.0.0-distroless \
           solvela-app-biz:3.0.0-distroless solvela-web:3.0.0; do
  if docker image inspect "$img" >/dev/null 2>&1; then
    echo "  ✓ $img"
  else
    die "缺镜像 $img —— 先在仓库根跑 docker compose build"
  fi
done

# -----------------------------------------------------------------------------
# ② namespace
# -----------------------------------------------------------------------------
say "建 namespace 与非机密配置"
kubectl apply -f deploy/k8s/00-base.yaml

# -----------------------------------------------------------------------------
# ③ Secret：从 .env 现读
# -----------------------------------------------------------------------------
# 读 .env 不用 source —— 值里可能有 # 和空格。逐行取 key=value。
envget() {
  sed -n "s/^$1=//p" "$REPO/.env" | head -1
}

say "从 .env 建 Secret"

MYSQL_PW=$(envget MYSQL_ROOT_PASSWORD)
REDIS_PW=$(envget REDIS_PASSWORD)
MINIO_USER=$(envget MINIO_ROOT_USER)
MINIO_PW=$(envget MINIO_ROOT_PASSWORD)
[ -n "$MYSQL_PW" ] || die ".env 里 MYSQL_ROOT_PASSWORD 是空的"
[ -n "$REDIS_PW" ] || die ".env 里 REDIS_PASSWORD 是空的"

# Grafana 的 admin 口令：.env 里有就用，没有就现生成一把存成文件（已进 .gitignore）。
GRAFANA_PW=$(envget GRAFANA_ADMIN_PASSWORD)
PW_FILE="$REPO/deploy/k8s/.grafana-admin-password"
if [ -z "$GRAFANA_PW" ]; then
  if [ -f "$PW_FILE" ]; then
    GRAFANA_PW=$(cat "$PW_FILE")
  else
    GRAFANA_PW=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
    printf '%s' "$GRAFANA_PW" > "$PW_FILE"
    echo "  Grafana admin 口令已生成，存在 deploy/k8s/.grafana-admin-password"
  fi
fi

# ⚠️ 同一个值在 Secret 里存了两套键名，这是刻意的：
#    · 中间件自己要的名字（MYSQL_ROOT_PASSWORD / REDIS_PASSWORD / MINIO_ROOT_*）
#    · Spring 要的名字（SPRING_DATASOURCE_PASSWORD 等）
#    后端用 envFrom 整个 Secret 注进去，所以必须有 Spring 那套名字；
#    而 mysql 容器只认 MYSQL_ROOT_PASSWORD。少任何一套都有一边起不来。
kubectl -n "$NS" create secret generic solvela-secret \
  --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_PW" \
  --from-literal=REDIS_PASSWORD="$REDIS_PW" \
  --from-literal=MINIO_ROOT_USER="$MINIO_USER" \
  --from-literal=MINIO_ROOT_PASSWORD="$MINIO_PW" \
  --from-literal=SPRING_DATASOURCE_PASSWORD="$MYSQL_PW" \
  --from-literal=SPRING_DATA_REDIS_PASSWORD="$REDIS_PW" \
  --from-literal=FILE_STORAGE_CLOUD_ACCESS_KEY="$MINIO_USER" \
  --from-literal=FILE_STORAGE_CLOUD_SECRET_KEY="$MINIO_PW" \
  --from-literal=SOLVELA_CRYPTO_PII_SECRET_KEY="$(envget SOLVELA_CRYPTO_PII_SECRET_KEY)" \
  --from-literal=SOLVELA_CRYPTO_PII_HMAC_KEY="$(envget SOLVELA_CRYPTO_PII_HMAC_KEY)" \
  --from-literal=SOLVELA_MEMBER_ID_SECRET_KEY="$(envget SOLVELA_MEMBER_ID_SECRET_KEY)" \
  --from-literal=SOLVELA_AUTH_DEVICE_KEY_V1="$(envget SOLVELA_AUTH_DEVICE_KEY_V1)" \
  --from-literal=SOLVELA_APP_RATE_LIMIT_ACCOUNT_KEY="$(envget SOLVELA_APP_RATE_LIMIT_ACCOUNT_KEY)" \
  --from-literal=LOTTERY_FPE_MASTER_SECRET="$(envget LOTTERY_FPE_MASTER_SECRET)" \
  --from-literal=SMTP_HOST="$(envget SMTP_HOST)" \
  --from-literal=SMTP_USERNAME="$(envget SMTP_USERNAME)" \
  --from-literal=SMTP_PASSWORD="$(envget SMTP_PASSWORD)" \
  --from-literal=MAIL_FROM="$(envget MAIL_FROM)" \
  --from-literal=GRAFANA_ADMIN_PASSWORD="$GRAFANA_PW" \
  --dry-run=client -o yaml | kubectl apply -f -

# 管理端 Basic Auth 口令文件。缺了的话 nginx 对 :8081 直接 500（刻意的 fail closed）
if [ -f "$REPO/deploy/nginx/admin.htpasswd" ]; then
  kubectl -n "$NS" create secret generic web-htpasswd \
    --from-file=admin.htpasswd="$REPO/deploy/nginx/admin.htpasswd" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  die "缺 deploy/nginx/admin.htpasswd —— 生成方式见 deploy/README.md"
fi

# -----------------------------------------------------------------------------
# ④ 两个基线 → ConfigMap
# -----------------------------------------------------------------------------
# 仓库里不存第二份基线，apply 时从 数据库SQL脚本/mysql/ 现读。
# 文件名前缀决定 initdb 的执行顺序。
say "把两个基线做成 initdb ConfigMap"
SCHEMA="$REPO/数据库SQL脚本/mysql/schema-baseline.sql"
DATA="$REPO/数据库SQL脚本/mysql/data-baseline.sql"
[ -f "$SCHEMA" ] && [ -f "$DATA" ] || die "找不到两个基线文件"

# ⚠️ ConfigMap 整个对象上限 1MiB。现在两份加起来约 527KB，还有一倍余量，
#    但基线是会长的 —— 超了就得改成 hostPath 挂目录（见 README 末尾）。
BYTES=$(( $(wc -c < "$SCHEMA") + $(wc -c < "$DATA") ))
echo "  两份基线共 $((BYTES / 1024)) KB（ConfigMap 上限 1024 KB）"
[ "$BYTES" -lt 1000000 ] || die "基线已超过 ConfigMap 上限，改用 hostPath，见 deploy/k8s/README.md"

kubectl -n "$NS" create configmap mysql-initdb \
  --from-file=01-schema.sql="$SCHEMA" \
  --from-file=02-data.sql="$DATA" \
  --dry-run=client -o yaml | kubectl apply -f -

# -----------------------------------------------------------------------------
# ⑤ nginx.conf → ConfigMap（只改 resolver 那一行）
# -----------------------------------------------------------------------------
# 🔴 整套搬迁唯一一处非改不可的东西：
#    deploy/nginx/nginx.conf 里写的是 `resolver 127.0.0.11`（Docker 内置 DNS），
#    k8s 里 DNS 是 CoreDNS，地址完全不同。不改的话 nginx 每个请求都解析失败，
#    全站 502，而 nginx 自己日志里只有 "no resolver defined to resolve" 一类。
#
#    改法是在这里现 sed，【不动仓库里那份】—— 动了就会碰到现有 compose 部署，
#    而仓库里存第二份 nginx.conf 就等着漂移。
say "按集群 DNS 生成 nginx.conf ConfigMap"
COREDNS_IP=$(kubectl -n kube-system get svc kube-dns -o jsonpath='{.spec.clusterIP}' 2>/dev/null || true)
[ -n "$COREDNS_IP" ] || die "取不到 CoreDNS 的 ClusterIP（kube-system/kube-dns）"
echo "  CoreDNS: $COREDNS_IP"

TMP_CONF=$(mktemp)
sed "s#resolver 127\.0\.0\.11 valid=30s ipv6=off;#resolver ${COREDNS_IP} valid=30s ipv6=off;  # k8s: CoreDNS（由 deploy/k8s/setup.sh 替换）#" \
  "$REPO/deploy/nginx/nginx.conf" > "$TMP_CONF"
grep -q "resolver ${COREDNS_IP}" "$TMP_CONF" || die "resolver 那一行没替换成功 —— nginx.conf 是不是改过了？去 setup.sh 里更新那条 sed。"

kubectl -n "$NS" create configmap web-nginx-conf \
  --from-file=nginx.conf="$TMP_CONF" \
  --dry-run=client -o yaml | kubectl apply -f -
rm -f "$TMP_CONF"

# -----------------------------------------------------------------------------
# ⑥ apply
# -----------------------------------------------------------------------------
say "apply 应用部分"
kubectl apply -f deploy/k8s/10-data.yaml
kubectl apply -f deploy/k8s/20-backend.yaml
kubectl apply -f deploy/k8s/30-web.yaml

say "apply 监控部分"
kubectl apply -f deploy/k8s/monitoring/

# -----------------------------------------------------------------------------
# ⑦ 等
# -----------------------------------------------------------------------------
say "等 MySQL 灌完两个基线（首次约 1-2 分钟）"
kubectl -n "$NS" rollout status deploy/mysql --timeout=15m

say "等三个后端"
kubectl -n "$NS" rollout status deploy/app-biz     --timeout=10m
kubectl -n "$NS" rollout status deploy/app-gateway --timeout=10m
kubectl -n "$NS" rollout status deploy/admin       --timeout=10m
kubectl -n "$NS" rollout status deploy/web         --timeout=5m

say "等监控"
kubectl -n "$NS" rollout status deploy/prometheus --timeout=5m
kubectl -n "$NS" rollout status deploy/grafana    --timeout=5m

printf '\n\033[1;32m全起来了。\033[0m\n'
cat <<EOF

  C 端          http://localhost:30080
  管理端        http://localhost:30081   （Basic Auth，口令就是 admin.htpasswd 那个）
  Prometheus    http://localhost:30090
  Grafana       http://localhost:30300   admin / 见 deploy/k8s/.grafana-admin-password

  看状态        kubectl -n $NS get pod
  看日志        kubectl -n $NS logs -f deploy/admin
  连库          kubectl -n $NS port-forward deploy/mysql 13306:3306

  拆掉          bash deploy/k8s/teardown.sh

compose 那一套没被动过：docker compose ps 还是原样。
EOF
