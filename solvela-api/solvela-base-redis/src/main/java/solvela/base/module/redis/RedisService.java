package solvela.base.module.redis;

import jakarta.annotation.Resource;
import solvela.base.domain.SystemEnvironment;
import solvela.base.enumeration.SystemEnvironmentEnum;
import solvela.base.constant.StringConst;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 上<b>做了决策</b>的那几件事：环境隔离的 key 前缀，以及原子的固定窗口计数。
 *
 * <h3>🔴 这不是 RedisUtil，请不要往这里加转发方法</h3>
 * 判据只有一条：<b>一个方法值不值得放进来，看它替调用方做掉了多少决策</b>。
 * {@code set(k, v, ttl)} 什么都没决定，它只是把 {@code opsForValue().set} 抄了一遍，
 * 还顺手丢掉了 {@link StringRedisTemplate} 的泛型、pipeline 与批量能力 ——
 * 加这种方法不是封装，是多一层需要维护的转述。
 *
 * <p>要封装就封装<b>业务能力</b>：令牌存取去看 {@code MemberTokenStore}，
 * 那一层的调用方看到的是 {@code issue / resolve / revoke}，根本不知道底下是 Redis；
 * key 结构去看 {@code DrawCacheKey} / {@code LotteryCacheKey}，
 * 把「运维要按什么模式扫」这件事定在一处。这两种都比 RedisUtil 有价值得多。
 *
 * <p>这条规矩是被实证过的：本类原有 19 个 public 方法，其中 13 个（{@code hasKey}、
 * {@code expire}、{@code mset}、{@code mget}、{@code getObject}、若干 {@code set} 重载……）
 * 从落地起<b>一个调用方都没有</b>，写了两年没人用；而唯一被三处业务同时依赖的
 * {@link #increment}，恰好是唯一一个有决策的方法。2026-09-05 已清理，
 * 同批删掉的还有 {@code getLock / unLock} —— 那是一对没有 owner 校验的 SETNX 土锁
 * （任何人都能 unlock 别人持有的锁），分布式锁统一走 Redisson 的 {@code @RedisLock}。
 *
 * @Author 1024创新实验室: 罗伊
 * @Date 2020/8/25 21:57
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */
@Service
public class RedisService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SystemEnvironment systemEnvironment;

    /**
     * 拼出带环境隔离的 key：{@code 项目名:环境:前缀+key}。
     *
     * <p>前两段是隔离位。开发、测试、预发经常共用一个 Redis 实例，少了它，
     * 测试环境的一次「清验证码」会把预发用户的验证码一起清掉，而两边日志都正常。
     */
    public String generateRedisKey(String prefix, String key) {
        SystemEnvironmentEnum currentEnvironment = systemEnvironment.getCurrentEnvironment();
        return systemEnvironment.getProjectName() + StringConst.COLON + currentEnvironment.getValue()
                + StringConst.COLON + prefix + key;
    }

    /**
     * 计数 +1，并<b>只在第一次</b>计数时打上过期时间，返回累计值。
     *
     * <p>「只在第一次」是关键：每次都 expire 会让 TTL 被不断续期，窗口永远不结束 ——
     * 一个每 29 分钟试一次密码的脚本能把计数一直累加到锁定，而它其实从没在任一 30 分钟窗口里
     * 超过阈值。这里的语义是<b>固定窗口</b>：第一次失败起算，TTL 到点整个计数清零。
     *
     * <p>🔴 <b>INCR 与 EXPIRE 必须在同一个脚本里</b>，不能写成两条命令。
     * INCR 自己是原子的，但「INCR 完再 EXPIRE」这一对不是：两条命令之间应用被 kill、
     * 连接断开、或 Redis 主从切换，都会留下一个<b>没有 TTL 的计数键</b>。
     * 那个键此后永不过期，计数只增不减 —— 表现是这个会员/这个活动的限流<b>再也不会解除</b>，
     * 而且查不出原因：代码看着没问题，数据库里也没有任何限制记录。
     *
     * <p>脚本里还多了一层保险：即使键已存在却没有 TTL（历史遗留的坏键），
     * 也会补上过期时间，让老数据自己愈合。
     *
     * @param expireSeconds 窗口长度（秒）
     */
    public long increment(String key, long expireSeconds) {
        Long count = stringRedisTemplate.execute(INCR_WITH_TTL, List.of(key), String.valueOf(expireSeconds));
        return count == null ? 0L : count;
    }

    /**
     * INCR + 首次设置 TTL，一次往返、原子完成。
     *
     * <p>{@code PTTL} 返回 -1 表示键存在但没有过期时间，-2 表示键不存在。
     * 新建（返回值 1）或发现没有 TTL 时都补上 —— 后者是为了修复历史上由非原子写法留下的坏键。
     */
    private static final RedisScript<Long> INCR_WITH_TTL = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 or redis.call('PTTL', KEYS[1]) == -1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return n
            """, Long.class);

    /**
     * 剩余过期秒数。-1 = 键存在但没有 TTL，-2 = 键不存在。
     *
     * <p>限流场景靠它把「还要等多久」告诉用户，所以这两个负值要原样透出去，
     * 不能归一成 0 —— 调用方分不清「已经解封」和「永远不会解封」。
     */
    public long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public String get(String key) {
        return key == null ? null : stringRedisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value, long second) {
        stringRedisTemplate.opsForValue().set(key, value, second, TimeUnit.SECONDS);
    }

    /** 删除一个或多个 key。多个时走一次 DEL，不是循环单删。 */
    public void delete(String... key) {
        if (key == null || key.length == 0) {
            return;
        }
        stringRedisTemplate.delete(Arrays.asList(key));
    }
}
