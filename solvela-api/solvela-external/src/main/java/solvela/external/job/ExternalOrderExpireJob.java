package solvela.external.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.external.ExternalOrder;
import solvela.external.dao.ExternalOrderDao;
import solvela.external.service.ExternalRechargeService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部场景单超时取消：把待支付超时的单子取消掉，<b>并把锁定的券放回去</b>。
 *
 * <h3>为什么需要它</h3>
 * 用户下了单、券被锁上、然后他把页面关了。没有这个任务的话，那张券会一直
 * 挂在「锁定中」—— 用户在券包里看得到它，但点进去用不了，而且它永远不会过期。
 *
 * <h3>🔴 和商城那个超时任务是同构的，但<b>各自一个</b></h3>
 * 两种单据的表不同、补偿动作也不同：商城要退积分、放库存、放券三样，
 * 这里只放券。合成一个的结果是一个满是 {@code if} 的任务，
 * 而它一旦写错会<b>同时</b>影响两条业务线。
 *
 * <h3>它和券自己的兜底任务不是一回事</h3>
 * {@code CouponStuckLockReleaseJob} 是<b>最后一道</b>兜底：它只看「锁了多久」，
 * 判不了单据状态（账务域不能依赖业务域）。这个任务看得到单据，所以它是
 * <b>正常路径</b>——超时了就该取消，而不是等两小时后由兜底任务捡回来。
 *
 * <p>幂等：{@code markCancelled} 带着 {@code status = 0}，重复跑第二遍影响 0 行。
 *
 * <p>⚠️ 时间一律取 {@link SolvelaJobContext#dbNow()}（铁律 9）。
 *
 * <h3>🔴 它当前【没有挂载】—— t_solvela_job 里没有对应的行，别顺手补一行</h3>
 * 补一行的后果不是「任务开始跑」，而是<b>调度健康自检每 5 分钟报一次执行器失联</b>：
 * 调度器只跑在 solvela-admin 里，而 admin <b>不依赖 solvela-external</b>，
 * 所以它扫不到这个 handler。2026-09-21 库里就躺着这么一行，
 * 挂了一周从未执行，直到系统看门狗被重新打开才报出来。
 *
 * <h3>为什么不给 admin 加依赖</h3>
 * 产品决策（2026-09-22）：话费代充只作为<b>演示业务</b>存在 ——
 * 真做代充的合规与资金风险极大，不会上。为一个演示场景让 admin 多背一个模块，
 * 换来的是 admin 的启动依赖和攻击面都变大，不划算。
 *
 * <h3>真要让它跑起来时，怎么做（不用动 admin）</h3>
 * {@code solvela-app-biz} <b>已经依赖 solvela-external</b>，把它起成 WORKER 即可：
 * <pre>
 *   java -jar solvela-app-biz.jar --solvela.job.role=WORKER
 * </pre>
 * 然后往 {@code t_solvela_job} 插一行 {@code handler_name = 'externalOrderExpire'}。
 * ⚠️ {@code next_trigger_time} 要写成 {@code NOW() + 8 小时}（服务端时钟是 UTC），
 * 否则一上来就被判成「错过调度」直接 SKIP。
 *
 * <h3>⚠️ 不挂载的代价（已确认，不是永久泄漏）</h3>
 * <ul>
 *   <li><b>券不会卡死</b>：{@code CouponStuckLockReleaseJob} 会在约 2 小时后兜底放回，
 *       只是用户要多等 —— 本来 15 分钟就该回来的；</li>
 *   <li><b>单据停在「待支付」</b>：永远不翻成已取消。已确认下单路径<b>没有</b>
 *       「已有待支付单就拒绝」的校验，所以不挡用户下新单。</li>
 * </ul>
 *
 * @author alaric
 * @date 2026-09-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "externalOrderExpire",
        title = "【外部场景】超时单取消",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 300,
        params = {
                @JobParam(key = "dryRun", desc = "试运行：只统计将要取消的单数，不动数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class ExternalOrderExpireJob implements SolvelaJob {

    /** 单批条数。取消是一单一单做的（每单要放一次券），批小一点，跑不完下次接着来 */
    private static final int BATCH_SIZE = 200;

    private static final String CANCEL_REASON = "超时未支付，自动取消";

    private final ExternalOrderDao externalOrderDao;
    private final ExternalRechargeService externalRechargeService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        LocalDateTime now = ctx.dbNow();
        List<ExternalOrder> expired = externalOrderDao.selectExpiredUnpaid(now, BATCH_SIZE);
        if (expired.isEmpty()) {
            return "没有超时的待支付单";
        }

        if (ctx.boolParam("dryRun", false)) {
            log.info("【外部场景超时取消】试运行：截至 {} 有 {} 单超时未支付", now, expired.size());
            return "试运行：有 " + expired.size() + " 单超时未支付，本次未改动任何数据";
        }

        int cancelled = 0;
        for (ExternalOrder order : expired) {
            // 超时靠中断实现，每单开头自查一次，否则超时配了也砍不掉（框架硬约束 1）
            ctx.checkCancelled();
            /*
             * 🔴 取消走 service 而不是在这里直接改状态：
             *    「取消」= 改状态 + 放券，两件事必须绑在一起。
             *    在 job 里只改状态的话，券会被留在锁定中，
             *    然后由券自己的兜底任务在两小时后捡回来 —— 用户白等两小时。
             */
            if (externalRechargeService.cancel(order, CANCEL_REASON)) {
                cancelled++;
            }
        }

        log.info("【外部场景超时取消】本次取消 {} 单（截至 {}）", cancelled, now);
        return "已取消 " + cancelled + " 单超时未支付的充值单";
    }
}
