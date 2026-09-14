package solvela.ledger.coupon.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.enums.NotificationTemplateEnum;
import solvela.ledger.coupon.dao.MemberCouponDao;
import solvela.ledger.coupon.domain.dto.MemberCouponExpiringDTO;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 券即将过期提醒：每天跑一次，给手里有快过期券的会员发一条站内信。
 *
 * <h3>🔴 一个人一条，不是一张券一条</h3>
 * 这是整个通知方案里最容易做错、也最容易毁掉消息中心的一处。
 *
 * <p>消息中心死掉的原因通常不是性能，是<b>消息泛滥导致用户再也不点那个红点</b>。
 * 而「券将过期」是所有事件里最容易泛滥的一个：一个活跃用户手里攒十几张券很正常，
 * 一张发一条，用户当天收到 15 条通知，第二天就把这个 tab 拉黑了。
 *
 * <p>防线有三层，任何一层单独都不够：
 * <ol>
 *   <li><b>SQL 聚合</b> —— {@code selectExpiringGroupByMember} 按会员 GROUP BY，
 *       一个会员一行；</li>
 *   <li><b>DTO 形状</b> —— {@link MemberCouponExpiringDTO} 只有张数和最近失效时间，
 *       <b>没有券列表</b>，拿着它写不出一券一条；</li>
 *   <li><b>模板参数</b> —— {@code COUPON_EXPIRING} 的占位符是 {@code count}
 *       而不是券名。</li>
 * </ol>
 * 三层都指向同一件事：让「一人一条」成为唯一能写出来的写法。
 *
 * <h3>幂等性：⚠️ 本任务不幂等，重复跑会重复发</h3>
 * 窗口是「从现在到 N 天后」，同一批券今天跑、明天跑都会命中。所以：
 * <ul>
 *   <li>{@code idempotent = false}，<b>不要配失败重试</b>；</li>
 *   <li>调度上一天最多一次。</li>
 * </ul>
 *
 * <p>真要做到幂等，得记「这个人这批券已经提醒过了」——那需要一张新表或一个
 * 带 TTL 的 Redis key。当前的取舍是：<b>每天提醒一次本来就是这个功能想要的行为</b>
 * （券快过期了就该每天念叨），所以不值当为此加一套状态。
 * 🔴 但如果哪天把调度改成一天多次，必须先解决这件事。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "couponExpiringNotify",
        title = "【通知】优惠券即将过期提醒",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = false,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "aheadDays", desc = "提前几天提醒",
                        type = JobParam.Type.INT, defaultValue = "3"),
                @JobParam(key = "dryRun", desc = "试运行：只统计将要提醒的人数，不发通知",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class CouponExpiringNotifyJob implements SolvelaJob {

    /** 单批处理的会员数。分批是为了不把整个结果集拉进内存 */
    private static final int BATCH_SIZE = 500;

    /**
     * 单次执行最多跑多少批。跑不完下次接着 ——
     * 但注意本任务不幂等，「下次」意味着那批人明天才会收到提醒。
     */
    private static final int MAX_BATCH_ROUND = 200;

    private static final DateTimeFormatter EXPIRE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MemberCouponDao memberCouponDao;
    private final NotificationService notificationService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        int aheadDays = ctx.intParam("aheadDays", 3);
        if (aheadDays < 1) {
            throw new IllegalArgumentException("提前天数至少 1 天，填了 " + aheadDays);
        }

        // 时间一律取 dbNow：用 JVM 时钟会引入第二个时钟源，两边差几小时的话，
        // 提醒窗口就整个错位 —— 表现是「有人收到过期券的提醒、有人一直收不到」
        LocalDateTime now = ctx.dbNow();
        LocalDateTime until = now.plusDays(aheadDays);

        boolean dryRun = ctx.boolParam("dryRun", false);
        int members = 0;
        int coupons = 0;

        for (int round = 0; round < MAX_BATCH_ROUND; round++) {
            ctx.checkCancelled();

            List<MemberCouponExpiringDTO> batch =
                    memberCouponDao.selectExpiringGroupByMember(now, until, BATCH_SIZE, round * BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            for (MemberCouponExpiringDTO row : batch) {
                members++;
                coupons += row.getCount() == null ? 0 : row.getCount();
                if (!dryRun) {
                    notify(row);
                }
            }

            if (batch.size() < BATCH_SIZE) {
                break;
            }
        }

        if (members == 0) {
            return "没有即将过期的券";
        }
        String summary = (dryRun ? "试运行：" : "")
                + "共 " + members + " 位会员的 " + coupons + " 张券将在 " + aheadDays + " 天内过期"
                + (dryRun ? "，本次未发送任何通知" : "，已发出 " + members + " 条提醒（一人一条）");
        log.info("【券过期提醒】{}", summary);
        return summary;
    }

    private void notify(MemberCouponExpiringDTO row) {
        // send() 永不抛异常：一个人的通知发失败，不该让整批任务中断
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.COUPON_EXPIRING, row.getMemberId())
                .param("count", row.getCount())
                .param("nearestExpireTime", row.getNearestExpireTime() == null
                        ? null : row.getNearestExpireTime().format(EXPIRE_TIME_FORMATTER))
                .build());
    }
}
