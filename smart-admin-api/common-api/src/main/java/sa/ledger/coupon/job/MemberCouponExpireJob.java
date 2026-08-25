package sa.ledger.coupon.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sa.base.module.support.jobspi.constant.SmartJobLaneEnum;
import sa.base.module.support.jobspi.core.JobParam;
import sa.base.module.support.jobspi.core.SmartJob;
import sa.base.module.support.jobspi.core.SmartJobContext;
import sa.base.module.support.jobspi.core.SmartJobHandler;
import sa.ledger.coupon.dao.MemberCouponDao;

import java.time.LocalDateTime;

/**
 * 优惠券过期收口：把过了有效期还挂在「未使用」的券置为 2-已过期。
 *
 * <h3>为什么需要它</h3>
 * 在这个任务之前，全工程<b>没有任何地方</b>把券写成 2-已过期 —— 既没有定时任务，
 * 也没有任何一行 Java 代码。后果不是"少了个状态"这么轻：
 * <ul>
 *   <li>用户端会一直看到一张<b>永远用不了的券</b>，点进去才发现过期了；</li>
 *   <li>按状态做的统计全部虚高 —— 「未使用 500 张」里混着一批早就作废的，
 *       运营据此判断"券发多了没人用"，结论是错的；</li>
 *   <li>没人查就永远发现不了，因为它不报错、不告警，只是数字不对。</li>
 * </ul>
 *
 * <h3>只改状态，不碰别的</h3>
 * 收口就是收口：只把 {@code status 0 -> 2}，不发通知、不退还任何东西。
 * 过期本身是券的自然终态，不是一次失败，没有需要补偿的事。
 *
 * <p>幂等：条件里带着 {@code status = 0}，重复跑第二遍影响行数就是 0。
 * 因此允许配失败重试。
 *
 * <p>⚠️ 时间一律取 {@link SmartJobContext#dbNow()}（铁律 9）：
 * 用 JVM 的 {@code LocalDateTime.now()} 会引入第二个时钟源，
 * 两边差几个小时的话，被判过期的就是<b>不该过期</b>的那一批券 —— 而且改完不可逆。
 *
 * @author alaric
 * @date 2026-08-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SmartJobHandler(
        name = "couponExpire",
        title = "【账务】优惠券过期收口",
        group = "BUSINESS",
        lane = SmartJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 300,
        params = {
                @JobParam(key = "dryRun", desc = "试运行：只统计将要过期的张数，不改数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class MemberCouponExpireJob implements SmartJob {

    /**
     * 单批更新条数。分批不是性能优化 —— 一次 UPDATE 掉几十万行会长时间持有行锁、
     * 撑爆 binlog，一个收口任务不该有能力影响线上业务。
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * 单次执行最多跑多少批。收不完下次接着收 —— 反正它是幂等的。
     */
    private static final int MAX_BATCH_ROUND = 50;

    private final MemberCouponDao memberCouponDao;

    @Override
    public String execute(SmartJobContext ctx) {
        LocalDateTime now = ctx.dbNow();

        if (ctx.boolParam("dryRun", false)) {
            long expirable = memberCouponDao.countExpirableCoupon(now);
            log.info("【券过期收口】试运行：截至 {} 有 {} 张券已过有效期但仍是未使用", now, expirable);
            return "试运行：有 " + expirable + " 张券已过有效期仍未使用，本次未改动任何数据";
        }

        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            // 超时靠中断实现，每批开头自查一次，否则超时配了也砍不掉（框架硬约束 1）
            ctx.checkCancelled();
            int rows = memberCouponDao.expireCouponBatch(now, BATCH_SIZE);
            total += rows;
            if (rows > 0) {
                // 卡住时这一行是唯一的线索：返回值只有跑完才有（框架硬约束 5）
                log.info("【券过期收口】第 {} 批收口 {} 张，累计 {} 张", round + 1, rows, total);
            }
            if (rows < BATCH_SIZE) {
                break;
            }
        }

        if (total == 0) {
            return "没有需要收口的券";
        }
        log.info("【券过期收口】本次共收口 {} 张（截至 {}）", total, now);
        return "已把 " + total + " 张过期券置为「已过期」";
    }
}
