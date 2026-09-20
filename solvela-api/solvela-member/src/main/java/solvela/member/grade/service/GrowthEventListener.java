package solvela.member.grade.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.member.grade.GrowthProperties;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 业务动作 → 成长值的<b>防腐层</b>。
 *
 * <h3>它是打点管道的第二个消费者</h3>
 * 2026-09-17 建那套管道时说过「加消费者，生产者零改动」——
 * 这是第一次真的兑现：资产域那边一行业务代码都没为等级改过，
 * 它只是照常广播「积分入账了，来源是 X」。
 *
 * <pre>
 *   ORDER_PAID / SCORE_EARNED / ...
 *      ├──▶ BizActionEventListener (marketing) → 任务进度
 *      └──▶ GrowthEventListener    (member)    → 成长值     ← 本类
 * </pre>
 *
 * <h3>🔴 「算不算成长值」的判断在这里，不在资产域</h3>
 * 资产域广播<b>全部</b> SCORE 入账。哪些算是会员域的业务规则 ——
 * 让账本知道「等级」这个概念存在，就等于把一条会变的营销规则焊死在最不该变的那一层。
 *
 * <h3>🔴 判据是 {@code bizType}，不是「哪个方法写的流水」</h3>
 * 资产域的 {@code executeWalletRefund} 同时承担「商城发积分」（该算）
 * 和「真正的退回」（不该算）—— 按方法判会把商城那一半一起漏掉。
 * 详见 {@code GrowthProperties.countableBizTypes} 的注释。
 *
 * <h3>不加 {@code @Async}</h3>
 * 与 {@code BizActionEventListener} 同一条：那个池队列无界且被派奖占用，
 * 而 {@code @Async} 在这个仓库里还踩过 JDK 代理的坑。
 * 本方法跑在提交线程上，做的是一次插入加一次自增 —— 毫秒级。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrowthEventListener {

    private final MemberGrowthService memberGrowthService;
    private final GrowthProperties growthProperties;

    /**
     * 接住一件业务动作，该算成长值的就记一笔。
     *
     * <p>🔴 {@code fallbackExecution = true} 与 {@code BizActionEventListener} 保持一致 ——
     * 两个监听器对同一条事件的投递语义必须相同，否则会出现
     * 「任务进度涨了但成长值没涨」这种只在无事务生产者上发生的偏差，
     * 而那种偏差极难联想到根因。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBizAction(BizActionEvent event) {
        try {
            if (!BizActionCodes.SCORE_EARNED.equals(event.actionCode())) {
                // 其它动作今天不产生成长值。将来加来源就是在这里多一个分支
                return;
            }
            String bizType = asString(event.payload().get("bizType"));
            if (!countable(bizType)) {
                /*
                 * 最典型的就是【退回】：用户取消订单，积分退回钱包。
                 * 那不是新贡献，是「东西没给成还给你」—— 算进成长值等于
                 * 下单再取消就能刷等级。
                 */
                log.debug("【成长值】bizType={} 不计成长值，忽略。memberId={}, bizId={}",
                        bizType, event.memberId(), event.bizId());
                return;
            }

            long base = baseValueOf(event);
            if (base <= 0) {
                log.debug("【成长值】基数为 0，忽略。memberId={}, bizId={}",
                        event.memberId(), event.bizId());
                return;
            }

            memberGrowthService.accrue(event.memberId(), base,
                    BizActionCodes.SCORE_EARNED, bizType, event.bizId(), "积分入账");

        } catch (RuntimeException e) {
            /*
             * 🔴 异常必须全吃掉。
             *
             * 此刻积分入账的事务【已经提交】——积分已经到用户账上了。
             * 异常再往上抛会变成一次没有归属的 500，而用户的积分明明已经到了。
             * 形状与 BizActionEventListener / MallOrderFulfillListener 一致。
             *
             * 吃掉不等于丢了：成长值流水的唯一键让重推安全，
             * 对账任务把它补回来时不会重复计数。
             */
            log.error("【成长值】入账失败，本次不涨。action={}, member={}, bizId={}",
                    event.actionCode(), event.memberId(), event.bizId(), e);
        }
    }

    /** 这个来源算不算成长值。白名单 —— 新来源默认不算，那是安全的一边 */
    private boolean countable(String bizType) {
        return bizType != null && growthProperties.getCountableBizTypes().contains(bizType);
    }

    /**
     * 倍率之前的基数 = 入账积分 × 全局系数。
     *
     * <p>⚠️ 积分是 {@code decimal(18,4)}，成长值是整数。这里<b>向下取整</b> ——
     * 四舍五入会让「0.6 积分」变成 1 点成长值，而那种小额入账在活动里很常见，
     * 积少成多就是凭空多给的等级。宁可少给。
     */
    private long baseValueOf(BizActionEvent event) {
        BigDecimal amount = event.amount();
        if (amount == null || amount.signum() <= 0) {
            return 0L;
        }
        return amount.multiply(BigDecimal.valueOf(growthProperties.getScoreRatio()))
                .setScale(0, java.math.RoundingMode.DOWN)
                .longValue();
    }

    private static String asString(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }
}
