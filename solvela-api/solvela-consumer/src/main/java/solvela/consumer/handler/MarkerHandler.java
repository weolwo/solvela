package solvela.consumer.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.anno.PrizeStrategy;
import solvela.dispatch.DispatchOutcome;
import solvela.enums.PrizeTypeEnum;
import solvela.prize.PrizeLog;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 标记（标识）派发策略：<b>什么都不发</b>。
 * <p>
 * 「谢谢参与」这类兜底奖项要的只是一条中奖流水 —— 前端要显示转盘停在哪一格，
 * 运营要知道这一格被抽中过多少次。奖品本身没有实体，也就没有可下发的资产。
 * <p>
 * 与其它策略的结构性差异：<b>不生成提案</b>。提案是风控/预算/审批的载体，
 * 而标记既不占预算也不需要审批，硬塞进提案链路只会在 t_proposal_record 里
 * 堆出一堆金额为 0 的空单，把真正要盯的预算口径冲淡。
 * 正因为不进提案，ledger 侧也就没有对应的 {@code @AssetStrategy}，
 * 那不是漏了一层，是这条链路到此为止。
 * <p>
 * 在这个类之前，谢谢参与只能配成 {@code SCORE} + 价值 0（见 {@link ScoreHandler}
 * 里那段「0 分无需入账」的特判）。那样发得出去，但奖励漏斗按 prizeType 分组统计时，
 * 兜底奖项会混进积分口径 —— 条数被算进「已发积分」，价值却是 0，
 * 于是「发出积分 N 笔、合计 0」这种没法解读的数字就出现了。
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Slf4j
@AllArgsConstructor
@PrizeStrategy(value = PrizeTypeEnum.MARKER)
@Service
public class MarkerHandler implements IPrizeHandler {

    @Override
    public DispatchOutcome dispatch(PrizeLog prizeLog) {
        // 标记类奖品的 prize_value 没有语义，正常应为 0。
        // 配了非 0 说明运营把它当成了值类奖品（想发的东西一分也不会到账），
        // 但这不该让抽奖失败 —— 中奖是既成事实，兜底奖项报错反而会掩盖真正的故障。
        // 所以只 WARN 留痕，让人在日志里查得到，不写 fail_reason。
        String prizeValue = prizeLog.getPrizeValue();
        if (isNonZero(prizeValue)) {
            log.warn("【标记奖品配置存疑】标记类奖品不产生任何资产变动，但配置了非 0 价值：{}。LogId: {}, 奖品编码: {}",
                    prizeValue, prizeLog.getId(), prizeLog.getPrizeCode());
        }

        log.info(">>>> [标记派发策略] 无资产变动，直接判成功。LogId: {}, 奖品: {}",
                prizeLog.getId(), prizeLog.getPrizeName());
        return DispatchOutcome.success();
    }

    /**
     * 非法数值一律当成 0 处理：这里只是想判断「运营是不是填了个金额」，
     * 解析不出来说明填的根本不是数值，与「配错成值类奖品」不是一回事，不必告警。
     */
    private boolean isNonZero(String prizeValue) {
        if (prizeValue == null || prizeValue.isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(prizeValue).compareTo(BigDecimal.ZERO) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
