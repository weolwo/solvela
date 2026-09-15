package solvela.ledger.coupon.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.ledger.MemberCoupon;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.writeoff.CouponWriteOffService;
import solvela.ledger.coupon.writeoff.domain.CouponWriteOffResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 券的兜底释放：把卡在「4-锁定中」太久的券放回「0-未使用」。
 *
 * <h3>为什么需要它</h3>
 * 进程在<b>锁定和确认之间</b>挂掉，券会永远停在锁定中 —— 用户手里那张券
 * 既用不了也不过期，而且<b>不报错</b>：券包里看着还在，点进去用不了。
 *
 * <p>对策和商城订单超时取消是同一套：一个兜底任务把它捞回来。
 *
 * <h3>🔴 它判不了「订单是不是已经终态」，只能按时间兜底</h3>
 * 方案 §4.2 写的是「锁定超过 N 分钟<b>且对应单据已终态（或不存在）</b>」。
 * 落地时只能做到前半句：账务域<b>不能依赖商城域</b>（{@code MallLedgerBoundaryTest}
 * 盯着这条缝），所以这里看不到订单表。
 *
 * <p>于是阈值必须选得足够宽：商城订单自己的支付超时是
 * <b>30 分钟</b>（{@code MallRedeemService.PAY_EXPIRE_MINUTES}），
 * 到点由 {@code mallOrderExpire} 取消并走正常的释放路径。
 * 本任务默认 <b>120 分钟</b>，是那个窗口的 4 倍 ——
 * 走到这里的券，对应的订单几乎不可能还活着。
 *
 * <h3>⚠️ 万一真的误放了，后果是<b>可见</b>的，不是静默的</h3>
 * 券被放回去之后，那一笔如果还想确认，{@code confirmCoupon} 的
 * {@code WHERE status = 4 AND locked_biz_id = ?} 会匹配 0 行，
 * {@link CouponWriteOffService#confirm} 返回失败并打 ERROR。
 * 调用方必须按「这一单没用券」重新结算 —— 那是一个响亮的失败，
 * 而不是「订单按打折价结算、券还躺在券包里能再用一次」。
 *
 * <h3>幂等</h3>
 * 释放本身是条件更新（{@code WHERE status = 4 AND locked_biz_id = ?}），
 * 重复跑第二遍影响 0 行。所以允许配失败重试。
 *
 * <p>⚠️ 时间一律取 {@link SolvelaJobContext#dbNow()}（铁律 9）：
 * 用 JVM 时钟会引入第二个时间源，差几小时的话被放回去的就是<b>不该放</b>的那批。
 *
 * @author alaric
 * @date 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "couponStuckLockRelease",
        title = "【账务】优惠券卡单释放",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 300,
        params = {
                @JobParam(key = "stuckMinutes",
                        desc = "锁定超过多少分钟算卡住。必须明显大于商城订单的支付超时（30 分钟）",
                        type = JobParam.Type.INT, defaultValue = "120"),
                @JobParam(key = "dryRun", desc = "试运行：只统计将要释放的张数，不改数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class CouponStuckLockReleaseJob implements SolvelaJob {

    /**
     * 卡单阈值的下限。
     *
     * <p>🔴 比商城订单的支付超时（30 分钟）还短的话，这个任务会去<b>抢活着的订单</b>
     * 手里的券 —— 那不是兜底，那是制造故障。所以参数配小了也按这个值兜住。
     */
    private static final int MIN_STUCK_MINUTES = 60;

    /** 单批条数。释放是一张一张做的（每张要写一行流水），批小一点，跑不完下次接着来 */
    private static final int BATCH_SIZE = 200;

    private static final String BIZ_TYPE_BOUNCE = "BOUNCE";

    private final MemberCouponDao memberCouponDao;
    private final CouponWriteOffService couponWriteOffService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        int stuckMinutes = Math.max(MIN_STUCK_MINUTES, ctx.intParam("stuckMinutes", 120));
        LocalDateTime before = ctx.dbNow().minusMinutes(stuckMinutes);

        List<MemberCoupon> stuck = memberCouponDao.selectStuckLocked(before, BATCH_SIZE);
        if (stuck.isEmpty()) {
            return "没有卡在锁定中的券";
        }

        if (ctx.boolParam("dryRun", false)) {
            log.info("【券卡单释放】试运行：{} 之前锁定的券有 {} 张还卡着", before, stuck.size());
            return "试运行：有 " + stuck.size() + " 张券锁定超过 " + stuckMinutes + " 分钟，本次未改动任何数据";
        }

        int released = 0;
        int failed = 0;
        for (MemberCoupon coupon : stuck) {
            // 超时靠中断实现，每张开头自查一次，否则超时配了也砍不掉（框架硬约束 1）
            ctx.checkCancelled();
            /*
             * 用 locked_biz_id 作为释放凭据：和正常释放走的是同一条条件更新。
             * 另写一条「无条件放回」的路会绕开那个条件，而那正是并发安全的来源。
             */
            CouponWriteOffResult result = couponWriteOffService.release(
                    coupon.getId(), BIZ_TYPE_BOUNCE, coupon.getLockedBizId(),
                    "兜底释放：锁定超过 " + stuckMinutes + " 分钟仍未确认");
            if (result.ok()) {
                released++;
            } else {
                // 多半是刚好被正常路径确认/释放掉了 —— 那是好事，不是错误
                failed++;
                log.info("【券卡单释放】券 {} 没放回去：{}", coupon.getId(), result.message());
            }
        }

        log.warn("【券卡单释放】放回 {} 张（阈值 {} 分钟）。"
                        + "🔴 这个数长期不为 0 说明有一条锁定后没人确认也没人释放的路径，要去找它",
                released, stuckMinutes);
        return "已放回 " + released + " 张卡单券"
                + (failed > 0 ? "，另有 " + failed + " 张在本次处理前已被正常收尾" : "");
    }
}
