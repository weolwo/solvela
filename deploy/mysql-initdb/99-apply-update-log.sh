#!/bin/bash
# 按版本号顺序回放 sql-update-log 下的增量脚本。
#
# 为什么需要这一步：基线 smart_admin_v3.sql 是旧版导出的（t_smart_job 只有 15 列，
# 而代码要 37 列），后续所有 DDL 变更都只落在 sql-update-log/v3.*.sql 里。
# 光跑基线建出来的库，SmartJob 模块启动就报 Unknown column 'job_code'。
#
# ⚠️ 必须用 sort -V（版本序）而不是默认字典序：一旦出现 v3.9.0 这种单位数小版本，
#    字典序会把它排到 v3.58.0 后面，DDL 顺序就乱了。
#
# ⚠️ 只在数据卷为空的首次初始化时执行。已有数据的库要升级得手工按版本跑。
set -uo pipefail

DIR=/sql-update-log
[ -d "$DIR" ] || { echo "[initdb] $DIR 不存在，跳过增量"; exit 0; }

total=0; noisy=0
for f in $(ls "$DIR"/v*.sql 2>/dev/null | sort -V); do
    total=$((total + 1))
    # --force 跳过单条报错继续执行，代价是进程退出码恒为 0 ——
    # 所以不能靠 $? 判断，只能看 stderr 有没有内容。
    mysql --defaults-extra-file=<(printf '[client]\nuser=root\npassword=%s\n' "$MYSQL_ROOT_PASSWORD") \
          --database=smart_admin_v3 --force < "$f" 2>/tmp/err.$$
    # 把报错打出来而不是咽掉，否则「某一版增量整个没生效」到运行期才会暴露。
    if [ -s /tmp/err.$$ ]; then
        noisy=$((noisy + 1))
        echo "[initdb] ⚠ $(basename "$f"):"
        sed 's/^/[initdb]     /' /tmp/err.$$
    fi
    rm -f /tmp/err.$$
done
echo "[initdb] 增量回放完毕：共 $total 个脚本，其中 $noisy 个有报错输出"
echo "[initdb] 注：Duplicate column / already exists 类报错属正常 ——"
echo "[initdb]     task.sql / activity.sql / lottery.sql 建表时已是增量之后的形态，"
echo "[initdb]     对应的 ALTER 再跑一次自然重复。真正要盯的是 Unknown table 这类。"
