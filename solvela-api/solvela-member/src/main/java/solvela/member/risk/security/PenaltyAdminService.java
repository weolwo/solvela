package solvela.member.risk.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import solvela.base.util.SolvelaStringUtil;
import solvela.member.RateLimitRule;
import solvela.member.risk.dao.RateLimitRuleDao;
import solvela.security.IpKeys;
import solvela.security.SecurityKeys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台「处罚查询」（网关方案 §6.2）：按 IP / 设备 / 会员号<b>精确</b>查此刻在罚的东西，可以手动解除。
 *
 * <h3>直接 GET 对应的 Redis 键，不扫库、不扫 Redis</h3>
 * 处罚以 Redis 为准（§6.4），库里只有留痕。要查的键全都能从输入算出来：全局处罚两个、封禁镜像一个、
 * 每个限流目标（场景、接口覆盖行）× 维度一个 —— 几十次 GET，不用 KEYS / SCAN。
 *
 * <h3>⚠️ 匿名场景的账号维度查不了</h3>
 * 那里的「账号」是请求体里的邮箱 / 手机号，键里存的是网关用自己的密钥算的 HMAC（为了不在 Redis 里留明文）。
 * 后台没有那把密钥，算不出键。被罚的人换个 IP / 设备就能继续登录，最长也只封 30 分钟（§2.4），所以不为它开一个口子。
 * 登录后场景的账号维度按会员号查（{@code m:会员号}）。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PenaltyAdminService {

    /** 维度编码，与 {@code t_rate_limit_rule.dimension} 一致 */
    private static final int IP = 1, ACCOUNT = 2, DEVICE = 3, IP_SEGMENT = 4;

    private final StringRedisTemplate redis;

    private final RateLimitRuleDao ruleDao;

    /**
     * 一条在罚的东西。
     *
     * @param key        Redis 键（解除时原样传回）
     * @param kind       GLOBAL（蜜罐 / 关键字 / 蜜罐账号，挡所有接口）、RATE_LIMIT（按场景）、BANNED（设备封禁，不在这里解）
     * @param target     限流的场景 / 接口；全局处罚为 null
     * @param untilEpochSec 到期（epoch 秒）；没有到期为 null
     * @param level      限流的档位；其它为 null
     */
    public record Penalty(String key, String kind, String target, String dimension, String value, Long untilEpochSec,
                          Integer level) {
    }

    public List<Penalty> query(String ip, String deviceId, Long memberId) {
        List<Penalty> result = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        String ipKey = SolvelaStringUtil.isBlank(ip) ? null : IpKeys.ip(ip.trim());
        String segment = SolvelaStringUtil.isBlank(ip) ? null : IpKeys.segment(ip.trim());
        String device = SolvelaStringUtil.isBlank(deviceId) ? null : deviceId.trim();
        if (SolvelaStringUtil.isNotBlank(ip) && ipKey == null) {
            throw new IllegalArgumentException("不是合法的 IP: " + ip);
        }

        if (ipKey != null) {
            global(result, SecurityKeys.securityPenaltyIp(ipKey), "IP", ipKey, now);
        }
        if (device != null) {
            global(result, SecurityKeys.securityPenaltyDevice(device), "DEVICE", device, now);
            if (Boolean.TRUE.equals(redis.opsForSet().isMember(SecurityKeys.BANNED_DEVICES, device))) {
                result.add(new Penalty(SecurityKeys.BANNED_DEVICES, "BANNED", null, "DEVICE", device, null, null));
            }
        }
        for (String target : targets()) {
            if (ipKey != null) {
                rateLimit(result, target, IP, "IP", ipKey, now);
            }
            if (segment != null) {
                rateLimit(result, target, IP_SEGMENT, "IP_SEGMENT", segment, now);
            }
            if (device != null) {
                rateLimit(result, target, DEVICE, "DEVICE", device, now);
            }
            if (memberId != null) {
                rateLimit(result, target, ACCOUNT, "ACCOUNT", "m:" + memberId, now);
            }
        }
        return result;
    }

    /**
     * 解除一条。只认处罚键（{@code rl:p:*}、{@code sec:pen:*}）—— 这个接口不能变成「删任意 Redis 键」。
     * 设备封禁不在这里解：它的真相源是 {@code t_device.status}，走设备处置。
     *
     * <p>网关各实例的进程内缓存最多再挡几秒。
     */
    public boolean release(String key, String operator) {
        if (key == null || !(key.startsWith("rl:p:") || key.startsWith("sec:pen:"))) {
            throw new IllegalArgumentException("只能解除处罚键；设备封禁请到设备管理里解封");
        }
        boolean deleted = Boolean.TRUE.equals(redis.delete(key));
        log.warn("【处罚】后台解除, 键: {}, 操作人: {}, 结果: {}", key, operator, deleted ? "已删" : "本来就不在了");
        return deleted;
    }

    private void global(List<Penalty> result, String key, String dimension, String value, long now) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) {
            return;
        }
        long until = parse(raw);
        if (until > now) {
            result.add(new Penalty(key, "GLOBAL", null, dimension, value, until, null));
        }
    }

    private void rateLimit(List<Penalty> result, String target, int dim, String dimension, String value, long now) {
        String key = SecurityKeys.rateLimitPenalty(target, dim, value);
        Map<Object, Object> h = redis.opsForHash().entries(key);
        if (h.isEmpty()) {
            return;
        }
        long until = h.get("until") == null ? 0 : parse(h.get("until").toString());
        if (until > now) {
            Integer level = h.get("level") == null ? null : (int) parse(h.get("level").toString());
            result.add(new Penalty(key, "RATE_LIMIT", target, dimension, value, until, level));
        }
    }

    /** 全部限流目标：场景编码与接口覆盖行的接口键 */
    private Set<String> targets() {
        Set<String> targets = new LinkedHashSet<>();
        for (RateLimitRule r : ruleDao.selectList(new LambdaQueryWrapper<RateLimitRule>()
                .select(RateLimitRule::getTarget).orderByAsc(RateLimitRule::getTargetType))) {
            targets.add(r.getTarget());
        }
        return targets;
    }

    private static long parse(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
