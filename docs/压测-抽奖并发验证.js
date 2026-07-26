/**
 * 抽奖接口并发压测：验证双层防超发（Redis Lua + DB 条件更新）在并发下绝不超发
 *
 * 使用方法（后端 + Redis + MySQL 均已启动）：
 *   1. 浏览器登录后台，DevTools -> Network 任意请求头里复制 x-access-token 的值
 *   2. node 压测-抽奖并发验证.js <token> [并发数] [总次数]
 *      示例：node 压测-抽奖并发验证.js eyJhbGci... 50 500
 *
 * 判定标准（跑完后人工核对）：
 *   A. 下方统计的各奖项「中奖次数」<= 该奖项压测前的剩余库存（限量奖项）
 *   B. SELECT prize_code, used_stock, total_stock FROM t_prize_pool_item WHERE activity_code='ACT_618';
 *      每个奖项 used_stock 增量 == 本脚本统计的中奖次数，且 used_stock <= total_stock（-1 除外）
 *   C. SELECT status, COUNT(*) FROM t_draw_prize_log GROUP BY status;  流水总数 == 有效请求数
 *   典型验证场景：把 iPhone 库存改为 5、概率调大(如 50%)，100 并发打 500 次 —— iPhone 中奖必须恰好 5 次
 */
const http = require('http');

const [token, concurrency = 50, total = 500] = process.argv.slice(2);
if (!token) {
  console.error('用法: node 压测-抽奖并发验证.js <x-access-token> [并发数=50] [总次数=500]');
  process.exit(1);
}

const HOST = '127.0.0.1';
const PORT = 1024; // SmartAdmin 后端默认端口，按实际修改
const ACTIVITY_CODE = 'ACT_618';
const POOL_CODE = 'POOL_FREE';

const stat = { ok: 0, biz: 0, err: 0, hitByPrize: {}, missByMsg: {}, costMs: [] };

function drawOnce(i) {
  return new Promise((resolve) => {
    const body = JSON.stringify({
      activityCode: ACTIVITY_CODE,
      poolCode: POOL_CODE,
      // 用不同会员名避开单人限领与防刷限流，专注验证库存并发
      memberName: `stress_user_${i}`,
      requestId: `stress_${Date.now()}_${i}`,
    });
    const start = Date.now();
    const req = http.request(
      { host: HOST, port: PORT, path: '/drawPrizeLog/execute', method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-access-token': token, 'Content-Length': Buffer.byteLength(body) } },
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
  console.log(`开始压测: 并发=${concurrency}, 总次数=${total}, 目标=${POOL_CODE}@${ACTIVITY_CODE}`);
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
