package solvela.consumer.handler;

import solvela.dispatch.DispatchOutcome;
import solvela.prize.PrizeLog;

public interface IPrizeHandler {
    /**
     * 执行具体的派奖逻辑
     *
     * @param prizeLog 已经落库的奖品日志记录
     * @return 派发结果 (包含成功/失败状态和外部流水号等)
     */
    DispatchOutcome dispatch(PrizeLog prizeLog);
}