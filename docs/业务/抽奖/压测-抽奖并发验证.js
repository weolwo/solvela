/**
 * 抽奖接口并发压测：验证双层防超发（Redis Lua + DB 条件更新）在并发下绝不超发
 *
 * 使用方法（后端 + Redis + MySQL 均已启动）：
 *   1. 浏览器登录后台，DevTools -> Network 任意请求头里复制 Authorization 的值（形如 Bearer xxxxx）
 *      鉴权头对齐 solvela-base.yaml：token-name=Authorization、token-prefix=Bearer
 *   2. 拿到活动编码与奖池编码（编码是 10 位随机码，不可硬编码）：
 *      SELECT activity_code FROM t_activity_config WHERE activity_type = 'DRAW';
 *      SELECT pool_code, pool_name FROM t_prize_pool_config WHERE activity_code = '<活动编码>';
 *   3. node 压测-抽奖并发验证.js <token> <活动编码> <奖池编码> [并发数] [总次数]
 *      示例：node 压测-抽奖并发验证.js eyJhbGci... 12278CBYW7 K7QW2M9XPD 100 500
 *
 * 判定标准（跑完后人工核对）：
 *   A. 下方统计的各奖项「中奖次数」<= 该奖项压测前的剩余库存（限量奖项）
 *   B. SELECT prize_code, used_stock, total_stock FROM t_prize_pool_item WHERE activity_code='<活动编码>';
 *      每个奖项 used_stock 增量 == 本脚本统计的中奖次数，且 used_stock <= total_stock（-1 除外）
 *   C. SELECT status, COUNT(*) FROM t_draw_prize_log GROUP BY status;  流水总数 == 有效请求数
 *   典型验证场景：把 iPhone 库存改为 5、概率调大(如 50%)，100 并发打 500 次 —— iPhone 中奖必须恰好 5 次
 */
const http = require('http');

const [token, activityCode, poolCode, concurrency = 50, total = 500] = process.argv.slice(2);
if (!token || !activityCode || !poolCode) {
  console.error('用法: node 压测-抽奖并发验证.js <token> <活动编码> <奖池编码> [并发数=50] [总次数=500]');
  console.error('提示: 尖括号只是占位符，实际命令里不要带。token 带不带 Bearer 前缀都行，脚本会统一处理');
  process.exit(1);
}

const HOST = '127.0.0.1';
const PORT = 1024; // Solvela 后端默认端口，按实际修改
const ACTIVITY_CODE = activityCode;
const POOL_CODE = poolCode;

// 鉴权头必须与 solvela-base.yaml 的 sa-token 配置一致：token-name=Authorization、token-prefix=Bearer
// （前端 lib/axios.js 也是这么发的）。粘贴时带不带 Bearer 都兼容，这里统一补齐。
const AUTH_HEADER = 'Authorization';
const AUTH_VALUE = /^Bearer\s+/i.test(token.trim()) ? token.trim() : `Bearer ${token.trim()}`;

// 每轮压测独立的用户名前缀。
// 固定用 stress_user_0..N 会跨轮撞车：user_max_count=1 时，上一轮赢过某奖项的用户
// 在 Redis 的 draw:user:{活动}:{奖项}:{用户} 计数器里已是 1，之后永远赢不了该奖项，
// 不清 Redis 就会让下一轮结果越跑越失真（表现为「明明有库存却一直不中」）。
const RUN_ID = Date.now().toString(36).toUpperCase();

const stat = { ok: 0, biz: 0, err: 0, hitByPrize: {}, missByMsg: {}, costMs: [] };

function drawOnce(i) {
  return new Promise((resolve) => {
    const body = JSON.stringify({
      activityCode: ACTIVITY_CODE,
      poolCode: POOL_CODE,
      // 同轮内各不相同 -> 避开单人限领与防刷限流；跨轮也不相同 -> 免去清 draw:user:* 的心智负担
      memberName: `stress_${RUN_ID}_${i}`,
      requestId: `stress_${RUN_ID}_${i}`,
    });
    const start = Date.now();
    const req = http.request(
      { host: HOST, port: PORT, path: '/drawPrizeLog/execute', method: 'POST',
        headers: { 'Content-Type': 'application/json', [AUTH_HEADER]: AUTH_VALUE, 'Content-Length': Buffer.byteLength(body) } },
      (res) => {
        let data = '';
        res.on('data', (c) => (data += c));
        res.on('end', () => {
          stat.costMs.push(Date.now() - start);
          try {
            const json = JSON.parse(data);
            if (json.ok && json.data) {
              stat.ok++;
              if (json.data.hit) {
                stat.hitByPrize[json.data.prizeCode] = (stat.hitByPrize[json.data.prizeCode] || 0) + 1;
              } else {
                stat.missByMsg[json.data.message] = (stat.missByMsg[json.data.message] || 0) + 1;
              }
            } else {
              stat.biz++;
              stat.missByMsg[json.msg || 'unknown'] = (stat.missByMsg[json.msg || 'unknown'] || 0) + 1;
            }
          } catch (e) { stat.err++; }
          resolve();
        });
      }
    );
    req.on('error', () => { stat.err++; resolve(); });
    req.end(body);
  });
}

(async () => {
  console.log(`开始压测: 并发=${concurrency}, 总次数=${total}, 目标=${POOL_CODE}@${ACTIVITY_CODE}, 本轮用户前缀=stress_${RUN_ID}_`);
  const t0 = Date.now();
  let seq = 0;
  const workers = Array.from({ length: Number(concurrency) }, async () => {
    while (true) {
      const i = seq++;
      if (i >= Number(total)) return;
      await drawOnce(i);
    }
  });
  await Promise.all(workers);
  const elapsed = (Date.now() - t0) / 1000;
  const sorted = stat.costMs.sort((a, b) => a - b);
  const p = (q) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))] || 0;

  console.log('\n========== 压测结果 ==========');
  console.log(`耗时 ${elapsed.toFixed(1)}s | QPS ${(Number(total) / elapsed).toFixed(1)} | RT p50=${p(0.5)}ms p95=${p(0.95)}ms p99=${p(0.99)}ms`);
  console.log(`成功响应: ${stat.ok} | 业务拒绝: ${stat.biz} | 网络/解析异常: ${stat.err}`);
  console.log('\n各奖项中奖次数（与 DB used_stock 增量逐一核对，限量奖项不得超过压测前剩余库存）:');
  Object.entries(stat.hitByPrize).sort((a, b) => b[1] - a[1]).forEach(([k, v]) => console.log(`  ${k}: ${v}`));
  console.log('\n未中/拒绝原因分布:');
  Object.entries(stat.missByMsg).forEach(([k, v]) => console.log(`  ${k}: ${v}`));
})();
