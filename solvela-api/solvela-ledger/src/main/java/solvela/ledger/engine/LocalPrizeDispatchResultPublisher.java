package solvela.ledger.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.prize.prizelog.dao.PrizeLogDao;

/**
 * 单体形态下的回写：直接更新 {@code t_prize_log}。
 *
 * <p><b>admin 用它</b> —— 发奖流水就在同一个进程、同一个库，绕一圈 MQ 只是凭空
 * 多一个必须在线的中间件，还让 admin 的验收测试需要一个真 broker。
 *
 * <p>装哪个实现由 {@code solvela.prize.dispatch.mode} 决定，不填即 {@code local}。
 * 会员服务那边配成 {@code mq}，本实现就自动让位。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "solvela.prize.dispatch.mode", havingValue = "local", matchIfMissing = true)
public class LocalPrizeDispatchResultPublisher implements PrizeDispatchResultPublisher {

    /** 对齐 t_prize_log.fail_reason 的列宽 */
    private static final int FAIL_REASON_MAX_LENGTH = 128;

    private final PrizeLogDao prizeLogDao;

    @Override
    public void publish(PrizeDispatchResultMessage message) {
        try {
            prizeLogDao.updateStatusByExternalBizNo(
                    message.sourceBizId(),
                    message.success() ? PrizeDispatchStatusEnum.SUCCESS : PrizeDispatchStatusEnum.FAIL,
                    SolvelaStringUtil.truncate(message.failReason(), FAIL_REASON_MAX_LENGTH));
        } catch (Exception e) {
            // 回写失败不能影响已经完成的入账：钱已经到账了，为一次状态同步失败去回滚它更糟
            log.error("【发奖记录回写失败】业务单号: {}, 发奖记录状态可能与提案不一致，请人工核对",
                    message.sourceBizId(), e);
        }
    }
}
