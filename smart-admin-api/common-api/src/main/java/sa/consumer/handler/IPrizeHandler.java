package sa.consumer.handler;

import sa.base.common.domain.ResponseDTO;
import sa.prize.prizelog.domain.entity.PrizeLog;

public interface IPrizeHandler {
    /**
     * 执行具体的派奖逻辑
     * @param prizeLog 已经落库的奖品日志记录
     * @return 派发结果 (包含成功/失败状态和外部流水号等)
     */
    ResponseDTO dispatch(PrizeLog prizeLog);
}