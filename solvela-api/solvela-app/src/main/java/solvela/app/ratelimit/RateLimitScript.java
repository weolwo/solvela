package solvela.app.ratelimit;

import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 一个维度一次往返：查处罚 → 取令牌 → 当前分钟 +1 → 汇总 3 / 5 / 10 分钟与长窗口 → 判违规 → 必要时升档处罚。
 * 原子完成（网关方案 §3、§4）。
 *
 * <h3>为什么不复用 {@code SlidingWindowScripts.COUNT}</h3>
 * 那段脚本一次只算一个窗口。这里要在<b>同一个原子操作</b>里同时看令牌桶、三个窗口、长窗口，并根据结果写处罚 ——
 * 拆成几次调用的话，两个并发请求可以同时看到「没超」、同时通过，阶梯也可能被同一波违规升两档。
 * 分桶的做法（字段 = 桶号、读的时候顺手删掉窗口外的桶）与那段脚本一致。
 *
 * <h3>键</h3>
 * KEYS[1] 计数哈希（令牌桶 + 最近 10 个分钟桶，TTL 11 分钟）；KEYS[2] 处罚哈希；KEYS[3] 长窗口哈希（没有长窗口时不碰）。
 *
 * <h3>参数</h3>
 * now（秒）、enforce（1 = 生效，0 = 陪跑：照常计数、只返回「本来会怎样」）、burst、l3、l5、l10、
 * 长窗口秒数（0 = 无）、长窗口阈值、阶梯（逗号分隔的秒数）、档位清零秒数、封顶秒数（0 = 不封顶）。
 *
 * <h3>返回 {@code {blocked, retryAfter, level, kind}}</h3>
 * kind：0 = 正常或「已在处罚中」，1 = 本次刚判罚 / 升档，2 = 陪跑下本来会判罚，3 = 陪跑下本来会因突发拒绝，
 * 4 = 突发拒绝（不判罚）。
 *
 * <h3>几条规矩</h3>
 * <ul>
 *   <li>🔴 处罚期间<b>不计数、不延长</b>：脚本第一步就返回。被罚的人一直点只是在收 429；</li>
 *   <li>被突发拒掉的请求<b>照样计入</b>时间窗口：持续猛打的脚本很快会在 3 分钟窗口上记到违规；</li>
 *   <li>突发<b>第一次只拒不罚</b>，同一个键 10 分钟内第二次超突发才记违规（双击、弱网重试不该被罚 60 秒）；</li>
 *   <li>🔴 只用单对参数的 {@code HSET}：多对参数要 Redis 4.0，本地开发用的 Redis 不认。</li>
 * </ul>
 *
 * @Date 2026-10-17
 */
final class RateLimitScript {

    private RateLimitScript() {
    }

    @SuppressWarnings("rawtypes")
    static final RedisScript<List> SCRIPT = RedisScript.of("""
            local now = tonumber(ARGV[1])
            local enforce = ARGV[2] == '1'
            local burst = tonumber(ARGV[3])
            local l3 = tonumber(ARGV[4])
            local l5 = tonumber(ARGV[5])
            local l10 = tonumber(ARGV[6])
            local lw = tonumber(ARGV[7])
            local ll = tonumber(ARGV[8])
            local resetSec = tonumber(ARGV[10])
            local cap = tonumber(ARGV[11])

            if enforce then
                local untilTs = tonumber(redis.call('HGET', KEYS[2], 'until') or '0')
                if untilTs > now then
                    return {1, untilTs - now, tonumber(redis.call('HGET', KEYS[2], 'level') or '0'), 0}
                end
            end

            local tokens = tonumber(redis.call('HGET', KEYS[1], 'b:t') or tostring(burst))
            local ts = tonumber(redis.call('HGET', KEYS[1], 'b:ts') or tostring(now))
            tokens = math.min(burst, tokens + (now - ts) * burst / 10)
            local burstHit = tokens < 1
            if not burstHit then tokens = tokens - 1 end
            redis.call('HSET', KEYS[1], 'b:t', tostring(tokens))
            redis.call('HSET', KEYS[1], 'b:ts', tostring(now))

            local cur = math.floor(now / 60)
            redis.call('HINCRBY', KEYS[1], 'm:' .. cur, 1)
            local c3, c5, c10 = 0, 0, 0
            local stale = {}
            local flat = redis.call('HGETALL', KEYS[1])
            for i = 1, #flat, 2 do
                local f = flat[i]
                if string.sub(f, 1, 2) == 'm:' then
                    local age = cur - tonumber(string.sub(f, 3))
                    local v = tonumber(flat[i + 1])
                    if age >= 10 or age < 0 then
                        table.insert(stale, f)
                    else
                        c10 = c10 + v
                        if age < 5 then c5 = c5 + v end
                        if age < 3 then c3 = c3 + v end
                    end
                end
            end
            if #stale > 0 then redis.call('HDEL', KEYS[1], unpack(stale)) end
            redis.call('EXPIRE', KEYS[1], 660)

            local cl = 0
            if lw > 0 then
                local bucketSec = 60
                if lw > 3600 then bucketSec = 3600 end
                local buckets = math.floor(lw / bucketSec)
                local lcur = math.floor(now / bucketSec)
                redis.call('HINCRBY', KEYS[3], tostring(lcur), 1)
                local lstale = {}
                local lflat = redis.call('HGETALL', KEYS[3])
                for i = 1, #lflat, 2 do
                    local b = tonumber(lflat[i])
                    if b == nil or b <= lcur - buckets then
                        table.insert(lstale, lflat[i])
                    else
                        cl = cl + tonumber(lflat[i + 1])
                    end
                end
                if #lstale > 0 then redis.call('HDEL', KEYS[3], unpack(lstale)) end
                redis.call('EXPIRE', KEYS[3], lw + bucketSec)
            end

            local violation = c3 > l3 or c5 > l5 or c10 > l10 or (lw > 0 and cl > ll)
            if burstHit and not violation then
                local last = tonumber(redis.call('HGET', KEYS[1], 'b:strike') or '0')
                redis.call('HSET', KEYS[1], 'b:strike', tostring(now))
                if last > 0 and now - last <= 600 then violation = true end
            end

            if violation then
                if not enforce then return {0, 0, 0, 2} end
                local level = tonumber(redis.call('HGET', KEYS[2], 'level') or '0')
                local lastV = tonumber(redis.call('HGET', KEYS[2], 'last') or '0')
                if now - lastV > resetSec then level = 0 end
                level = level + 1
                local steps = {}
                for s in string.gmatch(ARGV[9], '[^,]+') do table.insert(steps, tonumber(s)) end
                local dur = steps[math.min(level, #steps)]
                if cap > 0 and dur > cap then dur = cap end
                redis.call('HSET', KEYS[2], 'level', tostring(level))
                redis.call('HSET', KEYS[2], 'until', tostring(now + dur))
                redis.call('HSET', KEYS[2], 'last', tostring(now))
                redis.call('EXPIRE', KEYS[2], math.max(dur, resetSec))
                return {1, dur, level, 1}
            end

            if burstHit then
                if not enforce then return {0, 0, 0, 3} end
                return {1, math.max(1, math.ceil((1 - tokens) * 10 / burst)), 0, 4}
            end
            return {0, 0, 0, 0}
            """, List.class);
}
