package solvela.member.sign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.event.BizEventPublisher;
import solvela.base.module.redis.RedisService;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.member.api.MemberSignResult;
import solvela.member.service.MemberService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 每日签到。
 *
 * <h3>🔴 它只写 Redis，不落库 —— 这是有意的，不是省事</h3>
 * 签到这件事在这个平台上<b>没有独立价值</b>：用户签到是为了推任务进度、拿任务奖励，
 * 而进度和奖励已经完整地记在 {@code t_task_record} / {@code t_task_record_flow} 里了。
 * 再建一张 {@code t_member_sign}，得到的是<b>同一件事的第二份记账</b>，
 * 而两份记账迟早会对不上（补数、清理、重放各走各的）。
 *
 * <p>Redis 这一份不是账本，只是<b>按钮状态</b>：回答"今天这个按钮还能不能点"。
 * 它丢了最坏的后果是用户能再点一次，而那一次在任务侧仍然不会重复计数
 * （见 {@link #sign} 里那两道幂等）。
 *
 * <p>⚠️ 什么时候该建表：要做<b>签到日历</b>或<b>连续签到天数展示</b>的时候。
 * 那是一个新需求，到那时再建 —— <b>别为了"以后可能要"现在就建</b>，
 * 一张没人读的表只会在某次清理时被人问"这是干嘛的"。
 * （顺带：连续签到的<b>判定</b>今天已经能做了，任务引擎的 STREAK 类型就是干这个的，
 * 差的只是展示。）
 *
 * @author alaric
 * @date 2026-09-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSignService {

    /** 键前缀。真正的键还会带上环境与项目名，由 {@code generateRedisKey} 拼 */
    private static final String SIGN_KEY_PREFIX = "member:sign:";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 键过期的<b>余量</b>。
     *
     * <p>键名里已经带了日期，所以过期只是清理，不参与判重 —— 但余量不能是 0：
     * 服务器时钟与 Redis 时钟有偏差时，一个刚好在 23:59:59 建的键
     * 若 TTL 精确到零点，可能在用户还没看到结果时就没了。
     */
    private static final Duration EXPIRE_MARGIN = Duration.ofMinutes(10);

    private final RedisService redisService;
    private final MemberService memberService;
    /**
     * 业务动作广播。
     *
     * <p>🔴 <b>不是</b>任务引擎 —— {@code solvela-member} 排在 {@code solvela-marketing}
     * 之前，物理上引用不到（写反了 Maven 直接报循环依赖）。
     */
    private final BizEventPublisher bizEventPublisher;

    /**
     * 签到。
     *
     * <h3>幂等靠 {@code INCR} 的返回值，不是"先查再写"</h3>
     * {@code redisService.increment} 里是一段 Lua（INCR + 首次 EXPIRE 原子完成），
     * 返回 1 就说明<b>本次调用是今天的第一次</b>。
     *
     * <p>🔴 写成「先 get 判断有没有、再 set」的话，两次并发点击之间有一个窗口，
     * 两边都会看到"没签过"然后各发一次事件。那一次多发在任务侧会被唯一键挡下，
     * 所以不会算两次 —— 但用户会连着收到两条"签到成功"，
     * 而且日志里的签到量是虚的。<b>能用一次原子操作解决的，不要用两次往返。</b>
     *
     * <h3>🔴 会员存在性必须校验</h3>
     * 没有这一步的话，一个伪造的 memberId 会先在 Redis 里占一个键、
     * 再发出一条指向不存在会员的事件 —— 下游 {@code normalize} 会因为
     * {@code requireMemberName} 抛异常，表现是防腐层里一行看不出根因的 error。
     * 在入口挡掉，错误信息才说得清。
     */
    public MemberSignResult sign(Long memberId) {
        memberService.requireExists(memberId);

        LocalDateTime now = LocalDateTime.now();
        String key = signKey(memberId, now.toLocalDate());
        long count = redisService.increment(key, secondsUntilTomorrow(now));
        if (count != 1L) {
            log.debug("【每日签到】今天已经签过了, memberId: {}, 今日第 {} 次点击", memberId, count);
            return MemberSignResult.repeated();
        }

        /*
         * 广播「今天来过」。
         *
         * ⚠️ 本方法【没有事务】—— 它一行库都不写。防腐层的监听器开了
         *    fallbackExecution，所以这里的事件会【当场同步投递】而不是被丢弃。
         *    那正是开那个开关的原因，见 BizActionEventListener 的类注释。
         *
         * 🔴 bizId 传 null 是对的，不是漏了：签到天然没有业务单号，
         *    由 TaskPeriodResolver 按【事件自然日】兜底生成幂等键 ——
         *    这是第二道防重，独立于上面那个 Redis 键。
         *    Redis 被清空时第一道失效，这一道仍然挡得住重复计数。
         */
        bizEventPublisher.publish(new BizActionEvent(
                BizActionCodes.DAILY_SIGN, memberId, null, null, now, null));

        log.info("【每日签到】签到成功, memberId: {}", memberId);
        return MemberSignResult.first();
    }

    /** 今天签过没有。只读，给按钮状态用 */
    public boolean signedToday(Long memberId) {
        if (memberId == null) {
            return false;
        }
        return redisService.get(signKey(memberId, LocalDate.now())) != null;
    }

    /**
     * 键里带日期，所以跨零点自动换一个键 —— 不需要任何"到点清零"的逻辑。
     *
     * <p>⚠️ 用的是<b>服务器本地时区</b>的自然日，和任务引擎
     * {@code TaskPeriodResolver} 的周期归属是同一个口径。两边若用不同时区，
     * 会出现「按钮说今天签过了、任务却算进了昨天」这种没人能解释的状态。
     */
    private String signKey(Long memberId, LocalDate day) {
        return redisService.generateRedisKey(SIGN_KEY_PREFIX + day.format(DAY) + ":",
                String.valueOf(memberId));
    }

    /** 到明天零点还有多少秒，再加一点余量 */
    private long secondsUntilTomorrow(LocalDateTime now) {
        LocalDateTime tomorrow = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, tomorrow).plus(EXPIRE_MARGIN).toSeconds();
    }
}
