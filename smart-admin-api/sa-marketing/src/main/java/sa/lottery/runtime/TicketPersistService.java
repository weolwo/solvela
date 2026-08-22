package sa.lottery.runtime;

import lombok.RequiredArgsConstructor;
import sa.lottery.config.domain.entity.LotteryConfig;
import sa.lottery.constant.LotteryConst;
import sa.lottery.issue.dao.LotteryIssueDao;
import sa.lottery.issue.domain.entity.LotteryIssue;
import sa.lottery.record.dao.LotteryRecordDao;
import sa.lottery.record.domain.entity.LotteryRecord;
import sa.lottery.runtime.domain.TicketObtainVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import sa.base.common.constant.TenantConst;

/**
 * 领号的落库动作，单独成一个 Bean。
 *
 * <p><b>为什么不能内联回 {@link TicketIssueService}</b>：
 * {@code @Transactional} 靠 Spring AOP 代理生效，而<b>同类内部的自调用不经过代理</b> ——
 * 把本方法写成 TicketIssueService 的私有/保护方法再从 obtain() 里调，
 * 注解会<b>静默失效</b>：没有事务、没有任何报错、编译和测试全都通过，
 * 只有在「记录插进去了但 sold_count 没加上」的那一刻才会暴露。
 * 拆成独立 Bean 是让事务真实生效的最简做法。
 *
 * <p>顺带的好处：领号失败重试时，每次尝试都是一个独立事务，
 * 上一次的 DuplicateKeyException 不会把后续尝试拖进 rollback-only 状态
 * （交接文档里 UnexpectedRollbackException 那个坑就是这么来的）。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@RequiredArgsConstructor
@Service
public class TicketPersistService {

    private final LotteryRecordDao lotteryRecordDao;
    private final LotteryIssueDao lotteryIssueDao;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 中奖状态：0-未开奖
     */
    private static final int WIN_STATUS_WAIT = 0;

    /**
     * 落记录 + 累加 sold_count，同一事务。
     *
     * <p>sold_count 用条件更新兜底（WHERE sold_count &lt; total_count）：
     * 即便游标判定被绕过，DB 这一层也不会让已发数超过上限。
     * 它与游标是两个口径 —— 游标是「消耗掉的槽位」，sold_count 是「真正发出去的张数」，
     * 恒有 {@code 游标 >= sold_count}；硬上限认游标，sold_count 只用于展示与对账。
     */
    @Transactional(rollbackFor = Exception.class)
    public TicketObtainVO persist(LotteryConfig config, LotteryIssue issue, String memberName,
                                  long sequenceNo, String ticketNumber, String securitySign) {
        // 时间取数据库时钟，不用 JVM 的 LocalDateTime.now()（铁律 9：只认数据库一个时钟）
        LocalDateTime now = lotteryIssueDao.selectDbNow();

        LotteryRecord record = new LotteryRecord();
        record.setTenantId(TenantConst.DEFAULT_TENANT_ID);
        record.setLotteryCode(config.getLotteryCode());
        record.setIssueNo(issue.getIssueNo());
        record.setSequenceNo((int) sequenceNo);
        record.setTicketNumber(ticketNumber);
        record.setMemberName(memberName);
        record.setObtainTime(now);
        record.setWinStatus(WIN_STATUS_WAIT);
        // 未开奖统一落 99，让 C 端「我的号码」可以直接 ORDER BY prize_level ASC
        record.setPrizeLevel(LotteryConst.PRIZE_LEVEL_NONE);
        // security_sign 是 NOT NULL 无默认值，漏赋值会被 MySQL 严格模式直接拒绝
        record.setSecuritySign(securitySign);
        lotteryRecordDao.insert(record);

        lotteryIssueDao.increaseSoldCount(issue.getId());

        return new TicketObtainVO(config.getLotteryCode(), issue.getIssueNo(), ticketNumber,
                sequenceNo, securitySign, now.format(TIME_FORMAT));
    }
}
