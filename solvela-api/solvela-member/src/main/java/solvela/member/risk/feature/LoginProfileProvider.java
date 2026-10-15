package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.base.module.redis.RedisService;
import solvela.enums.LoginLogResultEnum;
import solvela.member.Member;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.member.loginlog.domain.dto.LoginTraceDTO;
import solvela.member.risk.RiskContext;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 算出一个会员的登录画像，并缓存一天。
 *
 * <h3>为什么不建画像表</h3>
 * 它<b>能从登录日志算出来</b>（SQL README 的判据：凡是能从库里查出来的，就不要让人维护第二份）。
 * 建表就要有人保证它和登录日志一致，而不一致的那天不会有任何报错 ——
 * 只是风控开始按一份过期的「常用地」判人。
 *
 * <h3>缓存一天，成功登录后删</h3>
 * 画像变化很慢（常用网段、常去国家），但<b>刚登录成功之后必须立刻变</b>：
 * 不删的话，用户在新网络上验过一次码、下一次来还是「陌生网络」，于是反复要他验。
 *
 * <h3>注册 IP 也算一个常用网段</h3>
 * 否则一个注册完还没登录过的人，第二次打开就是「陌生网络」——
 * 而他用的就是刚注册那台机器、那个网络。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginProfileProvider {

    private static final String FEATURE_KEY = "loginProfile";

    private static final String KEY_PROFILE = "mbr:risk:profile:";

    private static final Duration TTL = Duration.ofDays(1);

    /** 最多记几个不同网段。只要最近那几个就够了，而被撞库刷过的账号可能有上千条记录 */
    private static final int MAX_PREFIXES = 10;

    /** 一次最多读多少条登录记录。10 个不同网段通常落在前几十条里 */
    private static final int SCAN_LIMIT = 100;

    private final MemberLoginLogDao loginLogDao;

    private final IpIntel ipIntel;

    private final RedisService redisService;

    /** 同一次尝试里只算一次（多条规则都要用它） */
    public LoginProfile of(RiskContext ctx, int lookbackDays) {
        if (ctx.memberId() == null) {
            // 查无此人：没有画像可言。返回空而不是跳过 —— 规则要能一致地处理这种情况
            return LoginProfile.EMPTY;
        }
        return ctx.feature(FEATURE_KEY, () -> load(ctx.getMember(), lookbackDays));
    }

    /** 登录成功后调：画像变了，缓存要立刻失效 */
    public void evict(Long memberId) {
        if (memberId == null) {
            return;
        }
        try {
            redisService.delete(key(memberId));
        } catch (RuntimeException e) {
            log.warn("【风控画像】缓存清理失败, memberId: {}", memberId, e);
        }
    }

    private LoginProfile load(Member member, int lookbackDays) {
        Long memberId = member.getMemberId();
        LoginProfile cached = readCache(memberId);
        if (cached != null) {
            return cached;
        }
        LoginProfile profile = compute(member, lookbackDays);
        writeCache(memberId, profile);
        return profile;
    }

    private LoginProfile compute(Member member, int lookbackDays) {
        List<LoginTraceDTO> traces = loginLogDao.selectRecentSuccess(
                member.getMemberId(), LoginLogResultEnum.LOGIN_SUCCESS.getValue(), lookbackDays, SCAN_LIMIT);

        Set<String> prefixes = new LinkedHashSet<>();
        Set<String> places = new LinkedHashSet<>();
        Set<String> countries = new LinkedHashSet<>();
        LoginProfile.LastSuccess last = null;

        // 查询是时间倒序的，所以第一条就是上一次成功登录
        for (LoginTraceDTO trace : traces) {
            IpIntel.Location location = ipIntel.resolve(trace.getClientIp());
            if (last == null && !location.unknown()) {
                last = new LoginProfile.LastSuccess(location.country(), location.province(), trace.getCreateTime());
            }
            if (location.country() != null) {
                countries.add(location.country());
            }
            if (location.cityIspKey() != null) {
                places.add(location.cityIspKey());
            }
            // 🔴 只在还没满的时候加：满了之后继续加会把更早的网段也算成「常用」，
            // 而「最近 10 个」这个口径的意义正是「他现在在用的那些」
            if (location.prefix() != null && prefixes.size() < MAX_PREFIXES) {
                prefixes.add(location.prefix());
            }
        }

        // 注册 IP 单独补一个网段，不占「最近 10 个」的额度
        IpIntel.Location registered = ipIntel.resolve(member.getRegisterIp());
        if (registered.prefix() != null) {
            prefixes.add(registered.prefix());
        }
        if (registered.cityIspKey() != null) {
            places.add(registered.cityIspKey());
        }

        return new LoginProfile(Set.copyOf(prefixes), Set.copyOf(places), Set.copyOf(countries),
                last, traces.size());
    }

    // ------------------------------------------------------------------ 缓存

    private LoginProfile readCache(Long memberId) {
        try {
            String raw = redisService.get(key(memberId));
            return raw == null ? null : JsonUtils.getMapper().readValue(raw, LoginProfile.class);
        } catch (RuntimeException e) {
            // 缓存读不出来（格式变了、有人手改了）就重算一次。绝不能因此让登录失败
            log.warn("【风控画像】缓存读取失败，改为重算, memberId: {}", memberId, e);
            return null;
        }
    }

    private void writeCache(Long memberId, LoginProfile profile) {
        try {
            redisService.set(key(memberId), JsonUtils.getMapper().writeValueAsString(profile), TTL.toSeconds());
        } catch (RuntimeException e) {
            log.warn("【风控画像】缓存写入失败，不影响本次判断, memberId: {}", memberId, e);
        }
    }

    private String key(Long memberId) {
        return redisService.generateRedisKey(KEY_PROFILE, String.valueOf(memberId));
    }
}
