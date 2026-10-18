#!/usr/bin/env bash
# =============================================================================
# 拆掉这套体验集群。
#
#   bash deploy/k8s/teardown.sh
#
# 🔴 它删的只有 namespace solvela 和两个 ClusterRole —— docker compose 那一套
#    （solvela_mysql-data 等卷、八个容器、隧道）一个字都不碰。
#
# ⚠️ 删 namespace 会【一并删掉 PVC】，也就是 k8s 这套库里的数据。那本来就是
#    从基线现灌的干净库，不是生产库的拷贝，所以没什么可留的。
# =============================================================================
set -euo pipefail

NS=solvela

CTX=$(kubectl config current-context)
if [ "$CTX" != "docker-desktop" ] && [ "${FORCE_CONTEXT:-0}" != "1" ]; then
  printf '\n\033[1;31m✗ 当前 context 是 [%s]，不是 docker-desktop。要真删就 FORCE_CONTEXT=1\033[0m\n' "$CTX" >&2
  exit 1
fi

printf '\n==> 删 namespace %s（含 PVC）\n' "$NS"
kubectl delete namespace "$NS" --ignore-not-found --wait=true

printf '\n==> 删集群级 RBAC\n'
kubectl delete clusterrole        solvela-prometheus solvela-kube-state-metrics --ignore-not-found
kubectl delete clusterrolebinding solvela-prometheus solvela-kube-state-metrics --ignore-not-found

printf '\n拆完了。docker compose 那一套不受影响：docker compose ps 自己看一眼。\n'
