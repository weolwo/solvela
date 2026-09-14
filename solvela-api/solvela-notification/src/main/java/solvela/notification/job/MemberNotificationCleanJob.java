package solvela.notification.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.notification.dao.MemberNotificationDao;

import java.time.LocalDateTime;

/**
 * 站内信保留期清理：删掉超过保留期的通知。
 *
 * <h3>🔴 为什么这个 Job 必须和发通知的功能同批上线</h3>
 * {@code t_member_notification} 是整个方案里唯一一张行数被<b>时间</b>无限放大的表：
 * 行数 = 业务事件数 × 时间，只增不减。100 万活跃用户 × 300 条/年 = <b>3 亿行/年</b>。
 *
 * <p>等有了 3 亿行再来补这个 job，第一次跑就是一次大删除 —— 要分批、要避开高峰、
 * 还要处理 binlog 膨胀，而且那时候没人敢按下去。现在上，它只是每天删掉昨天该删的那一小撮。
 *
 * <h3>为什么是删而不是归档到另一张表</h3>
 * 站内信是<b>时效性内容</b>：没人会翻一年前的「您的订单已发货」。
 * 真正需要长期留痕的东西（资产变动、发奖流水）在各自的域里有自己的表，
 * 站内信只是它们的一个通知副本，删了不丢事实。
 *
 * <p>真需要留档时，正确做法是加一个 {@code archive=true} 参数往冷表搬，
 * 而不是把保留期调到 10 年 —— 那等于没有保留期。
 *
 * <p>幂等：条件是 {@code create_time < before}，重复跑第二遍影响行数就是 0。
 * 因此允许配失败重试。
 *
 * <p>⚠️ 时间取 {@link SolvelaJobContext#dbNow()}：用 JVM 的 {@code LocalDateTime.now()}
 * 会引入第二个时钟源，两边差几个小时的话，被删掉的就是<b>不该删</b>的那一批 ——
 * 而且删完不可逆。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "memberNotificationClean",
        title = "【通知】站内信保留期清理",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "retainDays", desc = "保留天数：早于这个天数的通知会被删除",
                        type = JobParam.Type.INT, defaultValue = "180"),
                @JobParam(key = "dryRun", desc = "试运行：只统计将要删除的条数，不动数据",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class MemberNotificationCleanJob implements SolvelaJob {

    /**
     * 单批删除条数。分批不是性能优化 —— 一次 DELETE 掉几十万行会长时间持有行锁、
     * 撑爆 binlog，一个清理任务不该有能力影响线上业务。
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * 单次执行最多跑多少批。删不完下次接着删 —— 反正它是幂等的。
     *
     * <p>50 × 1000 = 每次最多 5 万条。按日增量算绰绰有余；
     * 首次上线时存量大，让它跑几天慢慢削，比一次删干净安全得多。
     */
    private static final int MAX_BATCH_ROUND = 50;

    /**
     * 保留天数的下限。低于它直接拒绝执行。
     *
     * <p>🔴 这道闸是防手滑的：参数是运营在后台填的，填成 0 或 1 就是
     * 「把用户昨天收到的通知全删了」，而且不可逆。7 天以内的保留期没有任何合理场景。
     */
    private static final int MIN_RETAIN_DAYS = 7;

    private final MemberNotificationDao memberNotificationDao;

    @Override
    public String execute(SolvelaJobContext ctx) {
        int retainDays = ctx.intParam("retainDays", 180);
        if (retainDays < MIN_RETAIN_DAYS) {
            // 抛异常而不是自动纠正成 7：自动纠正会让「填错了」这件事悄无声息，
            // 下次还会填错。这里要的就是一次显眼的失败
            throw new IllegalArgumentException(
                    "保留天数 " + retainDays + " 太短，最少 " + MIN_RETAIN_DAYS + " 天。填错了会把用户刚收到的通知删光");
        }

        LocalDateTime before = ctx.dbNow().minusDays(retainDays);

        if (ctx.boolParam("dryRun", false)) {
            long count = memberNotificationDao.countExpired(before);
            log.info("【站内信清理】试运行：{} 之前的通知共 {} 条", before, count);
            return "试运行：" + before + " 之前共 " + count + " 条待清理，本次未删除任何数据";
        }

        int total = 0;
        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            // 超时靠中断实现，每批开头自查一次，否则超时配了也砍不掉
            ctx.checkCancelled();
            int rows = memberNotificationDao.deleteExpiredBatch(before, BATCH_SIZE);
            total += rows;
            if (rows > 0) {
                // 卡住时这一行是唯一的线索：返回值只有跑完才有
                log.info("【站内信清理】第 {} 批删除 {} 条，累计 {} 条", round + 1, rows, total);
            }
            if (rows < BATCH_SIZE) {
                break;
            }
        }

        if (total == 0) {
            return "没有需要清理的通知";
        }
        log.info("【站内信清理】本次共删除 {} 条（{} 之前，保留 {} 天）", total, before, retainDays);
        return "已清理 " + total + " 条（" + before + " 之前）";
    }
}
