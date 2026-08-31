package solvela.ledger.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.PrizeDispatchStatusEnum;
import solvela.member.api.PrizeDispatchResultMessage;
import solvela.prize.prizelog.dao.PrizeLogDao;

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
