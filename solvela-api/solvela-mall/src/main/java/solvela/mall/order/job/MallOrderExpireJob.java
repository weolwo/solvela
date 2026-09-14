package solvela.mall.order.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.mall.MallOrder;
import solvela.mall.order.dao.MallOrderDao;
import solvela.mall.order.service.MallOrderCancelService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商城订单超时释放：把过了支付时限还停在「待支付」的单子取消掉，
 * 并把积分、库存、限购额度还回去。
 *
 * <h3>🔴 这是商城的第一个 Job，而它本该一开始就有</h3>
 * {@code t_mall_order.expire_time} 的列注释写着「到点由 job 取消并放回库存」，
 * {@code MallSkuDao.releaseLocked} 的注释写着「到期由超时释放 job 调它放回去」——
 * 两处都在描述这个类，而它在 2026-09-14 之前<b>不存在</b>。
 *
 * <p>后果不是「少个功能」：{@code MallRedeemService} 是<b>先扣积分再建单</b>的，
 * 而 {@code POINTS_CASH} 商品建出来的单落在 0-待支付等现金回调（那条链路至今没写）。
 * 所以每一张这样的单子都是：<b>积分扣了、库存锁了、限购占了，然后永远停在那里。</b>
 *
 * <h3>幂等</h3>
 * 取消走 {@code markCancelled}（带 {@code AND status = 0}），重复跑第二遍影响 0 行、
 * 整单跳过。退款的 {@code bizRefId} 带 {@code :REFUND} 后缀，被唯一键兜着也是幂等的。
 * 所以 {@code idempotent = true}，可以配失败重试。
 *
 * <h3>⚠️ 时间取 dbNow</h3>
 * 用 JVM 的 {@code LocalDateTime.now()} 会引入第二个时钟源。两边差几分钟的话，
 * 被取消的就是<b>还没到期</b>的单 —— 而用户可能正在付款页上。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "mallOrderExpire",
        title = "【商城】超时未支付订单释放",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = true,
        defaultTimeoutSeconds = 300,
        params = {
                @JobParam(key = "dryRun", desc = "试运行：只统计将要取消的单数，不动数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class MallOrderExpireJob implements SolvelaJob {

    /**
     * 单批条数。故意小 —— 每一单都要退款 + 放库存 + 放限购，是一串写操作，
     * 不是一条 UPDATE。批太大会长时间占着连接。
     */
    private static final int BATCH_SIZE = 100;

    /** 单次执行最多几批。处理不完下次接着，它是幂等的 */
    private static final int MAX_BATCH_ROUND = 50;

    private final MallOrderDao mallOrderDao;
    private final MallOrderCancelService mallOrderCancelService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        LocalDateTime now = ctx.dbNow();
        boolean dryRun = ctx.boolParam("dryRun", false);

        if (dryRun) {
            List<MallOrder> preview = mallOrderDao.selectExpiredUnpaid(now, BATCH_SIZE);
            log.info("【商城超时释放】试运行：截至 {} 至少有 {} 张超时待支付单", now, preview.size());
            return "试运行：至少 " + preview.size() + " 张超时待支付单（单批上限 " + BATCH_SIZE + "），本次未改动任何数据";
        }

        int cancelled = 0;
        int skipped = 0;
        int failed = 0;

        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            ctx.checkCancelled();

            // 每轮都重新捞：上一轮取消掉的单子状态已经变了，不会再被捞到，
            // 所以这里不需要 offset —— 用 offset 反而会跳过刚被别的实例改动的行
            List<MallOrder> batch = mallOrderDao.selectExpiredUnpaid(now, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            for (MallOrder order : batch) {
                try {
                    if (mallOrderCancelService.cancelExpired(order)) {
                        cancelled++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    // 单单独立事务，一单失败不影响别的。但必须计数 ——
                    // 否则「全部失败」和「全部成功」在返回值上长得一样
                    failed++;
                    log.error("【商城超时释放】{} 处理失败，本单已回滚，下轮重试", order.getOrderNo(), e);
                }
            }

            if (batch.size() < BATCH_SIZE) {
                break;
            }
            // 🔴 整批都失败时必须退出，否则同一批会被无限重捞 —— 那是个活锁
            if (failed >= batch.size()) {
                log.error("【商城超时释放】整批 {} 单全部失败，停止本次执行", batch.size());
                break;
            }
        }

        if (cancelled == 0 && skipped == 0 && failed == 0) {
            return "没有超时待支付的订单";
        }
        String summary = "已取消 " + cancelled + " 单"
                + (skipped > 0 ? "，跳过 " + skipped + " 单（已被支付或已处理）" : "")
                + (failed > 0 ? "，失败 " + failed + " 单（见日志，下轮重试）" : "");
        log.info("【商城超时释放】{}", summary);
        return summary;
    }
}
