package solvela.member.operationlimit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;
import solvela.base.module.redis.RedisService;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.MemberOperationLimitStatusEnum;
import solvela.enums.MemberOperationTypeEnum;
import solvela.enums.MemberOperationUnlockTypeEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.member.Member;
import solvela.member.MemberOperationLimit;
import solvela.member.auth.MemberAuthDao;
import solvela.member.operationlimit.MemberOperationLimitProperties;
import solvela.member.operationlimit.dao.MemberOperationLimitDao;
import solvela.member.risk.feature.PasswordCooldownStore;
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

    /** 解冻 LOGIN 时要按会员号取到他的联系方式，才算得出冷却键用的身份摘要 */
    private final MemberAuthDao memberAuthDao;

    private final PiiCipher piiCipher;

    private final PiiHasher piiHasher;

    /**
     * 密码阶梯冷却。
     *
     * <p>🔴 延迟取：冷却那边要读规则表，而规则 bean 的装配会带起一串风控组件，
     * 其中有的又会用到本服务 —— 构造器直接注入在某些顺序下成环。
     * 同 {@code PasswordCooldownStore} 自己取 registry 的做法。
     *
     * <p>用 {@code ObjectFactory} 而不是 {@code ObjectProvider}：两者 Spring 都注得进来，
     * 而前者是单方法接口 —— 测试里一个 lambda 就够，后者要手写五个空实现。
     */
    private final ObjectFactory<
            PasswordCooldownStore> cooldownStoreProvider;

    public MemberOperationLimitService(MemberOperationLimitDao memberOperationLimitDao,
                                       MemberOperationLimitProperties properties,
                                       RedisService redisService,
                                       NotificationService notificationService,
                                       MemberAuthDao memberAuthDao,
                                       PiiCipher piiCipher,
                                       PiiHasher piiHasher,
                                       ObjectFactory<
                                               PasswordCooldownStore>
                                               cooldownStoreProvider) {
        this.memberOperationLimitDao = memberOperationLimitDao;
        this.properties = properties;
        this.redisService = redisService;
        this.notificationService = notificationService;
        this.memberAuthDao = memberAuthDao;
        this.piiCipher = piiCipher;
        this.piiHasher = piiHasher;
        this.cooldownStoreProvider = cooldownStoreProvider;
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
     * 直接冻结，<b>不走失败计数</b>：风控或用户举报当场认定要冻。
     *
     * <h3>和 {@link #recordFail} 的分工</h3>
     * 那个是「又错了一次，到阈值才冻」；这个是「已经有结论了，现在就冻」。
     * 走 {@code recordFail} 的话要先把计数推到阈值，而这里压根没有「几次」这个概念。
     *
     * <h3>🔴 幂等：已经冻着就什么都不做</h3>
     * 不然用户连点两次「这不是我」，表里会多一行，而客服看到两条生效中的限制
     * 会不知道该解哪一个（{@code selectActive} 取最晚那条，于是解掉前一条也没用）。
     *
     * @param expireTime 到期时间。{@code ASSET_OUT} 这类「靠改密解除」的要给一个很远的日期 ——
     *                   那一列是 NOT NULL，但到期<b>不是</b>它预期的出口
     * @return 生效中的那一行（新建的，或已经存在的）
     */
    public MemberOperationLimit freeze(Long memberId, MemberOperationTypeEnum operationType,
                                       String reason, LocalDateTime expireTime) {
        if (memberId == null || operationType == null || expireTime == null) {
            return null;
        }
        MemberOperationLimit active = this.getActiveLimit(memberId, operationType);
        if (active != null) {
            return active;
        }
        LocalDateTime now = LocalDateTime.now();
        MemberOperationLimit limit = new MemberOperationLimit();
        limit.setMemberId(memberId);
        limit.setOperationType(operationType);
        limit.setLockTime(now);
        limit.setExpireTime(expireTime);
        limit.setStatus(MemberOperationLimitStatusEnum.LOCKED);
        limit.setReason(reason);
        limit.setCreateTime(now);
        limit.setUpdateTime(now);
        memberOperationLimitDao.insert(limit);
        log.info("会员操作限制（直接冻结） memberId={} operation={} 到期={} 原因={}",
                memberId, operationType.getDesc(), expireTime, reason);
        return limit;
    }

    /** 此刻这个操作被冻着吗。{@link #getActiveLimit} 的布尔版，调用方多半只关心这一点 */
    public boolean isFrozen(Long memberId, MemberOperationTypeEnum operationType) {
        return this.getActiveLimit(memberId, operationType) != null;
    }

    /**
     * 解冻登录限制时顺手清掉密码阶梯冷却。
     *
     * <p>冷却是按<b>身份摘要</b>算的，而后台只知道会员号 —— 所以要按号取回联系方式再算摘要。
     * 手机号和邮箱<b>两条都清</b>：这个账号可能用任一条登录，而阶梯按「登录时用的那个身份」计数。
     *
     * <p>🔴 {@code selectContacts} 是专门为核身加的那个窄查询（只取两列密文），
     * 不是登录主干那个 —— 后者刻意不取 PII。
     *
     * <p>查不到人（已注销）就只清表，不抛：客服要的结果是「这个人不再被限」。
     */
    private void clearPasswordCooldown(Long memberId) {
        try {
            Member contacts = memberAuthDao.selectContacts(memberId);
            if (contacts == null) {
                return;
            }
            PasswordCooldownStore store = cooldownStoreProvider.getObject();
            clearByContact(store, contacts.getEmail());
            clearByContact(store, contacts.getPhone());
        } catch (RuntimeException e) {
            log.error("会员操作限制解冻时清密码冷却失败 memberId={}", memberId, e);
        }
    }

    private void clearByContact(PasswordCooldownStore store, String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return;
        }
        String plain = piiCipher.decrypt(cipher);
        if (plain != null && !plain.isBlank()) {
            // deviceId 传 null：clear 本来就把两个桶都清掉（可信设备桶 + 共用桶）。
            // 只清一个的话，用户换台设备还会一脚踩进另一个桶里攒下的计数
            store.onIdentityVerified(piiHasher.hash(plain), null);
        }
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
     * <h3>🔴 {@code LOGIN} 这一档还要清 Redis 里的密码冷却键</h3>
     * 真正拦住用户的<b>不是这张表</b>，而是 {@code PasswordCooldownStore} 在 Redis 里
     * 那个带 TTL 的键 —— 这张表只是给后台看的副本（由 {@code RiskAction.RECORD_LIMIT} 落的）。
     * 只改这一行的话，客服看到「已解冻」，而用户照样登不进去，然后再打一次电话。
     *
     * <p>这正是这颗解冻按钮迟迟没接的原因（方案 §9）：一个解除了却不生效的按钮比没有按钮更糟。
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
        if (rows > 0 && operationType == MemberOperationTypeEnum.LOGIN) {
            // 🔴 见方法注释：不清 Redis 的话这颗按钮是假的
            clearPasswordCooldown(memberId);
        }
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
