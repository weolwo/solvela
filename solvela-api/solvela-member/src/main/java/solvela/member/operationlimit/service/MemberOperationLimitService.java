package solvela.member.operationlimit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import solvela.base.module.redis.RedisService;
import solvela.member.operationlimit.MemberOperationLimitProperties;
import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.member.operationlimit.dao.MemberOperationLimitDao;
import solvela.member.MemberOperationLimit;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 会员操作限制：连续失败的计数、触发限制、解冻。
 *
 * <h3>职责边界</h3>
 * 本服务只回答「这个会员的这个操作，现在能不能做」以及「记一次失败」。
 * <b>它不判断密码对不对、不写登录日志</b> —— 那是调用方的事。
 * 这样同一套限制能直接复用到「修改密码」「提现」等其它操作上，不用跟着登录流程走。
 *
 * <h3>计数在 Redis，锁在 MySQL</h3>
 * 失败计数是高频写、可丢失（丢了最坏是攻击者多试几次），进 Redis；
 * 限制本身是低频写、不可丢失、要给客服看要能追溯，进表。
 * 两者故障时的表现也不同：Redis 挂了退化成「不限制」，MySQL 挂了整个登录本来也走不下去。
 *
 * @Date 2026-08-26
 */
@Slf4j
@Service
public class MemberOperationLimitService {

    /**
     * 失败计数的 key 前缀。{@code RedisService#generateRedisKey} 会再拼上环境，
     * 所以 dev / test 不会互相污染
     */
    private static final String FAIL_COUNT_KEY_PREFIX = "member:operation-limit:fail";

    /**
     * 通知里展示解冻时间的格式。
     *
     * <p>不用 ISO 的 {@code 2026-09-14T15:04:05} —— 那个 T 会让用户以为是乱码。
     */
    private static final DateTimeFormatter UNLOCK_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MemberOperationLimitDao memberOperationLimitDao;

    private final MemberOperationLimitProperties properties;

    private final RedisService redisService;

    /**
     * 通知：账号被限制时告诉用户一声。
     *
     * <p>此前用户被限制之后<b>什么提示都没有</b> —— 只能自己反复试、试到怀疑账号被盗，
     * 然后打客服。而「还要等多久」恰恰是这个功能最该说清楚的一件事。
     */
    private final NotificationService notificationService;

    public MemberOperationLimitService(MemberOperationLimitDao memberOperationLimitDao,
                                       MemberOperationLimitProperties properties,
                                       RedisService redisService,
                                       NotificationService notificationService) {
        this.memberOperationLimitDao = memberOperationLimitDao;
        this.properties = properties;
        this.redisService = redisService;
        this.notificationService = notificationService;
    }

    /**
     * 取当前生效中的限制；没有则返回 null。
     *
     * <p>调用方拿到非 null 就该中止操作，并把 {@code getExpireTime()} 告诉用户 ——
     * 「还要等多久」是这个功能最该说清楚的一件事，含糊其辞只会把人逼去打客服电话。
     */
    public MemberOperationLimit getActiveLimit(Long memberId, MemberOperationTypeEnum operationType) {
        if (memberId == null || operationType == null) {
            return null;
        }
        return memberOperationLimitDao.selectActive(memberId, operationType, LocalDateTime.now());
    }

    /**
     * 记一次失败。达到阈值则当场落一行限制并返回它；未达阈值返回 null。
     *
     * @param reason 触发原因，写进表里给客服看的人话
     */
    public MemberOperationLimit recordFail(Long memberId, MemberOperationTypeEnum operationType, String reason) {
        if (memberId == null || operationType == null || properties.getFailMaxTimes() < 1) {
            return null;
        }

        long failCount = redisService.increment(failCountKey(memberId, operationType), properties.getFailWindowSeconds());
        if (failCount < properties.getFailMaxTimes()) {
            return null;
        }

        // 已经在限制中就不再叠加：否则被限期间的每一次尝试都会把到期时间往后推，
        // 变成「越试越久」的无限延长，用户永远等不到自动解除。
        MemberOperationLimit active = this.getActiveLimit(memberId, operationType);
        if (active != null) {
            return active;
        }

        LocalDateTime now = LocalDateTime.now();
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(memberId);
        limit.setOperationType(operationType);
        limit.setLockTime(now);
        limit.setExpireTime(now.plusSeconds(properties.getLockSeconds()));
        limit.setStatus(MemberOperationLimitStatusEnum.LOCKED);
        limit.setReason(reason);
        limit.setCreateTime(now);
        limit.setUpdateTime(now);
        memberOperationLimitDao.insert(limit);

        // 计数已经兑现成一条限制，留着它会让解冻后「一次失败就又被限」
        this.clearFail(memberId, operationType);
        log.info("会员操作限制触发 memberId={} operation={} 到期={} 原因={}",
                memberId, operationType.getDesc(), limit.getExpireTime(), reason);

        // 告诉用户一声。ACCOUNT_LIMITED 属于 SYSTEM 分类，用户关不掉免打扰 ——
        // 「你被限制了、到什么时候」不该是可以被静音的东西。
        //
        // ⚠️ send() 永不抛异常，所以这里不需要 try-catch，也不会因为模板没配
        //    就让限制记不下来。反过来，如果上面那条 insert 所在的事务回滚了，
        //    这条通知也跟着回滚 —— 那是对的，没被限制却收到「你被限制了」更糟。
        //
        // 🔴 reason 是给客服看的内部措辞，【刻意不传给用户】：它可能含
        //    「连续5次密码错误」这类对攻击者有用的信息。用户只需要知道
        //    受限的操作类型和恢复时间。
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.ACCOUNT_LIMITED, memberId)
                .param("limitType", operationType.getDesc())
                .param("unlockTime", limit.getExpireTime().format(UNLOCK_TIME_FORMATTER))
                .bizRefId(String.valueOf(limit.getId()))
                .build());

        return limit;
    }

    /**
     * 清空失败计数。操作成功时调用
     */
    public void clearFail(Long memberId, MemberOperationTypeEnum operationType) {
        if (memberId == null || operationType == null) {
            return;
        }
        redisService.delete(failCountKey(memberId, operationType));
    }

    /**
     * 解冻。幂等：没有生效中的限制时什么都不做，返回 false。
     *
     * @param unlockType     解冻方式；{@link MemberOperationUnlockTypeEnum#MANUAL} 时 operator 与 remark 必填
     * @param unlockOperator 人工解冻的操作人，其余方式传 null
     * @param remark         解冻原因，人工解冻时由客服填写；自动到期 / 重置密码传 null
     */
    public boolean unlock(Long memberId, MemberOperationTypeEnum operationType,
                          MemberOperationUnlockTypeEnum unlockType, String unlockOperator, String remark) {
        if (memberId == null || operationType == null) {
            return false;
        }
        int rows = memberOperationLimitDao.unlock(memberId, operationType,
                LocalDateTime.now(), unlockType, unlockOperator, remark);
        if (rows > 0) {
            // 解了锁却留着计数，等于「解冻后再错一次立刻又被限」
            this.clearFail(memberId, operationType);
            log.info("会员操作限制解除 memberId={} operation={} 方式={} 操作人={}",
                    memberId, operationType.getDesc(), unlockType.getDesc(), unlockOperator);
        }
        return rows > 0;
    }

    /**
     * 把已过期却还挂着「冻结中」的行推到终态，供定时任务调用。
     *
     * <p>纯粹为了让 status 列可信 —— 业务判断从不依赖它，见
     * {@link MemberOperationLimitDao#selectActive}。所以这个任务漏跑几次没有业务影响。
     */
    public int settleExpired() {
        return memberOperationLimitDao.settleExpired(LocalDateTime.now());
    }

    /**
     * 某会员最近的限制历史，客服排查用
     */
    public List<MemberOperationLimit> listRecentByMember(Long memberId, int limit) {
        return memberOperationLimitDao.selectRecentByMember(memberId, limit);
    }

    private String failCountKey(Long memberId, MemberOperationTypeEnum operationType) {
        return redisService.generateRedisKey(FAIL_COUNT_KEY_PREFIX, operationType.getValue() + ":" + memberId);
    }
}
