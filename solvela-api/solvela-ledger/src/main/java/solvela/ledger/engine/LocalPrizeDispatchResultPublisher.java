package solvela.ledger.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.enums.PrizeTypeEnum;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;
import solvela.prize.PrizeLog;
import solvela.prize.prizelog.dao.PrizeLogDao;

import java.math.BigDecimal;

/**
 * 进程内回写：直接更新 {@code t_prize_log}。
 *
 * <p>{@link PrizeDispatchResultPublisher} <b>今天唯一的实现</b>，admin 与 biz 装的都是它 ——
 * 入账与发奖流水在同一个进程、同一个库，绕一圈 MQ 只是凭空多一个必须在线的中间件，
 * 还让验收测试需要一个真 broker。
 *
 * <h3>为什么不再有 @ConditionalOnProperty</h3>
 * 本类此前挂着 {@code @ConditionalOnProperty(name = "solvela.prize.dispatch.mode",
 * havingValue = "local", matchIfMissing = true)}，用来给 member 服务的 MQ 实现让位。
 * 那个实现已随 app-member 撤销一并删除，于是这个条件只可能<b>减掉本 bean</b>、
 * 不可能选中别的：谁再配上 {@code dispatch.mode=mq}，得到的是
 * {@code AssetDispatcher} 注入失败、进程起不来。整个配置键也一并删了。
 *
 * <p>资产域将来真独立出去时，再加回 MQ 实现和相应的装配条件 —— 到那时它才有意义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPrizeDispatchResultPublisher implements PrizeDispatchResultPublisher {

    /** 对齐 t_prize_log.fail_reason 的列宽 */
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    private final PrizeLogDao prizeLogDao;

    /** 通知：资产真的到账之后，告诉用户中了什么 */
    private final NotificationService notificationService;

    @Override
    public void publish(PrizeDispatchResultMessage message) {
        try {
            int rows = prizeLogDao.updateStatusByExternalBizNo(
                    message.sourceBizId(),
                    message.success() ? PrizeDispatchStatusEnum.SUCCESS : PrizeDispatchStatusEnum.FAIL,
                    SolvelaStringUtil.truncate(message.failReason(), FAIL_REASON_MAX_LENGTH));

            if (rows > 0 && message.success()) {
                notifyPrizeWon(message.sourceBizId());
            }
        } catch (Exception e) {
            // 回写失败不能影响已经完成的入账：钱已经到账了，为一次状态同步失败去回滚它更糟
            log.error("【发奖记录回写失败】业务单号: {}, 发奖记录状态可能与提案不一致，请人工核对",
                    message.sourceBizId(), e);
        }
    }

    /**
     * 发一条中奖通知。
     *
     * <h3>为什么挂在这里，而不是「中奖」那一刻</h3>
     * 中奖 ≠ 用户拿到了。中了之后还要过提案链路：可能卡人工审批、可能因预算耗尽失败
     * （{@code PrizeDispatchHandler.applyOutcome} 的注释把这个坑写得很清楚）。
     * 在受理时就发「恭喜中奖」然后发不出去，比不发更糟 —— 用户会拿着截图来找客服。
     *
     * <p>本方法只在 {@code updateStatusByExternalBizNo} <b>真的改了行</b>时被调到。
     * 那条 SQL 的 where 里带着 {@code and status = 0}，所以 {@code rows > 0} 等价于
     * 「这次才是 0 → 1 的迁移」。重复投递第二遍 rows 就是 0，<b>不会重复发通知，
     * 也不会多查一次库</b>。
     *
     * <h3>为什么要多查一次 t_prize_log</h3>
     * 回写消息里只有来源单号，而通知要奖品名和数量。走 {@code uk_external_biz}
     * 唯一索引，一次主键级查询，且只在成功迁移时发生 —— 这条路径刚刚已经写了好几次库，
     * 多一次索引查询不改变量级。
     *
     * <p>另一条路是把奖品名塞进 {@code PrizeDispatchResultMessage}，但那是
     * {@code solvela.member.api} 下的<b>跨服务契约</b>，为一条通知去动它不划算 ——
     * 契约变更的代价在将来拆服务时才会显现。
     */
    private void notifyPrizeWon(String sourceBizId) {
        PrizeLog prizeLog = prizeLogDao.selectByExternalBizNo(sourceBizId);
        if (prizeLog == null || prizeLog.getMemberId() == null) {
            return;
        }
        if (isPlaceholderPrize(prizeLog)) {
            return;
        }

        // send() 永不抛异常，所以不需要 try-catch。它也不会影响上面那次回写 ——
        // 反过来如果回写所在的事务回滚了，这条通知跟着回滚，那正是想要的
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.PRIZE_WON, prizeLog.getMemberId())
                .param("prizeName", prizeLog.getPrizeName())
                .param("amount", prizeLog.getPrizeValue())
                .bizRefId(sourceBizId)
                .build());
    }

    /**
     * 占位奖不发通知：「谢谢参与」不是喜讯。
     *
     * <p>两层判断都要：{@code MARKER} 是明确的占位类型，而价值为 0 的其它类型
     * （历史上靠 {@code SCORE} + {@code prizeValue=0} 硬凑的那批）也是同一回事。
     *
     * <p>⚠️ 其实这类奖在 {@code PrizeDispatchHandler.isNoDeliveryNeeded} 就被判成功、
     * 压根走不到引擎，也就到不了这里。留着是因为那个前提<b>不该由本方法去依赖</b> ——
     * 哪天上游改了判定，这里静默给全体用户发「恭喜获得 谢谢参与 ×0」。
     */
    private boolean isPlaceholderPrize(PrizeLog prizeLog) {
        if (PrizeTypeEnum.MARKER.name().equals(prizeLog.getPrizeType())) {
            return true;
        }
        try {
            return new BigDecimal(prizeLog.getPrizeValue()).compareTo(BigDecimal.ZERO) <= 0;
        } catch (RuntimeException e) {
            // 解析不了就当成正常奖品发通知：宁可多发一条，不要因为一个脏值把正常中奖的通知吞掉
            return false;
        }
    }
}
