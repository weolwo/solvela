package solvela.lottery.runtime;

import solvela.enums.LotteryConfigStatusEnum;
import solvela.enums.IssueStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.lottery.LotteryConfig;
import solvela.lottery.config.service.LotteryConfigService;
import solvela.lottery.engine.FpeCipher;
import solvela.lottery.engine.FpeCipherFactory;
import solvela.lottery.issue.dao.LotteryIssueDao;
import solvela.lottery.LotteryIssue;
import solvela.lottery.issue.manager.LotteryIssueManager;
import solvela.lottery.runtime.domain.TicketObtainCommand;
import solvela.lottery.runtime.domain.TicketObtainDTO;
import solvela.member.service.MemberService;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 领号编排（运行态核心链路）。
 *
 * <pre>
 * 幂等防重(requestId SETNX)
 *   → 防刷限流(RRateLimiter)
 *   → 玩法/期号校验(上线中 + 落在售卖窗口内)
 *   → Redis INCR 游标  ── 超过 total_count 即售罄，且【绝不回滚】
 *   → FPE 算号 + security_sign
 *   → 落 t_lottery_record（uk_issue_ticket 兜底）
 *   → DB 条件更新 sold_count
 * </pre>
 *
 * <h3>没有资产扣减，是刻意的</h3>
 * 本模块是纯粹的号码派发引擎：消耗多少积分、单人限购几张，都由上游业务算完再调进来。
 * 所以这条链路里既没有扣减也没有退还，比抽奖的 DrawExecuteService 短得多。
 * 保留的幂等与限流防的是网络重试和恶意刷接口，与业务限购不是一回事。
 *
 * <h3>方法签名为什么是平铺参数</h3>
 * {@link #obtain} 后续要挂 {@code @ScriptFunction} 暴露给 QLExpress。
 * QLExpress 本身是能传对象的，这里选平铺参数纯粹是为了<b>规则脚本的可读性</b>：
 * <pre>
 * ticket = obtainTicket('RMAAUK45TG', '2026_MID_01', memberId);   // 一行说清
 * </pre>
 * 比让规则作者先 new 一个 Form、再逐个 setter 要顺手得多。
 * 若上游已经持有现成的上下文对象、更愿意整个传进来，加一个收 Form 的重载即可，
 * 两者可以共存 —— 核心逻辑只有这一份。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TicketIssueService {

    private final LotteryConfigService lotteryConfigService;
    private final LotteryIssueManager lotteryIssueManager;
    private final LotteryIssueDao lotteryIssueDao;
    private final LotterySequenceService lotterySequenceService;
    private final TicketPersistService ticketPersistService;
    private final TicketSignService ticketSignService;
    private final FpeCipherFactory fpeCipherFactory;
    private final RedissonClient redissonClient;
    /**
     * 会员号 -> 账号。一次领号只查一次：记录上的展示快照与签名要素都用它，
     * 顺带把「会员号根本不存在」挡在发号之前 —— 号一旦发出去就收不回来了。
     */
    private final MemberService memberService;
    /**
     * 期号状态：0-待开奖（可售卖）。已进入核销或已开奖的期号不再发号
     */

    /**
     * 防刷限流：单用户单玩法每秒 5 次。
     * 比抽奖的 2 次/秒宽松 —— 领号没有资产消耗，且上游可能批量代领
     */
    private static final long RATE_LIMIT_PER_INTERVAL = 5;
    private static final long RATE_LIMIT_INTERVAL_SECONDS = 1;
    private static final Duration RATE_LIMITER_TTL = Duration.ofHours(1);

    private static final Duration REQUEST_DEDUP_TTL = Duration.ofMinutes(10);

    /**
     * 撞唯一索引后的重试上限。正常情况一次都不会发生（游标是双射的），
     * 真发生说明游标状态异常，重试几次给个自愈机会，但不能无限重试掩盖问题
     */
    private static final int DUPLICATE_RETRY_LIMIT = 3;

    /**
     * 发一个号码（对象入参重载）。
     *
     * <p>与平铺参数版是同一段逻辑，只是入口不同：
     * 上游若已经持有一个完整的领号上下文，整个传进来比拆成几个参数顺手。
     * <b>核心实现只有下面那一份，这里只做拆包</b> —— 两个入口的行为不可能漂移。
     */
    public TicketObtainDTO obtain(TicketObtainCommand form) {
        return obtain(form.getLotteryCode(), form.getIssueNo(), form.getMemberId(), form.getRequestId());
    }

    /**
     * 发一个号码。
     *
     * <p>事务边界：只包住「落记录 + 更新 sold_count」，且落库动作在
     * {@link TicketPersistService} 这个独立 Bean 里 —— 同类自调用不走 AOP 代理，
     * {@code @Transactional} 会静默失效，那个坑必须绕开。
     * Redis 游标<b>刻意不在事务内也不做补偿</b> —— 事务回滚时游标不退回，
     * 留下一个空洞，这正是我们要的：浪费一个号可以接受，发重号不行。
     *
     * @param lotteryCode 彩票编码
     * @param issueNo     期号
     * @param memberId    会员号（关联键）。账号快照由本方法查会员表取，调用方不用传
     * @param requestId   幂等键，可为空；传了则同一个 requestId 只会发出一个号码
     */
    public TicketObtainDTO obtain(String lotteryCode, String issueNo, Long memberId, String requestId) {
        checkNotDuplicate(requestId);
        // 会员号必须真实存在；顺带取回账号 —— 它既是记录上的展示快照，也是签名要素。
        // 一次领号只查一次，不在用到名字的每个地方各查一次。
        String memberName = memberService.requireMemberName(memberId);
        acquireRateLimit(lotteryCode, memberId);

        LotteryConfig config = requireOnlineConfig(lotteryCode);
        LotteryIssue issue = requireSellableIssue(lotteryCode, issueNo);

        return issueTicket(config, issue, memberId, memberName);
    }

    /**
     * 幂等防重。<b>先于限流</b>：一次网络重试不该白白消耗一个令牌。
     */
    private void checkNotDuplicate(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        boolean first = redissonClient.getBucket(LotteryCacheKey.request(requestId), StringCodec.INSTANCE)
                .setIfAbsent("1", REQUEST_DEDUP_TTL);
        if (!first) {
            throw new BusinessException("请求处理中或已处理，请勿重复提交");
        }
    }

    /**
     * 防刷限流。
     *
     * <p>🔴 限流 key 用<b>会员号</b>不用账号：账号可改，改完就是一个全新的 key，限流当场归零。
     */
    private void acquireRateLimit(String lotteryCode, Long memberId) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LotteryCacheKey.rateLimit(lotteryCode, memberId));
        if (limiter.trySetRate(RateType.OVERALL, RATE_LIMIT_PER_INTERVAL,
                RATE_LIMIT_INTERVAL_SECONDS, RateIntervalUnit.SECONDS)) {
            limiter.expire(RATE_LIMITER_TTL);
        }
        if (!limiter.tryAcquire()) {
            throw new BusinessException("操作太频繁，请稍后再试");
        }
    }

    private LotteryConfig requireOnlineConfig(String lotteryCode) {
        LotteryConfig config = lotteryConfigService.getByLotteryCode(lotteryCode);
        if (config == null) {
            throw new BusinessException("彩票玩法不存在：" + lotteryCode);
        }
        if (config.getStatus() != LotteryConfigStatusEnum.ONLINE) {
            throw new BusinessException("彩票玩法未上线，暂不能领号");
        }
        return config;
    }

    /**
     * 期号必须存在、待开奖，且当前落在售卖窗口内。
     *
     * <p>售卖窗口用<b>数据库时钟</b>判定（铁律 9/10：不引第二个时钟源）——
     * 应用服务器的时钟与库不一致时，会出现「页面显示可领、领号被拒」这种查无可查的现象。
     */
    private LotteryIssue requireSellableIssue(String lotteryCode, String issueNo) {
        LotteryIssue issue = lotteryIssueManager.lambdaQuery()
                .eq(LotteryIssue::getLotteryCode, lotteryCode)
                .eq(LotteryIssue::getIssueNo, issueNo).one();
        if (issue == null) {
            throw new BusinessException("期号不存在：" + issueNo);
        }
        if (issue.getStatus() != IssueStatusEnum.WAIT) {
            throw new BusinessException("该期已开奖或正在核销，不能再领号");
        }
        LocalDateTime now = lotteryIssueDao.selectDbNow();
        if (issue.getSaleStartTime() != null && now.isBefore(issue.getSaleStartTime())) {
            throw new BusinessException("该期尚未开始发售");
        }
        if (issue.getSaleEndTime() != null && now.isAfter(issue.getSaleEndTime())) {
            throw new BusinessException("该期已停止发售");
        }
        return issue;
    }

    /**
     * 取游标 -> 算号 -> 签名 -> 落库，撞唯一索引时换下一个游标重试。
     *
     * <p>游标与号码是<b>双射</b>关系（FPE 加密），同一个游标必然算出同一个号码、
     * 不同游标必然算出不同号码 —— 所以正常情况下这个循环只会走一次。
     */
    private TicketObtainDTO issueTicket(LotteryConfig config, LotteryIssue issue, Long memberId, String memberName) {
        String lotteryCode = config.getLotteryCode();
        String issueNo = issue.getIssueNo();
        FpeCipher cipher = fpeCipherFactory.create(lotteryCode, issueNo, config.getNumberLength());

        for (int attempt = 0; attempt < DUPLICATE_RETRY_LIMIT; attempt++) {
            long cursor = lotterySequenceService.nextCursor(lotteryCode, issueNo);
            if (cursor > config.getTotalCount()) {
                // 售罄。游标已经涨上去了也不退回 —— 见类注释
                throw new BusinessException("本期号码已发完");
            }
            long sequenceNo = lotterySequenceService.toSequenceNo(cursor, config.getTotalCount(), cipher.domain());
            String ticketNumber = cipher.encrypt(sequenceNo);
            String securitySign = ticketSignService.sign(lotteryCode, issueNo, sequenceNo, ticketNumber, memberName);

            try {
                return ticketPersistService.persist(
                        config, issue, memberId, memberName, sequenceNo, ticketNumber, securitySign);
            } catch (DuplicateKeyException e) {
                // 双射保证了这不该发生。真发生说明 Redis 游标与 DB 不一致（如误清了 Redis），
                // 不静默吞：告警 + 换游标重试，重试耗尽则如实报错
                log.error("[彩票发号] 号码撞唯一索引，游标状态可能异常 lotteryCode={}, issueNo={}, "
                                + "cursor={}, sequenceNo={}, ticketNumber={}, 第{}次重试",
                        lotteryCode, issueNo, cursor, sequenceNo, ticketNumber, attempt + 1);
            }
        }
        throw new BusinessException("系统繁忙，请稍后重试");
    }

}
